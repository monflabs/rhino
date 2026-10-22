/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.Block;
import org.monflabs.mozilla.javascript.ast.BreakStatement;
import org.monflabs.mozilla.javascript.ast.CatchClause;
import org.monflabs.mozilla.javascript.ast.ClassNode;
import org.monflabs.mozilla.javascript.ast.ContinueStatement;
import org.monflabs.mozilla.javascript.ast.ExpressionStatement;
import org.monflabs.mozilla.javascript.ast.FunctionCall;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.IfStatement;
import org.monflabs.mozilla.javascript.ast.InfixExpression;
import org.monflabs.mozilla.javascript.ast.Jump;
import org.monflabs.mozilla.javascript.ast.LabeledStatement;
import org.monflabs.mozilla.javascript.ast.Loop;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.NumberLiteral;
import org.monflabs.mozilla.javascript.ast.ParenthesizedExpression;
import org.monflabs.mozilla.javascript.ast.ReturnStatement;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.SwitchCase;
import org.monflabs.mozilla.javascript.ast.SwitchStatement;
import org.monflabs.mozilla.javascript.ast.Symbol;
import org.monflabs.mozilla.javascript.ast.TryStatement;
import org.monflabs.mozilla.javascript.ast.UnaryExpression;
import org.monflabs.mozilla.javascript.ast.VariableDeclaration;
import org.monflabs.mozilla.javascript.ast.WithStatement;
import org.monflabs.mozilla.javascript.config.RhinoConfig;

/**
 * Splits a function too large for a class file into chunks, as Nashorn's SplitIntoFunctions: runs
 * of statements of its body, or of a block too large on its own, become arrow functions called in
 * place, which share this, arguments, new.target and super with the function. The names of a chunk
 * resolve to the bindings that the parser recorded in the enclosing scopes. A return in a chunk
 * stores its value in a variable of the function and returns a state; a break or continue to a
 * statement outside of the chunk returns a state of its own; the call site replays them.
 *
 * <p>Runs on the AST, before IRFactory transforms the body, when the class compiler found the
 * function too large (CompilerEnvirons#splitFurther). Lexical declarations and function
 * declarations stay in place. Not split: generators and async functions (a yield cannot cross a
 * function), derived constructors (super()), and functions with a direct eval or a with statement,
 * whose names resolve at run time.
 */
final class Splitter {

    /** The number of times a function can be split again, with chunks half as large each time. */
    static final int MAX_LEVEL = 3;

    /** The weight of a chunk the first time, in AST nodes: about 6 KB of bytecode. */
    private static final int CHUNK_WEIGHT = 600;

    /**
     * For testing: a chunk weight at which compiled mode splits every function it can, whatever its
     * size (0: only after the class compiler found a function too large).
     */
    static final int FORCED_WEIGHT = RhinoConfig.get("rhino.splitWeight", 0);

    static final String RETURN_VALUE = "%return";
    private static final String STATE = "%state";
    private static final int RETURN_STATE = 1;

    private final FunctionNode fn;
    private final int limit;
    private boolean declared;

    private Splitter(FunctionNode fn, int limit) {
        this.fn = fn;
        this.limit = limit;
    }

    /** The weight of a chunk at that split level. */
    static int chunkWeight(int level) {
        return CHUNK_WEIGHT >> (level - 1);
    }

    /** Splits the body of the function; returns whether it could be split. */
    static boolean split(FunctionNode fn, int weight) {
        if (!canSplit(fn)) {
            return false;
        }
        Splitter splitter = new Splitter(fn, Math.max(weight, 1));
        try {
            return splitter.splitBlock(fn.getBody());
        } catch (CannotSplit e) {
            return false;
        }
    }

    private static boolean canSplit(FunctionNode fn) {
        if (fn.isGenerator()
                || fn.isES6Generator()
                || fn.isAsync()
                || fn.isDerivedConstructor()
                || fn.getSplitOwner() != null
                || !(fn.getBody() instanceof Block)) {
            return false;
        }
        boolean[] dynamic = {false};
        fn.getBody()
                .visit(
                        node -> {
                            if (node instanceof WithStatement || isDirectEval(node)) {
                                dynamic[0] = true;
                            }
                            // an arrow function resolves names in the scope of the function
                            return !dynamic[0]
                                    && (!(node instanceof FunctionNode)
                                            || ((FunctionNode) node).getFunctionType()
                                                    == FunctionNode.ARROW_FUNCTION);
                        });
        return !dynamic[0];
    }

    private static boolean isDirectEval(AstNode node) {
        return node instanceof FunctionCall
                && ((FunctionCall) node).getTarget() instanceof Name
                && "eval".equals(((Name) ((FunctionCall) node).getTarget()).getIdentifier());
    }

    /** Thrown when a jump cannot be rewritten: the function is not split. */
    private static final class CannotSplit extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CannotSplit() {
            super(null, null, false, false);
        }
    }

    /**
     * The weight of a node: the number of AST nodes, a nested function counting as one (it is a
     * method of its own).
     */
    private static int weight(AstNode node) {
        int[] count = {0};
        node.visit(
                n -> {
                    count[0]++;
                    return n == node || !(n instanceof FunctionNode);
                });
        return count[0];
    }

    /** Splits a block with statements as children (a Block, a Scope); returns whether it did. */
    private boolean splitBlock(AstNode block) {
        List<AstNode> statements = new ArrayList<>();
        for (Node kid : block) {
            statements.add((AstNode) kid);
        }
        List<AstNode> result = splitStatements(statements, block);
        if (result == null) {
            return false;
        }
        block.removeChildren();
        for (AstNode statement : result) {
            block.addChildToBack(statement);
            statement.setParent(block);
        }
        return true;
    }

    private boolean splitSwitchCase(SwitchCase switchCase) {
        if (switchCase.getStatements() == null) {
            return false;
        }
        List<AstNode> result =
                splitStatements(new ArrayList<>(switchCase.getStatements()), switchCase);
        if (result == null) {
            return false;
        }
        switchCase.setStatements(result);
        return true;
    }

    /**
     * Splits the statements of a block, the blocks too large on their own first; returns the new
     * statements, or null if nothing changed.
     */
    private List<AstNode> splitStatements(List<AstNode> statements, AstNode block) {
        boolean changed = false;
        int total = 0;
        for (AstNode statement : statements) {
            int w = weight(statement);
            if (w > limit) {
                changed |= splitInside(statement);
                w = weight(statement);
            }
            total += w;
        }
        if (total <= limit) {
            return changed ? statements : null;
        }
        List<AstNode> result = new ArrayList<>();
        List<AstNode> run = new ArrayList<>();
        int runWeight = 0;
        for (AstNode statement : statements) {
            if (!isMovable(statement)) {
                changed |= flush(run, block, result);
                runWeight = 0;
                result.add(statement);
                continue;
            }
            int w = weight(statement);
            if (!run.isEmpty() && runWeight + w > limit) {
                changed |= flush(run, block, result);
                runWeight = 0;
            }
            run.add(statement);
            runWeight += w;
        }
        changed |= flush(run, block, result);
        return changed ? result : null;
    }

    /** Splits the blocks of a compound statement; returns whether it did. */
    private boolean splitInside(AstNode statement) {
        boolean changed = false;
        if (statement instanceof Block
                || (statement instanceof Scope && isStatementBlock(statement))) {
            changed = splitBlock(statement);
        } else if (statement instanceof Loop) {
            changed = splitNested(((Loop) statement).getBody());
        } else if (statement instanceof IfStatement) {
            changed = splitNested(((IfStatement) statement).getThenPart());
            changed |= splitNested(((IfStatement) statement).getElsePart());
        } else if (statement instanceof TryStatement) {
            TryStatement tryStatement = (TryStatement) statement;
            changed = splitNested(tryStatement.getTryBlock());
            for (CatchClause clause : tryStatement.getCatchClauses()) {
                changed |= splitNested(clause.getBody());
            }
            changed |= splitNested(tryStatement.getFinallyBlock());
        } else if (statement instanceof LabeledStatement) {
            changed = splitNested(((LabeledStatement) statement).getStatement());
        } else if (statement instanceof SwitchStatement) {
            for (SwitchCase switchCase : ((SwitchStatement) statement).getCases()) {
                if (weight(switchCase) > limit) {
                    changed |= splitSwitchCase(switchCase);
                }
            }
        }
        return changed;
    }

    private boolean splitNested(AstNode node) {
        if (node == null || weight(node) <= limit) {
            return false;
        }
        if (node instanceof Block || (node instanceof Scope && isStatementBlock(node))) {
            return splitBlock(node);
        }
        boolean changed = splitInside(node);
        if (weight(node) > limit && isMovable(node) && node.getParent() != null) {
            // a statement that is not a block, such as the else part of an else if chain: a chunk
            // of its own, from the innermost one out
            Block holder = new Block(node.getAbsolutePosition(), node.getLength());
            holder.setLineColumnNumber(node.getLineno(), node.getColumn());
            replace(node, holder);
            List<AstNode> run = new ArrayList<>();
            run.add(node);
            for (AstNode statement : chunk(run, holder)) {
                holder.addStatement(statement);
            }
            changed = true;
        }
        return changed;
    }

    /** A Scope used as a block of statements (not a loop or another statement with a scope). */
    private static boolean isStatementBlock(AstNode node) {
        return node.getType() == Token.BLOCK && !(node instanceof FunctionNode);
    }

    /**
     * Whether a statement can move to a chunk: not a declaration whose binding or hoisting belongs
     * to the block, nor a statement with a function declared in a block (Annex B.3.3 hoists it to a
     * variable of the function, assigned where the declaration is evaluated).
     */
    private static boolean isMovable(AstNode statement) {
        if (statement instanceof FunctionNode
                || statement instanceof ClassNode
                || hasBlockFunction(statement)) {
            return false;
        }
        if (statement instanceof VariableDeclaration) {
            return statement.getType() == Token.VAR;
        }
        if (statement instanceof ExpressionStatement) {
            AstNode expression = ((ExpressionStatement) statement).getExpression();
            return !(expression instanceof FunctionNode || expression instanceof ClassNode);
        }
        return true;
    }

    private static boolean hasBlockFunction(AstNode statement) {
        boolean[] found = {false};
        statement.visit(
                n -> {
                    if (n instanceof FunctionNode) {
                        int type = ((FunctionNode) n).getFunctionType();
                        found[0] |=
                                type == FunctionNode.FUNCTION_EXPRESSION_STATEMENT
                                        || type == FunctionNode.FUNCTION_BLOCK_SCOPED;
                        return false;
                    }
                    return !found[0];
                });
        return found[0];
    }

    /**
     * Wraps a run of statements in a chunk, unless it is too small to be worth it; adds the result
     * to the statements and clears the run. Returns whether it made a chunk.
     */
    private boolean flush(List<AstNode> run, AstNode block, List<AstNode> result) {
        if (run.isEmpty()) {
            return false;
        }
        if (run.size() == 1 && weight(run.get(0)) < Math.min(limit, 16)) {
            result.addAll(run);
            run.clear();
            return false;
        }
        result.addAll(chunk(run, block));
        run.clear();
        return true;
    }

    /** The statements that replace a run of statements: the call of a chunk holding them. */
    private List<AstNode> chunk(List<AstNode> run, AstNode block) {
        AstNode first = run.get(0);
        AstNode last = run.get(run.size() - 1);
        int start = first.getAbsolutePosition();
        int end = last.getAbsolutePosition() + last.getLength();
        int lineno = first.getLineno();
        int column = first.getColumn();

        Block body = new Block(start, end - start);
        body.setLineColumnNumber(lineno, column);
        for (AstNode statement : run) {
            statement.setParent(null);
            body.addStatement(statement);
        }

        FunctionNode chunk = new FunctionNode(start);
        chunk.setFunctionType(FunctionNode.ARROW_FUNCTION);
        chunk.setInStrictMode(fn.isInStrictMode());
        chunk.setBody(body);
        chunk.setLength(end - start);
        chunk.setSplitOwner(fn);
        chunk.setSplitKey(fn.getSplitKey());
        chunk.setSourceName(fn.getSourceName());
        chunk.setBaseLineno(lineno);
        chunk.setEndLineno(last.getLineno());
        chunk.setLineColumnNumber(lineno, column);
        moveScopes(body, chunk);

        IdentityHashMap<AstNode, Integer> states = new IdentityHashMap<>();
        List<AstNode> replays = new ArrayList<>();
        rewriteJumps(body, body, states, replays);

        FunctionCall call = new FunctionCall(start, end - start);
        call.setTarget(new ParenthesizedExpression(start, end - start, chunk));
        call.setArguments(new ArrayList<>());
        call.setLineColumnNumber(lineno, column);

        List<AstNode> statements = new ArrayList<>();
        if (states.isEmpty()) {
            statements.add(statement(call, lineno, column));
            return statements;
        }
        declareVariables();
        statements.add(statement(assign(name(STATE, start), call, start), lineno, column));
        for (AstNode replay : replays) {
            IfStatement test = new IfStatement(start);
            test.setCondition(
                    new InfixExpression(
                            Token.SHEQ,
                            name(STATE, start),
                            number(states.get(replay), start),
                            start));
            test.setThenPart(replay);
            test.setLineColumnNumber(lineno, column);
            statements.add(test);
        }
        return statements;
    }

    /**
     * Rewrites the returns of a chunk and its jumps to statements outside of it; records the state
     * of each and the statement that replays it after the call.
     */
    private void rewriteJumps(
            AstNode node,
            Block body,
            IdentityHashMap<AstNode, Integer> states,
            List<AstNode> replays) {
        List<AstNode> jumps = new ArrayList<>();
        node.visit(
                n -> {
                    if (n instanceof FunctionNode) {
                        return false;
                    }
                    if (n instanceof ReturnStatement
                            || n instanceof BreakStatement
                            || n instanceof ContinueStatement) {
                        jumps.add(n);
                    }
                    return true;
                });
        IdentityHashMap<AstNode, AstNode> breaks = new IdentityHashMap<>();
        IdentityHashMap<AstNode, AstNode> continues = new IdentityHashMap<>();
        int nextState = RETURN_STATE + 1;
        for (AstNode jump : jumps) {
            int start = jump.getAbsolutePosition();
            if (jump instanceof ReturnStatement) {
                ReturnStatement ret = (ReturnStatement) jump;
                AstNode value = ret.getReturnValue();
                if (value == null) {
                    value = new UnaryExpression(Token.VOID, start, number(0, start));
                } else {
                    value.setParent(null);
                }
                InfixExpression comma =
                        new InfixExpression(
                                Token.COMMA,
                                assign(name(RETURN_VALUE, start), value, start),
                                number(RETURN_STATE, start),
                                start);
                ret.setReturnValue(comma);
                if (!states.containsValue(RETURN_STATE)) {
                    ReturnStatement replay = new ReturnStatement(start);
                    replay.setReturnValue(name(RETURN_VALUE, start));
                    replay.setLineColumnNumber(jump.getLineno(), jump.getColumn());
                    states.put(replay, RETURN_STATE);
                    replays.add(replay);
                }
                continue;
            }
            AstNode target =
                    jump instanceof BreakStatement
                            ? ((BreakStatement) jump).getBreakTarget()
                            : ((ContinueStatement) jump).getTarget();
            if (target == null) {
                throw new CannotSplit();
            }
            if (isWithin(target, body)) {
                continue;
            }
            IdentityHashMap<AstNode, AstNode> byTarget =
                    jump instanceof BreakStatement ? breaks : continues;
            AstNode replay = byTarget.get(target);
            if (replay == null) {
                replay = replayOf(jump);
                byTarget.put(target, replay);
                states.put(replay, nextState++);
                replays.add(replay);
            }
            ReturnStatement ret = new ReturnStatement(start);
            ret.setReturnValue(number(states.get(replay), start));
            ret.setLineColumnNumber(jump.getLineno(), jump.getColumn());
            replace(jump, ret);
        }
    }

    /** A copy of a break or continue, for the call site of the chunk. */
    private static AstNode replayOf(AstNode jump) {
        int start = jump.getAbsolutePosition();
        AstNode replay;
        if (jump instanceof BreakStatement) {
            BreakStatement original = (BreakStatement) jump;
            BreakStatement copy = new BreakStatement(start, jump.getLength());
            if (original.getBreakLabel() != null) {
                copy.setBreakLabel(new Name(start, original.getBreakLabel().getIdentifier()));
            }
            copy.setBreakTarget((Jump) original.getBreakTarget());
            replay = copy;
        } else {
            ContinueStatement original = (ContinueStatement) jump;
            ContinueStatement copy = new ContinueStatement(start, jump.getLength());
            if (original.getLabel() != null) {
                copy.setLabel(new Name(start, original.getLabel().getIdentifier()));
            }
            copy.setTarget(original.getTarget());
            replay = copy;
        }
        replay.setLineColumnNumber(jump.getLineno(), jump.getColumn());
        return replay;
    }

    /** Replaces a statement in its parent. */
    private static void replace(AstNode old, AstNode replacement) {
        AstNode parent = old.getParent();
        replacement.setPosition(old.getAbsolutePosition());
        if (parent instanceof IfStatement) {
            IfStatement ifStatement = (IfStatement) parent;
            if (ifStatement.getThenPart() == old) {
                ifStatement.setThenPart(replacement);
            } else {
                ifStatement.setElsePart(replacement);
            }
        } else if (parent instanceof Loop && ((Loop) parent).getBody() == old) {
            ((Loop) parent).setBody(replacement);
        } else if (parent instanceof LabeledStatement) {
            ((LabeledStatement) parent).setStatement(replacement);
        } else if (parent instanceof SwitchCase) {
            List<AstNode> statements = new ArrayList<>(((SwitchCase) parent).getStatements());
            statements.set(statements.indexOf(old), replacement);
            ((SwitchCase) parent).setStatements(statements);
        } else if (parent != null && isChild(parent, old)) {
            parent.replaceChild(old, replacement);
            replacement.setParent(parent);
        } else {
            throw new CannotSplit();
        }
    }

    private static boolean isChild(AstNode parent, AstNode node) {
        for (Node kid : parent) {
            if (kid == node) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithin(AstNode node, AstNode ancestor) {
        for (AstNode n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Makes the chunk the scope of the scopes of its statements: their scope chains stop at the
     * chunk, as they stop at a function, and the names of the enclosing functions are found through
     * the syntax tree. Nested functions are scopes of their own.
     */
    private void moveScopes(Block body, FunctionNode chunk) {
        Set<Scope> scopes = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Scope> order = new ArrayList<>();
        body.visit(
                n -> {
                    if (n instanceof FunctionNode) {
                        return false;
                    }
                    Scope scope =
                            n instanceof Scope
                                    ? (Scope) n
                                    // the scope of a class name binding is outside of the tree
                                    : n instanceof ClassNode ? ((ClassNode) n).getScope() : null;
                    if (scope != null && scopes.add(scope)) {
                        order.add(scope);
                    }
                    return true;
                });
        // the function whose variables hold the bindings of each scope (a let expression of
        // JavaScript 1.7 has no top: that of its parent)
        IdentityHashMap<Scope, ScriptNode> tops = new IdentityHashMap<>();
        for (Scope scope : order) {
            Scope parent = scope.getParentScope();
            ScriptNode top = scope.getTop();
            if (top == null) {
                top = parent != null && tops.get(parent) != null ? tops.get(parent) : fn;
            }
            tops.put(scope, top);
        }
        for (Scope scope : order) {
            Scope parent = scope.getParentScope();
            ScriptNode top = tops.get(scope);
            // a scope without a parent is linked when IRFactory reaches it (a let expression of
            // JavaScript 1.7)
            if (parent != null && !scopes.contains(parent)) {
                if (parent.getChildScopes() != null) {
                    parent.getChildScopes().remove(scope);
                }
                chunk.addChildScope(scope);
            }
            // the bindings of the scope become variables of the chunk
            if (top != chunk && scope.getSymbolTable() != null) {
                for (Symbol symbol : scope.getSymbolTable().values()) {
                    top.getSymbols().remove(symbol);
                    chunk.getSymbols().add(symbol);
                }
            }
            scope.setTop(chunk);
        }
    }

    /** Declares the variables of the function that hold the return value and the state. */
    private void declareVariables() {
        if (!declared) {
            fn.putSymbol(new Symbol(Symbol.Type.VAR, RETURN_VALUE));
            fn.putSymbol(new Symbol(Symbol.Type.VAR, STATE));
            declared = true;
        }
    }

    private static Name name(String identifier, int position) {
        return new Name(position, identifier);
    }

    private static NumberLiteral number(int value, int position) {
        return new NumberLiteral(position, Integer.toString(value), value);
    }

    private static AstNode assign(AstNode target, AstNode value, int position) {
        return new org.monflabs.mozilla.javascript.ast.Assignment(
                Token.ASSIGN, target, value, position);
    }

    private static ExpressionStatement statement(AstNode expression, int lineno, int column) {
        ExpressionStatement statement = new ExpressionStatement(expression, false);
        statement.setLineColumnNumber(lineno, column);
        return statement;
    }
}
