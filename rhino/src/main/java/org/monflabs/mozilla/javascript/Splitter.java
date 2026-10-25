/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.monflabs.mozilla.javascript.ast.AbstractObjectProperty;
import org.monflabs.mozilla.javascript.ast.ArrayComprehension;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.Block;
import org.monflabs.mozilla.javascript.ast.BreakStatement;
import org.monflabs.mozilla.javascript.ast.CatchClause;
import org.monflabs.mozilla.javascript.ast.ClassNode;
import org.monflabs.mozilla.javascript.ast.ConditionalExpression;
import org.monflabs.mozilla.javascript.ast.ContinueStatement;
import org.monflabs.mozilla.javascript.ast.ExpressionStatement;
import org.monflabs.mozilla.javascript.ast.FunctionCall;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.GeneratorExpression;
import org.monflabs.mozilla.javascript.ast.IfStatement;
import org.monflabs.mozilla.javascript.ast.InfixExpression;
import org.monflabs.mozilla.javascript.ast.Jump;
import org.monflabs.mozilla.javascript.ast.Label;
import org.monflabs.mozilla.javascript.ast.LabeledStatement;
import org.monflabs.mozilla.javascript.ast.LetNode;
import org.monflabs.mozilla.javascript.ast.Loop;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.NumberLiteral;
import org.monflabs.mozilla.javascript.ast.ObjectLiteral;
import org.monflabs.mozilla.javascript.ast.ObjectProperty;
import org.monflabs.mozilla.javascript.ast.ParenthesizedExpression;
import org.monflabs.mozilla.javascript.ast.ReturnStatement;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.Spread;
import org.monflabs.mozilla.javascript.ast.SpreadObjectProperty;
import org.monflabs.mozilla.javascript.ast.StringLiteral;
import org.monflabs.mozilla.javascript.ast.SwitchCase;
import org.monflabs.mozilla.javascript.ast.SwitchStatement;
import org.monflabs.mozilla.javascript.ast.Symbol;
import org.monflabs.mozilla.javascript.ast.TryStatement;
import org.monflabs.mozilla.javascript.ast.UnaryExpression;
import org.monflabs.mozilla.javascript.ast.VariableDeclaration;
import org.monflabs.mozilla.javascript.ast.VariableInitializer;
import org.monflabs.mozilla.javascript.ast.WithStatement;
import org.monflabs.mozilla.javascript.config.RhinoConfig;

/**
 * Splits a function or a script too large for a class file into chunks, as Nashorn's
 * SplitIntoFunctions: runs of statements of its body, or of a block too large on its own, become
 * arrow functions called in place, which share this, arguments, new.target and super with the
 * function. The names of a chunk resolve to the bindings that the parser recorded in the enclosing
 * scopes. A return in a chunk stores its value in a variable of the function and returns a state; a
 * break or continue to a statement outside of the chunk returns a state of its own; the call site
 * replays them.
 *
 * <p>Runs on the AST, before IRFactory transforms the body, when the class compiler found the
 * function too large (CompilerEnvirons#splitFurther). Function declarations stay in place, as do
 * lexical declarations, but those of the body of a function, whose bindings are variables of the
 * function. Not split: generators and async functions (a yield cannot cross a function), derived
 * constructors (super()), and functions with a direct eval or a with statement, whose names resolve
 * at run time.
 *
 * <p>The chunks of a script keep its completion value: a chunk takes the current value as its
 * parameter (FunctionNode#SPLIT_COMPLETION, the result of the script at its call site) and returns
 * the new one, and the statements that set the completion value assign the parameter instead
 * (IRFactory). A statement with a break or continue to a statement outside of it stays in place.
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

    /** The key of a script, which has no source position of its own (FunctionNode#getSplitKey). */
    static final int SCRIPT_KEY = -2;

    private final ScriptNode owner;
    private final boolean script;
    private final int limit;
    private boolean declared;

    /** The switch statements made by #splitClauses, whose clauses are not split again. */
    private final Set<SwitchStatement> madeSwitches =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** The number of switch statements whose clauses were split, for the names of their labels. */
    private int splitSwitches;

    private Splitter(ScriptNode owner, int limit) {
        this.owner = owner;
        this.script = !(owner instanceof FunctionNode);
        this.limit = limit;
    }

    static int keyOf(ScriptNode node) {
        return node instanceof FunctionNode ? ((FunctionNode) node).getSplitKey() : SCRIPT_KEY;
    }

    /** The weight of a chunk at that split level. */
    static int chunkWeight(int level) {
        return CHUNK_WEIGHT >> (level - 1);
    }

    /** Splits the body of a function or a script; returns whether it could be split. */
    static boolean split(ScriptNode owner, int weight) {
        AstNode body = owner instanceof FunctionNode ? ((FunctionNode) owner).getBody() : owner;
        if (!canSplit(owner, body)) {
            return false;
        }
        Splitter splitter = new Splitter(owner, Math.max(weight, 1));
        try {
            return splitter.splitBlock(body);
        } catch (CannotSplit e) {
            return false;
        }
    }

    private static boolean canSplit(ScriptNode owner, AstNode body) {
        if (owner instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) owner;
            if (fn.isGenerator()
                    || fn.isES6Generator()
                    || fn.isAsync()
                    || fn.isDerivedConstructor()
                    || fn.getSplitOwner() != null
                    || !(body instanceof Block)) {
                return false;
            }
        }
        boolean[] dynamic = {false};
        body.visit(
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
        for (int i = 0; i < statements.size(); i++) {
            AstNode statement = statements.get(i);
            int w = weight(statement);
            if (w > limit && isSplittableVar(statement)) {
                // var a = 1, b = 2; is var a = 1; var b = 2;
                List<AstNode> parts = splitVar((VariableDeclaration) statement);
                statements.remove(i);
                statements.addAll(i, parts);
                statement = parts.get(0);
                w = weight(statement);
                changed = true;
            }
            if (w > limit) {
                if (statement instanceof SwitchStatement && canSplitClauses(statement)) {
                    statement = splitClauses((SwitchStatement) statement);
                    statements.set(i, statement);
                    changed = true;
                }
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
        boolean changed = splitLiterals(statement);
        if (statement instanceof Block
                || (statement instanceof Scope && isStatementBlock(statement))) {
            changed |= splitBlock(statement);
        } else if (statement instanceof Loop) {
            changed |= splitNested(((Loop) statement).getBody());
        } else if (statement instanceof IfStatement) {
            changed |= splitNested(((IfStatement) statement).getThenPart());
            changed |= splitNested(((IfStatement) statement).getElsePart());
        } else if (statement instanceof TryStatement) {
            TryStatement tryStatement = (TryStatement) statement;
            changed |= splitNested(tryStatement.getTryBlock());
            for (CatchClause clause : tryStatement.getCatchClauses()) {
                changed |= splitNested(clause.getBody());
            }
            changed |= splitNested(tryStatement.getFinallyBlock());
        } else if (statement instanceof LabeledStatement) {
            changed |= splitNested(((LabeledStatement) statement).getStatement());
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
        if (node instanceof SwitchStatement && canSplitClauses(node)) {
            node = splitClauses((SwitchStatement) node);
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

    /**
     * Whether the clauses of a switch can be split into several switch statements: in a function
     * (the chunks of a script return its completion value, so a statement with a break out of it
     * stays in place), at least two clauses each light enough, and no declaration of the switch
     * block, whose bindings all its clauses share.
     */
    private boolean canSplitClauses(AstNode node) {
        SwitchStatement sw = (SwitchStatement) node;
        if (script
                || madeSwitches.contains(sw)
                || sw.getCases().size() < 2
                || (sw.getSymbolTable() != null && !sw.getSymbolTable().isEmpty())
                || hasBlockFunction(sw)
                || sw.getParent() == null) {
            return false;
        }
        for (SwitchCase clause : sw.getCases()) {
            if (clause.getStatements() != null) {
                for (AstNode statement : clause.getStatements()) {
                    if (!isMovable(statement)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Splits a switch whose clauses are too many for one method, which the chunks of its clauses do
     * not make smaller:
     *
     * <pre>
     * %swN: {
     *   switch (discriminant) { case e0: %caseN = 0; break; ... default: %caseN = d; }
     *   if (%caseN &lt; k1) { switch (%caseN) { case 0: ... case k1 - 1: ... } %caseN = k1; }
     *   if (%caseN &lt; k2) { switch (%caseN) { case k1: ... } %caseN = k2; }
     *   ...
     * }
     * </pre>
     *
     * The first switch evaluates the discriminant and the case expressions as the original one, in
     * order until one matches, and keeps the index of the clause entered; each run of clauses is a
     * switch of its own, entered at that clause, falling through to the next run; a break to the
     * switch leaves the labeled block. The block's statements are then chunked as any other.
     */
    private LabeledStatement splitClauses(SwitchStatement sw) {
        int pos = sw.getAbsolutePosition();
        int lineno = sw.getLineno();
        int column = sw.getColumn();
        int n = splitSwitches++;
        String caseVar = "%case" + n;
        owner.putSymbol(new Symbol(Symbol.Type.VAR, caseVar));
        Label label = new Label(pos, 0, "%sw" + n);
        label.setLineColumnNumber(lineno, column);
        LabeledStatement labeled = new LabeledStatement(pos, sw.getLength());
        labeled.setLineColumnNumber(lineno, column);
        labeled.addLabel(label);
        Block block = new Block(pos, sw.getLength());
        block.setLineColumnNumber(lineno, column);

        // the breaks to the switch leave the block
        List<BreakStatement> breaks = new ArrayList<>();
        sw.visit(
                node -> {
                    if (node instanceof BreakStatement
                            && ((BreakStatement) node).getBreakTarget() == sw) {
                        breaks.add((BreakStatement) node);
                    }
                    return !(node instanceof FunctionNode);
                });
        for (BreakStatement brk : breaks) {
            BreakStatement leave = new BreakStatement(brk.getAbsolutePosition(), brk.getLength());
            leave.setBreakLabel(name(label.getName(), brk.getAbsolutePosition()));
            leave.setBreakTarget(label);
            leave.setLineColumnNumber(brk.getLineno(), brk.getColumn());
            replace(brk, leave);
        }

        Scope parentScope = sw.getParentScope();
        List<SwitchCase> clauses = new ArrayList<>(sw.getCases());
        // the clause entered: the default one, unless a case expression matches, in order
        String discriminantVar = "%disc" + n;
        owner.putSymbol(new Symbol(Symbol.Type.VAR, discriminantVar));
        AstNode discriminant = sw.getExpression();
        discriminant.setParent(null);
        block.addStatement(
                statement(assign(name(discriminantVar, pos), discriminant, pos), lineno, column));
        int defaultIndex = clauses.size();
        for (int i = 0; i < clauses.size(); i++) {
            if (clauses.get(i).isDefault()) {
                defaultIndex = i;
            }
        }
        block.addStatement(
                statement(
                        assign(name(caseVar, pos), number(defaultIndex, pos), pos),
                        lineno,
                        column));
        Label found = new Label(pos, 0, "%found" + n);
        found.setLineColumnNumber(lineno, column);
        LabeledStatement search = new LabeledStatement(pos, sw.getLength());
        search.setLineColumnNumber(lineno, column);
        search.addLabel(found);
        Block tests = new Block(pos, sw.getLength());
        tests.setLineColumnNumber(lineno, column);
        for (int i = 0; i < clauses.size(); i++) {
            SwitchCase clause = clauses.get(i);
            if (clause.isDefault()) {
                continue;
            }
            int at = clause.getAbsolutePosition();
            AstNode expression = clause.getExpression();
            expression.setParent(null);
            Block then = new Block(at, 0);
            then.setLineColumnNumber(clause.getLineno(), clause.getColumn());
            then.addStatement(
                    statement(
                            assign(name(caseVar, at), number(i, at), at),
                            clause.getLineno(),
                            clause.getColumn()));
            BreakStatement leave = new BreakStatement(at);
            leave.setBreakLabel(name(found.getName(), at));
            leave.setBreakTarget(found);
            leave.setLineColumnNumber(clause.getLineno(), clause.getColumn());
            then.addStatement(leave);
            IfStatement test = new IfStatement(at);
            test.setLineColumnNumber(clause.getLineno(), clause.getColumn());
            test.setCondition(
                    new InfixExpression(Token.SHEQ, name(discriminantVar, at), expression, at));
            test.setThenPart(then);
            tests.addStatement(test);
        }
        search.setStatement(tests);
        block.addStatement(search);

        // runs of clauses about as heavy as a chunk
        int first = 0;
        while (first < clauses.size()) {
            int end = first;
            int runWeight = 0;
            while (end < clauses.size()
                    && (end == first || runWeight + weight(clauses.get(end)) <= limit)) {
                runWeight += weight(clauses.get(end));
                end++;
            }
            SwitchStatement run = new SwitchStatement(clauses.get(first).getAbsolutePosition());
            run.setLineColumnNumber(clauses.get(first).getLineno(), clauses.get(first).getColumn());
            run.setExpression(name(caseVar, pos));
            for (int i = first; i < end; i++) {
                SwitchCase clause = clauses.get(i);
                clause.setParent(null);
                clause.setExpression(number(i, clause.getAbsolutePosition()));
                run.addCase(clause);
            }
            madeSwitches.add(run);
            adopt(parentScope, run);
            // the scopes of the clauses are the run's
            if (sw.getChildScopes() != null) {
                for (Scope child : new ArrayList<>(sw.getChildScopes())) {
                    if (isWithin(child, run)) {
                        sw.getChildScopes().remove(child);
                        run.addChildScope(child);
                    }
                }
            }
            Block then = new Block(run.getAbsolutePosition(), 0);
            then.setLineColumnNumber(run.getLineno(), run.getColumn());
            then.addStatement(run);
            then.addStatement(
                    statement(assign(name(caseVar, pos), number(end, pos), pos), lineno, column));
            IfStatement test = new IfStatement(run.getAbsolutePosition());
            test.setLineColumnNumber(run.getLineno(), run.getColumn());
            test.setCondition(
                    new InfixExpression(Token.LT, name(caseVar, pos), number(end, pos), pos));
            test.setThenPart(then);
            block.addStatement(test);
            first = end;
        }
        if (sw.getChildScopes() != null) {
            // the scopes of the discriminant and the case expressions (a class)
            for (Scope child : new ArrayList<>(sw.getChildScopes())) {
                sw.getChildScopes().remove(child);
                adopt(parentScope, child);
            }
        }
        if (parentScope != null && parentScope.getChildScopes() != null) {
            parentScope.getChildScopes().remove(sw);
        }
        labeled.setStatement(block);
        replace(sw, labeled);
        return labeled;
    }

    /**
     * Splits the expressions of a statement too heavy for a chunk, outside of its blocks and nested
     * functions, innermost first: object literals, comma expressions, and the operands of ?:, &&,
     * || and ??; returns whether it did.
     */
    private boolean splitLiterals(AstNode statement) {
        List<AstNode> expressions = new ArrayList<>();
        statement.visit(
                node -> {
                    if (node instanceof FunctionNode
                            || (node != statement
                                    && (node instanceof Block
                                            || (node instanceof Scope
                                                    && isStatementBlock(node))))) {
                        return false;
                    }
                    if ((node instanceof ObjectLiteral && !((ObjectLiteral) node).isDestructuring())
                            || node instanceof ConditionalExpression
                            || isLogical(node)
                            || (isComma(node) && !isComma(node.getParent()))) {
                        expressions.add(node);
                    }
                    return true;
                });
        boolean changed = false;
        for (int i = expressions.size() - 1; i >= 0; i--) {
            AstNode expression = expressions.get(i);
            if (weight(expression) <= limit) {
                continue;
            }
            if (expression instanceof ObjectLiteral) {
                changed |= splitLiteral((ObjectLiteral) expression);
            } else if (expression instanceof ConditionalExpression) {
                ConditionalExpression conditional = (ConditionalExpression) expression;
                changed |=
                        splitOperand(
                                conditional.getTestExpression(), conditional::setTestExpression);
                changed |=
                        splitOperand(
                                conditional.getTrueExpression(), conditional::setTrueExpression);
                changed |=
                        splitOperand(
                                conditional.getFalseExpression(), conditional::setFalseExpression);
            } else if (isComma(expression)) {
                changed |= splitComma((InfixExpression) expression);
            } else {
                InfixExpression logical = (InfixExpression) expression;
                changed |= splitOperand(logical.getLeft(), logical::setLeft);
                changed |= splitOperand(logical.getRight(), logical::setRight);
            }
        }
        return changed;
    }

    private static boolean isComma(AstNode node) {
        return node instanceof InfixExpression && node.getType() == Token.COMMA;
    }

    private static boolean isLogical(AstNode node) {
        return node instanceof InfixExpression
                && (node.getType() == Token.AND
                        || node.getType() == Token.OR
                        || node.getType() == Token.NULLISH_COALESCING);
    }

    /**
     * Moves runs of the operands of a comma expression to chunks returning the value of their last
     * operand, (a, b, c, d) being ((() => (a, b))(), (() => (c, d))()): the same values, in the
     * same order, the last one being the result.
     */
    private boolean splitComma(InfixExpression comma) {
        List<AstNode> operands = new ArrayList<>();
        addOperands(comma, operands);
        if (weight(operands) <= limit) {
            return false;
        }
        List<AstNode> result = new ArrayList<>();
        List<AstNode> run = new ArrayList<>();
        int runWeight = 0;
        boolean changed = false;
        for (AstNode operand : operands) {
            int w = weight(operand);
            if (!run.isEmpty() && runWeight + w > limit) {
                changed |= flushOperands(run, result);
                runWeight = 0;
            }
            run.add(operand);
            runWeight += w;
        }
        changed |= flushOperands(run, result);
        if (!changed) {
            return false;
        }
        AstNode last = result.remove(result.size() - 1);
        AstNode left = result.size() == 1 ? result.get(0) : sequence(result);
        left.setParent(null);
        last.setParent(null);
        comma.setLeft(left);
        comma.setRight(last);
        return true;
    }

    private static void addOperands(AstNode node, List<AstNode> operands) {
        if (isComma(node)) {
            addOperands(((InfixExpression) node).getLeft(), operands);
            addOperands(((InfixExpression) node).getRight(), operands);
        } else {
            operands.add(node);
        }
    }

    /** The comma expression of the operands, from the left. */
    private static AstNode sequence(List<AstNode> operands) {
        AstNode result = operands.get(0);
        result.setParent(null);
        for (int i = 1; i < operands.size(); i++) {
            AstNode operand = operands.get(i);
            int position = operand.getAbsolutePosition();
            operand.setParent(null);
            result = new InfixExpression(Token.COMMA, result, operand, position);
            result.setLineColumnNumber(operands.get(0).getLineno(), operands.get(0).getColumn());
        }
        return result;
    }

    /** Replaces a run of operands of a comma expression by the call of a chunk returning them. */
    private boolean flushOperands(List<AstNode> run, List<AstNode> result) {
        if (run.size() == 1 || weight(run) < Math.min(limit, 16) || hasLegacyScope(run)) {
            result.addAll(run);
            run.clear();
            return false;
        }
        int start = run.get(0).getAbsolutePosition();
        AstNode last = run.get(run.size() - 1);
        int end = last.getAbsolutePosition() + last.getLength();
        result.add(valueChunk(sequence(run), start, end, run.get(0), last));
        run.clear();
        return true;
    }

    private static boolean hasLegacyScope(List<AstNode> expressions) {
        for (AstNode expression : expressions) {
            if (hasLegacyScope(expression)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves an operand of a ?: too heavy for half a chunk to a chunk returning its value, (() =>
     * operand)(): a value, not a reference, which the arrow function evaluates with the same this,
     * arguments and bindings.
     */
    private boolean splitOperand(AstNode operand, Consumer<AstNode> setter) {
        if (weight(operand) <= limit / 2 || hasLegacyScope(operand)) {
            return false;
        }
        int start = operand.getAbsolutePosition();
        int end = start + operand.getLength();
        operand.setParent(null);
        setter.accept(valueChunk(operand, start, end, operand, operand));
        return true;
    }

    /** The call of a chunk returning the value of an expression, (() => expression)(). */
    private FunctionCall valueChunk(
            AstNode expression, int start, int end, AstNode first, AstNode last) {
        ReturnStatement ret = new ReturnStatement(start);
        ret.setReturnValue(expression);
        ret.setLineColumnNumber(first.getLineno(), first.getColumn());
        Block body = new Block(start, end - start);
        body.setLineColumnNumber(first.getLineno(), first.getColumn());
        body.addStatement(ret);
        FunctionNode chunk = newChunk(body, start, end, first.getLineno(), first.getColumn(), last);
        moveScopes(body, chunk);
        FunctionCall call = new FunctionCall(start, end - start);
        call.setTarget(new ParenthesizedExpression(start, end - start, chunk));
        call.setArguments(new ArrayList<>());
        call.setLineColumnNumber(first.getLineno(), first.getColumn());
        return call;
    }

    /**
     * Moves runs of the properties of an object literal to chunks whose literals it spreads, {a: 1,
     * ...(() => ({b: 2, c: 3}))()}: the spread defines their data properties in the same order, at
     * the same point. Accessors, methods (their home object is the literal) and __proto__ stay.
     */
    private boolean splitLiteral(ObjectLiteral literal) {
        List<AbstractObjectProperty> elements = literal.getElements();
        List<AbstractObjectProperty> result = new ArrayList<>();
        List<AbstractObjectProperty> run = new ArrayList<>();
        int runWeight = 0;
        boolean changed = false;
        for (AbstractObjectProperty element : elements) {
            if (!isMovableProperty(element)) {
                changed |= flushProperties(run, result);
                runWeight = 0;
                result.add(element);
                continue;
            }
            int w = weight(element);
            if (!run.isEmpty() && runWeight + w > limit) {
                changed |= flushProperties(run, result);
                runWeight = 0;
            }
            run.add(element);
            runWeight += w;
        }
        changed |= flushProperties(run, result);
        if (changed) {
            for (AbstractObjectProperty element : elements) {
                element.setParent(null);
            }
            literal.setElements(result);
        }
        return changed;
    }

    /**
     * Whether an expression holds an array comprehension, a generator expression or a let
     * expression of JavaScript 1.7, whose scopes IRFactory links as it transforms them.
     */
    private static boolean hasLegacyScope(AstNode expression) {
        boolean[] found = {false};
        expression.visit(
                node -> {
                    if (node instanceof ArrayComprehension
                            || node instanceof GeneratorExpression
                            || node instanceof LetNode) {
                        found[0] = true;
                    }
                    return !found[0] && !(node instanceof FunctionNode);
                });
        return found[0];
    }

    private static boolean isMovableProperty(AbstractObjectProperty element) {
        if (element instanceof SpreadObjectProperty) {
            return true;
        }
        if (!(element instanceof ObjectProperty)) {
            return false;
        }
        ObjectProperty property = (ObjectProperty) element;
        if (property.isMethod() || hasLegacyScope(property)) {
            return false;
        }
        AstNode key = property.getKey();
        String name =
                key instanceof Name
                        ? ((Name) key).getIdentifier()
                        : key instanceof StringLiteral ? ((StringLiteral) key).getValue() : null;
        return !"__proto__".equals(name);
    }

    /** Replaces a run of properties by the spread of a chunk returning them in a literal. */
    private boolean flushProperties(
            List<AbstractObjectProperty> run, List<AbstractObjectProperty> result) {
        if (run.isEmpty()) {
            return false;
        }
        if (run.size() == 1 || weight(run) < Math.min(limit, 16)) {
            result.addAll(run);
            run.clear();
            return false;
        }
        AstNode first = run.get(0);
        AstNode last = run.get(run.size() - 1);
        int start = first.getAbsolutePosition();
        int end = last.getAbsolutePosition() + last.getLength();
        ObjectLiteral part = new ObjectLiteral(start, end - start);
        part.setLineColumnNumber(first.getLineno(), first.getColumn());
        for (AbstractObjectProperty element : run) {
            element.setParent(null);
            part.addElement(element);
        }
        Spread spread = new Spread(start, end - start);
        spread.setExpression(valueChunk(part, start, end, first, last));
        spread.setLineColumnNumber(first.getLineno(), first.getColumn());
        SpreadObjectProperty spreadProperty = new SpreadObjectProperty(spread);
        spreadProperty.setLineColumnNumber(first.getLineno(), first.getColumn());
        result.add(spreadProperty);
        run.clear();
        return true;
    }

    private static int weight(List<? extends AstNode> nodes) {
        int total = 0;
        for (AstNode node : nodes) {
            total += weight(node);
        }
        return total;
    }

    private static boolean isSplittableVar(AstNode statement) {
        return statement instanceof VariableDeclaration
                && statement.getType() == Token.VAR
                && ((VariableDeclaration) statement).isStatement()
                && ((VariableDeclaration) statement).getVariables().size() > 1;
    }

    /** The var statements of runs of the declarators of one, each about as heavy as a chunk. */
    private List<AstNode> splitVar(VariableDeclaration declaration) {
        List<AstNode> parts = new ArrayList<>();
        VariableDeclaration part = null;
        int partWeight = 0;
        for (VariableInitializer declarator : declaration.getVariables()) {
            int w = weight(declarator);
            if (part == null || partWeight + w > limit) {
                part = new VariableDeclaration(declarator.getAbsolutePosition());
                part.setType(Token.VAR);
                part.setIsStatement(true);
                part.setLineColumnNumber(declarator.getLineno(), declarator.getColumn());
                parts.add(part);
                partWeight = 0;
            }
            declarator.setParent(null);
            part.addVariable(declarator);
            part.setLength(
                    declarator.getAbsolutePosition()
                            + declarator.getLength()
                            - part.getAbsolutePosition());
            partWeight += w;
        }
        return parts;
    }

    /** Makes a new scope a child of the scope of the statement it replaces. */
    private static void adopt(Scope parent, Scope scope) {
        if (parent != null) {
            parent.addChildScope(scope);
        }
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
    private boolean isMovable(AstNode statement) {
        if (script && hasJumpOut(statement)) {
            // the return value of the chunk of a script is its completion value
            return false;
        }
        if (statement instanceof FunctionNode
                || statement instanceof ClassNode
                || hasBlockFunction(statement)) {
            return false;
        }
        if (statement instanceof VariableDeclaration) {
            return statement.getType() == Token.VAR || isFunctionLexical(statement);
        }
        if (statement instanceof ExpressionStatement) {
            AstNode expression = ((ExpressionStatement) statement).getExpression();
            return !(expression instanceof FunctionNode || expression instanceof ClassNode);
        }
        return true;
    }

    /**
     * Whether a statement is a let, const or class declaration of the body of a function: its
     * bindings are variables of the function, which a chunk initializes through its scope chain, as
     * those of a var. A destructuring declaration stays: its stores are not marked as
     * initializations.
     */
    private boolean isFunctionLexical(AstNode statement) {
        if (script || statement.getParent() != ((FunctionNode) owner).getBody()) {
            return false;
        }
        for (VariableInitializer declarator : ((VariableDeclaration) statement).getVariables()) {
            if (declarator.isDestructuring()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a break or continue in the statement jumps to a statement outside of it. */
    private static boolean hasJumpOut(AstNode statement) {
        boolean[] found = {false};
        statement.visit(
                n -> {
                    if (n instanceof FunctionNode) {
                        return false;
                    }
                    AstNode target =
                            n instanceof BreakStatement
                                    ? ((BreakStatement) n).getBreakTarget()
                                    : n instanceof ContinueStatement
                                            ? ((ContinueStatement) n).getTarget()
                                            : null;
                    found[0] |= target != null && !isWithin(target, statement);
                    return !found[0];
                });
        return found[0];
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

        FunctionNode chunk = newChunk(body, start, end, lineno, column, last);
        if (script) {
            // (%completion) => { statements; return %completion; }; the parameter is the first
            // variable of the chunk
            chunk.addParam(name(FunctionNode.SPLIT_COMPLETION, start));
            chunk.putSymbol(new Symbol(Symbol.Type.LP, FunctionNode.SPLIT_COMPLETION));
        }
        moveScopes(body, chunk);

        IdentityHashMap<AstNode, Integer> states = new IdentityHashMap<>();
        List<AstNode> replays = new ArrayList<>();
        rewriteJumps(body, body, states, replays);

        FunctionCall call = new FunctionCall(start, end - start);
        call.setTarget(new ParenthesizedExpression(start, end - start, chunk));
        call.setArguments(new ArrayList<>());
        call.setLineColumnNumber(lineno, column);
        if (script) {
            ReturnStatement ret = new ReturnStatement(end);
            ret.setReturnValue(name(FunctionNode.SPLIT_COMPLETION, end));
            ret.setLineColumnNumber(last.getLineno(), last.getColumn());
            body.addStatement(ret);
            call.addArgument(name(FunctionNode.SPLIT_COMPLETION, start));
            List<AstNode> statements = new ArrayList<>();
            ExpressionStatement statement = new ExpressionStatement(call, true);
            statement.setLineColumnNumber(lineno, column);
            statements.add(statement);
            return statements;
        }

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

    /** An arrow function of the owner holding body, called in place. */
    private FunctionNode newChunk(
            Block body, int start, int end, int lineno, int column, AstNode last) {
        FunctionNode chunk = new FunctionNode(start);
        chunk.setFunctionType(FunctionNode.ARROW_FUNCTION);
        chunk.setInStrictMode(owner.isInStrictMode());
        chunk.setBody(body);
        chunk.setLength(end - start);
        chunk.setSplitOwner(owner);
        chunk.setSplitKey(keyOf(owner));
        chunk.setSourceName(owner.getSourceName());
        chunk.setBaseLineno(lineno);
        chunk.setEndLineno(last.getLineno());
        chunk.setLineColumnNumber(lineno, column);
        return chunk;
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
                top = parent != null && tops.get(parent) != null ? tops.get(parent) : owner;
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
            owner.putSymbol(new Symbol(Symbol.Type.VAR, RETURN_VALUE));
            owner.putSymbol(new Symbol(Symbol.Type.VAR, STATE));
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
