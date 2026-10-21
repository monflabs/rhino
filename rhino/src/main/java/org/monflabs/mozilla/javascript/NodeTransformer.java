/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.Context.reportError;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.ClassNode;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.Jump;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.SwitchCase;
import org.monflabs.mozilla.javascript.ast.SwitchStatement;
import org.monflabs.mozilla.javascript.ast.Symbol;
import org.monflabs.mozilla.javascript.ast.VariableInitializer;

/**
 * This class transforms a tree to a lower-level representation for codegen.
 *
 * @see Node
 * @author Norris Boyd
 */
public class NodeTransformer {

    public NodeTransformer() {}

    public final void transform(ScriptNode tree, CompilerEnvirons env) {
        transform(tree, false, env);
    }

    public final void transform(ScriptNode tree, boolean inStrictMode, CompilerEnvirons env) {
        boolean useStrictMode = inStrictMode;
        // Support strict mode inside a function only for "ES6" language level
        // and above. Otherwise, we will end up breaking backward compatibility for
        // many existing scripts.
        if ((env.getLanguageVersion() >= Context.VERSION_ES6) && tree.isInStrictMode()) {
            useStrictMode = true;
        }
        tdz = env.isLatestEcmaScript();
        inEval = env.isInEval();
        activationNames = env.getActivationNames();
        registersWithActivation = env.isLatestEcmaScript();
        transformCompilationUnit(tree, useStrictMode);
        for (int i = 0; i != tree.getFunctionCount(); ++i) {
            FunctionNode fn = tree.getFunctionNode(i);
            transform(fn, useStrictMode, env);
        }
    }

    private void transformCompilationUnit(ScriptNode tree, boolean inStrictMode) {
        loops = new ArrayDeque<>();
        loopEnds = new ArrayDeque<>();

        // to save against upchecks if no finally blocks are used.
        hasFinally = false;

        // Flatten all only if we are not using scope objects for block scope
        boolean createScopeObjects =
                tree.getType() != Token.FUNCTION || ((FunctionNode) tree).requiresActivation();
        tree.flattenSymbolTable(!createScopeObjects);
        registerNames = null;
        registerLexicals = null;
        activationVarNames = null;
        if (registersWithActivation && createScopeObjects && tree.getType() == Token.FUNCTION) {
            keepVarsInRegisters((FunctionNode) tree);
        }
        if (tdz && createScopeObjects && tree.getType() == Token.FUNCTION) {
            initFunctionLexicalsUninitialized(tree);
        }
        if (tdz && tree.getType() == Token.SCRIPT && !inEval) {
            declareGlobalLexicals(tree);
        }

        // uncomment to print tree before transformation
        if (Token.printTrees) System.out.println(tree.toStringTree(tree));
        if (tree.getType() == Token.FUNCTION) {
            // the parameters of a generator, initialized before its body (see IRFactory)
            Node paramInit = ((FunctionNode) tree).getGeneratorParamInitBlock();
            if (paramInit != null) {
                transformCompilationUnit_r(tree, paramInit, tree, createScopeObjects, inStrictMode);
            }
        }
        transformCompilationUnit_r(tree, tree, tree, createScopeObjects, inStrictMode);
    }

    private void transformCompilationUnit_r(
            final ScriptNode tree,
            final Node parent,
            Scope scope,
            boolean createScopeObjects,
            boolean inStrictMode) {
        Node node = null;
        siblingLoop:
        for (; ; ) {
            Node previous = null;
            if (node == null) {
                node = parent.getFirstChild();
            } else {
                previous = node;
                node = node.getNext();
            }
            if (node == null) {
                break;
            }

            int type = node.getType();
            if (tdz
                    && !createScopeObjects
                    && node instanceof Scope
                    && isSwitchScope((Scope) node)
                    && ((Scope) node).getSymbolTable() != null
                    && uninitializedSwitches.add((Scope) node)) {
                // its bindings live in registers: uninitialized whenever the switch is entered,
                // for the clauses entered past their declarations (see classifyTdz)
                for (Symbol symbol : ((Scope) node).getSymbolTable().values()) {
                    if (isLexical(symbol)
                            && isSingleNameDeclaration(symbol.getNode())
                            && hasOtherClauseReference(
                                    node,
                                    symbol.getName(),
                                    switchClause((AstNode) symbol.getNode()))) {
                        Node set =
                                new Node(
                                        symbol.getDeclType() == Symbol.Type.CONST
                                                ? Token.SETCONST
                                                : Token.SETNAME,
                                        Node.newString(Token.BINDNAME, symbol.getName()),
                                        new Node(Token.TDZ_SENTINEL));
                        set.putIntProp(Node.TDZ_DECLARATION_PROP, 1);
                        node.addChildToFront(new Node(Token.EXPR_VOID, set));
                    }
                }
            }
            if (createScopeObjects
                    && (type == Token.BLOCK || type == Token.LOOP || type == Token.ARRAYCOMP)
                    && (node instanceof Scope)) {
                Scope newScope = (Scope) node;
                if (newScope.getSymbolTable() != null
                        && registerLexicals != null
                        && registerLexicals.containsAll(newScope.getSymbolTable().values())) {
                    // all its bindings live in registers: no scope object
                    unwrappedScopes.add(newScope);
                } else if (newScope.getSymbolTable() != null && wrappedScopes.add(newScope)) {
                    // transform to let statement so we get a with statement
                    // created to contain scoped let variables
                    Node let = new Node(type == Token.ARRAYCOMP ? Token.LETEXPR : Token.LET);
                    Node innerLet = new Node(Token.LET);
                    let.addChildToBack(innerLet);
                    for (Symbol symbol : newScope.getSymbolTable().values()) {
                        if (registerLexicals != null && registerLexicals.contains(symbol)) {
                            continue;
                        }
                        Node name = Node.newString(Token.NAME, symbol.getName());
                        if (tdz && isLexical(symbol) && symbol.getNode() != null) {
                            // starts in its temporal dead zone, see visitLet
                            name.putIntProp(Node.LEXICAL_PROP, 1);
                        }
                        innerLet.addChildToBack(name);
                    }
                    // the symbol table is kept: references in the block are still resolved
                    // against it (temporal dead zone)
                    Node oldNode = node;
                    node = replaceCurrent(parent, previous, node, let);
                    type = node.getType();
                    let.addChildToBack(oldNode);
                }
            }

            if (tdz && node.getIntProp(Node.TDZ_CHECKED_PROP, 0) == 0) {
                Node replacement =
                        checkTemporalDeadZone(
                                tree, parent, previous, node, scope, createScopeObjects);
                if (replacement != node) {
                    node = replacement;
                    type = node.getType();
                }
            }

            switch (type) {
                case Token.COPY_SCOPE:
                    if (!createScopeObjects
                            || unwrappedScopes.contains(scope)
                            || unwrappedScopes.contains(scope.getParentScope())) {
                        // bindings live in registers: no closure can observe a per-iteration copy
                        node = replaceCurrent(parent, previous, node, new Node(Token.EMPTY));
                    }
                    break;

                case Token.LABEL:
                case Token.SWITCH:
                case Token.LOOP:
                    loops.push(node);
                    loopEnds.push(((Jump) node).target);
                    break;

                case Token.WITH:
                    {
                        loops.push(node);
                        Node leave = node.getNext();
                        if (leave.getType() != Token.LEAVE_SCOPE) {
                            Kit.codeBug();
                        }
                        loopEnds.push(leave);
                        break;
                    }

                case Token.SCOPE_BLOCK:
                    {
                        loops.push(node);
                        Node leave = node.getNext();
                        if (leave.getType() != Token.LEAVE_SCOPE) {
                            Kit.codeBug();
                        }
                        loopEnds.push(leave);
                        break;
                    }

                case Token.TRY:
                    {
                        Jump jump = (Jump) node;
                        Node finallytarget = jump.getFinally();
                        if (finallytarget != null) {
                            hasFinally = true;
                            loops.push(node);
                            loopEnds.push(finallytarget);
                        }
                        break;
                    }

                case Token.TARGET:
                case Token.LEAVE_SCOPE:
                    if (!loopEnds.isEmpty() && loopEnds.peek() == node) {
                        loopEnds.pop();
                        loops.pop();
                    }
                    break;

                case Token.YIELD:
                case Token.YIELD_STAR:
                    ((FunctionNode) tree).addResumptionPoint(node);
                    break;

                case Token.RETURN:
                    {
                        boolean isGenerator =
                                tree.getType() == Token.FUNCTION
                                        && ((FunctionNode) tree).isGenerator();
                        if (isGenerator) {
                            node.putIntProp(Node.GENERATOR_END_PROP, 1);
                        }
                        /* If we didn't support try/finally, it wouldn't be
                         * necessary to put LEAVEWITH nodes here... but as
                         * we do need a series of JSR FINALLY nodes before
                         * each RETURN, we need to ensure that each finally
                         * block gets the correct scope... which could mean
                         * that some LEAVEWITH nodes are necessary.
                         */
                        if (!hasFinally) break; // skip the whole mess.
                        Node unwindBlock = null;
                        // Iterate from the top of the stack (most recently inserted) and down
                        for (Node n : loops) {
                            int elemtype = n.getType();
                            if (elemtype == Token.TRY
                                    || elemtype == Token.WITH
                                    || elemtype == Token.SCOPE_BLOCK
                                    || isForOf(n)) {
                                Node unwind;
                                if (isForOf(n)) {
                                    unwind = enumClose(n, tree);
                                } else if (elemtype == Token.TRY) {
                                    Jump jsrnode = new Jump(Token.JSR);
                                    jsrnode.target = ((Jump) n).getFinally();
                                    unwind = jsrnode;
                                } else {
                                    unwind = new Node(Token.LEAVE_SCOPE);
                                    unwind.putIntProp(Node.JUMP_EXIT_PROP, 1);
                                }
                                if (unwindBlock == null) {
                                    unwindBlock = new Node(Token.BLOCK);
                                    unwind.setLineColumnNumber(node.getLineno(), node.getColumn());
                                }
                                unwindBlock.addChildToBack(unwind);
                            }
                        }
                        if (unwindBlock != null) {
                            Node returnNode = node;
                            Node returnExpr = returnNode.getFirstChild();
                            node = replaceCurrent(parent, previous, node, unwindBlock);
                            if (returnExpr == null || isGenerator) {
                                unwindBlock.addChildToBack(returnNode);
                            } else {
                                Node store = new Node(Token.EXPR_RESULT, returnExpr);
                                unwindBlock.addChildToFront(store);
                                returnNode = new Node(Token.RETURN_RESULT);
                                unwindBlock.addChildToBack(returnNode);
                                // transform return expression
                                transformCompilationUnit_r(
                                        tree, store, scope, createScopeObjects, inStrictMode);
                            }
                            // skip transformCompilationUnit_r to avoid infinite loop
                            continue siblingLoop;
                        }
                        break;
                    }

                case Token.BREAK:
                case Token.CONTINUE:
                    {
                        Jump jump = (Jump) node;
                        Jump jumpStatement = jump.getJumpStatement();
                        if (jumpStatement == null) Kit.codeBug();

                        if (loops.isEmpty()) {
                            // Parser/IRFactory ensure that break/continue
                            // always has a jump statement associated with it
                            // which should be found
                            throw Kit.codeBug();
                        }
                        // Iterate from the top of the stack (most recently inserted) and down
                        for (Node n : loops) {
                            if (n == jumpStatement) {
                                if (type == Token.BREAK && isForOf(n)) {
                                    previous =
                                            addBeforeCurrent(
                                                    parent, previous, node, enumClose(n, tree));
                                }
                                break;
                            }

                            int elemtype = n.getType();
                            if (isForOf(n)) {
                                previous =
                                        addBeforeCurrent(
                                                parent, previous, node, enumClose(n, tree));
                            } else if (elemtype == Token.WITH
                                    || elemtype == Token.SCOPEEXPR
                                    || elemtype == Token.SCOPE_BLOCK) {
                                Node leave = new Node(Token.LEAVE_SCOPE);
                                leave.putIntProp(Node.JUMP_EXIT_PROP, 1);
                                previous = addBeforeCurrent(parent, previous, node, leave);
                            } else if (elemtype == Token.TRY) {
                                Jump tryNode = (Jump) n;
                                Jump jsrFinally = new Jump(Token.JSR);
                                jsrFinally.target = tryNode.getFinally();
                                previous = addBeforeCurrent(parent, previous, node, jsrFinally);
                            }
                        }

                        if (type == Token.BREAK) {
                            jump.target = jumpStatement.target;
                        } else {
                            jump.target = jumpStatement.getContinue();
                        }
                        jump.setType(Token.GOTO);

                        break;
                    }

                case Token.CALL:
                    visitCall(node, tree);
                    break;

                case Token.NEW:
                    visitNew(node, tree);
                    break;

                case Token.LETEXPR:
                case Token.LET:
                    {
                        Node child = node.getFirstChild();
                        if (child.getType() == Token.LET) {
                            // We have a let statement or expression rather than a
                            // let declaration
                            boolean createWith =
                                    tree.getType() != Token.FUNCTION
                                            || ((FunctionNode) tree).requiresActivation();
                            if (createWith && inRegisters(node)) {
                                // all its bindings live in registers: initialized as variables
                                createWith = false;
                                unwrappedScopes.add((Scope) node);
                            }
                            node = visitLet(createWith, parent, previous, node);
                            break;
                        }
                        // fall through to process let declaration...
                    }
                /* fall through */
                case Token.CONST:
                case Token.VAR:
                    {
                        Node result = new Node(Token.BLOCK);
                        for (Node cursor = node.getFirstChild(); cursor != null; ) {
                            // Move cursor to next before createAssignment gets chance
                            // to change n.next
                            Node n = cursor;
                            cursor = cursor.getNext();
                            if (n.getType() == Token.NAME) {
                                if (!n.hasChildren()) {
                                    if (type != Token.LET
                                            || !initializesLet(
                                                    tree, scope, n, createScopeObjects)) {
                                        continue;
                                    }
                                    // "let x;" initializes x to undefined: it may be in its
                                    // temporal dead zone, or still hold the value of a previous
                                    // loop iteration when it lives in a register
                                    n.addChildToBack(new Node(Token.VOID, Node.newNumber(0.0)));
                                }
                                Node init = n.getFirstChild();
                                n.removeChild(init);
                                n.setType(Token.BINDNAME);
                                n =
                                        new Node(
                                                type == Token.CONST
                                                        ? Token.SETCONST
                                                        : Token.SETNAME,
                                                n,
                                                init);
                                n.putIntProp(Node.TDZ_DECLARATION_PROP, 1);
                            } else {
                                // May be a destructuring assignment already transformed
                                // to a LETEXPR
                                if (n.getType() != Token.LETEXPR) throw Kit.codeBug();
                            }
                            Node pop = new Node(Token.EXPR_VOID, n);
                            pop.setLineColumnNumber(node.getLineno(), node.getColumn());
                            result.addChildToBack(pop);
                        }
                        node = replaceCurrent(parent, previous, node, result);
                        break;
                    }

                case Token.TYPEOFNAME:
                    {
                        Scope defining = scope.getDefiningScope(node.getString());
                        if (defining != null) {
                            node.setScope(defining);
                        }
                    }
                    break;

                case Token.TYPEOF:
                case Token.IFNE:
                    {
                        /* We want to suppress warnings for undefined property o.p
                         * for the following constructs: typeof o.p, if (o.p),
                         * if (!o.p), if (o.p == undefined), if (undefined == o.p)
                         */
                        Node child = node.getFirstChild();
                        if (type == Token.IFNE) {
                            while (child.getType() == Token.NOT) {
                                child = child.getFirstChild();
                            }
                            if (child.getType() == Token.EQ || child.getType() == Token.NE) {
                                Node first = child.getFirstChild();
                                Node last = child.getLastChild();
                                if (first.getType() == Token.UNDEFINED) {
                                    child = last;
                                } else if (last.getType() == Token.UNDEFINED) {
                                    child = first;
                                }
                            }
                        }
                        if (child.getType() == Token.GETPROP) {
                            child.setType(Token.GETPROPNOWARN);
                        }
                        break;
                    }

                case Token.SETNAME:
                    if (isSelfNameAssignment(tree, node, scope)) {
                        // the binding of a function expression's name is immutable
                        Node value = node.getLastChild();
                        node.removeChild(value);
                        Node assign =
                                Node.newString(
                                        Token.FN_NAME_ASSIGN, node.getFirstChild().getString());
                        assign.addChildToBack(value);
                        if (inStrictMode) {
                            assign.putIntProp(Node.STRICT_PROP, 1);
                        }
                        node = replaceCurrent(parent, previous, node, assign);
                        break;
                    }
                    if (inStrictMode) {
                        node.setType(Token.STRICT_SETNAME);
                        if (node.getFirstChild().getType() == Token.BINDNAME) {
                            Node name = node.getFirstChild();
                            if (name instanceof Name
                                    && "eval".equals(((Name) name).getIdentifier())) {
                                // Don't allow set of `eval` in strict mode
                                reportError("syntax error");
                            }
                        }
                    }
                /* fall through */
                case Token.NAME:
                case Token.SETCONST:
                case Token.DELPROP:
                    {
                        if (registersWithActivation && markOuterVar(tree, node, scope)) {
                            break;
                        }
                        // Turn name to var for faster access if possible
                        if (createScopeObjects
                                && registerNames == null
                                && registerLexicals == null
                                && activationVarNames == null) {
                            break;
                        }
                        Node nameSource;
                        if (type == Token.NAME) {
                            nameSource = node;
                        } else {
                            nameSource = node.getFirstChild();
                            if (nameSource.getType() != Token.BINDNAME) {
                                if (type == Token.DELPROP) {
                                    break;
                                }
                                throw Kit.codeBug();
                            }
                        }
                        if (nameSource.getScope() != null) {
                            break; // already have a scope set
                        }
                        String name = nameSource.getString();
                        Scope defining = scope.getDefiningScope(name);
                        boolean lexicalRegister =
                                defining != null
                                        && registerLexicals != null
                                        && registerLexicals.contains(defining.getSymbol(name));
                        if (createScopeObjects && !lexicalRegister) {
                            if (defining == tree
                                    && activationVarNames != null
                                    && activationVarNames.contains(name)
                                    && type != Token.SETCONST
                                    && type != Token.DELPROP) {
                                // read or assigned at its position in the activation
                                node.putIntProp(
                                        Node.ACTIVATION_VAR_PROP,
                                        defining.getSymbol(name).getIndex());
                                break;
                            }
                            if (defining != tree
                                    || registerNames == null
                                    || !registerNames.contains(name)) {
                                break;
                            }
                        }
                        if (defining != null) {
                            nameSource.setScope(defining);
                            if (type == Token.NAME) {
                                node.setType(Token.GETVAR);
                            } else if (type == Token.SETNAME || type == Token.STRICT_SETNAME) {
                                node.setType(Token.SETVAR);
                                nameSource.setType(Token.STRING);
                            } else if (type == Token.SETCONST) {
                                node.setType(Token.SETCONSTVAR);
                                nameSource.setType(Token.STRING);
                            } else if (type == Token.DELPROP) {
                                // Local variables are by definition permanent
                                Node n = new Node(Token.FALSE);
                                node = replaceCurrent(parent, previous, node, n);
                            } else {
                                throw Kit.codeBug();
                            }
                        }
                        break;
                    }

                case Token.OBJECTLIT:
                    {
                        Object[] propertyIds = (Object[]) node.getProp(Node.OBJECT_IDS_PROP);
                        if (propertyIds != null) {
                            for (Object propertyId : propertyIds) {
                                if (!(propertyId instanceof Node)) continue;
                                transformCompilationUnit_r(
                                        tree,
                                        (Node) propertyId,
                                        node instanceof Scope ? (Scope) node : scope,
                                        createScopeObjects,
                                        inStrictMode);
                            }
                        }
                    }
            }

            transformCompilationUnit_r(
                    tree,
                    node,
                    node instanceof Scope ? (Scope) node : scope,
                    createScopeObjects,
                    inStrictMode);
        }
    }

    /**
     * Marks a NAME or SETNAME of a variable that an enclosing function reads at a fixed position of
     * its activation (Node#OUTER_VAR_PROP); returns whether it did.
     */
    private static boolean markOuterVar(ScriptNode tree, Node node, Scope scope) {
        if (!(tree instanceof FunctionNode) || ((FunctionNode) tree).getOuterVars() == null) {
            return false;
        }
        int type = node.getType();
        Node nameSource;
        if (type == Token.NAME) {
            nameSource = node;
        } else if (type == Token.SETNAME || type == Token.STRICT_SETNAME) {
            nameSource = node.getFirstChild();
            if (nameSource.getType() != Token.BINDNAME) {
                return false;
            }
        } else {
            return false;
        }
        String name = nameSource.getString();
        Integer var = ((FunctionNode) tree).getOuterVars().get(name);
        if (var == null || nameSource.getScope() != null || scope.getDefiningScope(name) != null) {
            return false;
        }
        node.putIntProp(Node.OUTER_VAR_PROP, var);
        return true;
    }

    /** An assignment to the name of the function expression being compiled, see IRFactory. */
    private static boolean isSelfNameAssignment(ScriptNode tree, Node setName, Scope scope) {
        if (!(tree instanceof FunctionNode)
                || !((FunctionNode) tree).hasSelfNameBinding()
                || setName.getIntProp(Node.TDZ_DECLARATION_PROP, 0) == 1
                || setName.getFirstChild().getType() != Token.BINDNAME) {
            return false;
        }
        String name = setName.getFirstChild().getString();
        return name.equals(((FunctionNode) tree).getName()) && scope.getDefiningScope(name) == tree;
    }

    /**
     * A for-of loop, which closes its iterator when a jump leaves it (see IRFactory#createForIn).
     */
    private static boolean isForOf(Node loop) {
        return loop.getType() == Token.LOOP && loop.getProp(Node.LOCAL_BLOCK_PROP) != null;
    }

    private static Node enumClose(Node forOf, ScriptNode tree) {
        if (forOf.getIntProp(Node.AWAIT_KIND_PROP, -1) != -1) {
            // a for await loop: AsyncIteratorClose, by the driver of the async body
            Node yield =
                    new Node(
                            Token.YIELD,
                            IRFactory.awaitNode(
                                    Node.AWAIT_ITER_CLOSE,
                                    IRFactory.createUseLocal(
                                            (Node) forOf.getProp(Node.LOCAL_BLOCK_PROP))));
            ((FunctionNode) tree).addResumptionPoint(yield);
            return new Node(Token.EXPR_VOID, yield);
        }
        Node close = new Node(Token.ENUM_CLOSE);
        close.putProp(Node.LOCAL_BLOCK_PROP, forOf.getProp(Node.LOCAL_BLOCK_PROP));
        return close;
    }

    protected void visitNew(Node node, ScriptNode tree) {}

    protected void visitCall(Node node, ScriptNode tree) {}

    protected Node visitLet(boolean createScope, Node parent, Node previous, Node scopeNode) {
        Node vars = scopeNode.getFirstChild();
        Node body = vars.getNext();
        scopeNode.removeChild(vars);
        scopeNode.removeChild(body);
        boolean isExpression = scopeNode.getType() == Token.LETEXPR;
        Node result;
        Node newVars = new Node(Token.ENTER_SCOPE);
        if (createScope) {
            result = new Node(isExpression ? Token.SCOPEEXPR : Token.BLOCK);
            result = replaceCurrent(parent, previous, scopeNode, result);
            ArrayList<Object> list = new ArrayList<>();
            for (Node v = vars.getFirstChild(); v != null; v = v.getNext()) {
                Node current = v;
                if (current.getType() == Token.LETEXPR) {
                    // destructuring in let expr, e.g. let ([x, y] = [3, 4]) {}
                    List<?> destructuringNames =
                            (List<?>) current.getProp(Node.DESTRUCTURING_NAMES);
                    Node c = current.getFirstChild();
                    if (c.getType() != Token.LET) throw Kit.codeBug();
                    // Add initialization code to front of body
                    if (isExpression) {
                        body = new Node(Token.COMMA, c.getNext(), body);
                    } else {
                        body = new Node(Token.BLOCK, new Node(Token.EXPR_VOID, c.getNext()), body);
                    }
                    // Update "list" and "objectLiteral" for the variables
                    // defined in the destructuring assignment
                    if (destructuringNames != null) {
                        list.addAll(destructuringNames);
                        for (int i = 0; i < destructuringNames.size(); i++) {
                            newVars.addChildToBack(new Node(Token.VOID, Node.newNumber(0.0)));
                        }
                    }
                    // Process all NAME children of the inner LET node (not just the first)
                    // This includes the main temp variable ($0) and any computed property temps
                    // ($1, $2, etc.)
                    for (Node child = c.getFirstChild(); child != null; child = child.getNext()) {
                        if (child.getType() != Token.NAME) throw Kit.codeBug();
                        list.add(ScriptRuntime.getIndexObject(child.getString()));
                        Node init = child.getFirstChild();
                        if (init == null) {
                            init = new Node(Token.VOID, Node.newNumber(0.0));
                        }
                        newVars.addChildToBack(init);
                    }
                    continue; // Already processed all children, move to next sibling of LETEXPR
                }
                if (current.getType() != Token.NAME) throw Kit.codeBug();
                list.add(ScriptRuntime.getIndexObject(current.getString()));
                Node init = current.getFirstChild();
                if (init == null) {
                    init =
                            current.getIntProp(Node.LEXICAL_PROP, 0) == 1
                                    ? new Node(Token.TDZ_SENTINEL)
                                    : new Node(Token.VOID, Node.newNumber(0.0));
                }
                newVars.addChildToBack(init);
            }
            newVars.putProp(Node.OBJECT_IDS_PROP, list.toArray());
            result.addChildToBack(newVars);
            result.addChildToBack(new Node(Token.SCOPE_BLOCK, body));
            result.addChildToBack(new Node(Token.LEAVE_SCOPE));
        } else {
            result = new Node(isExpression ? Token.COMMA : Token.BLOCK);
            result = replaceCurrent(parent, previous, scopeNode, result);
            newVars = new Node(Token.COMMA);
            boolean destructuring = scopeNode.getProp(Node.DESTRUCTURING_NAMES) != null;
            for (Node v = vars.getFirstChild(); v != null; v = v.getNext()) {
                Node current = v;
                if (current.getType() == Token.LETEXPR) {
                    // destructuring in let expr, e.g. let ([x, y] = [3, 4]) {}
                    Node c = current.getFirstChild();
                    if (c.getType() != Token.LET) throw Kit.codeBug();
                    // Add initialization code to front of body
                    if (isExpression) {
                        body = new Node(Token.COMMA, c.getNext(), body);
                    } else {
                        body = new Node(Token.BLOCK, new Node(Token.EXPR_VOID, c.getNext()), body);
                    }
                    // We're removing the LETEXPR, so move the symbols
                    Scope.joinScopes((Scope) current, (Scope) scopeNode);
                    // Process all NAME children of the inner LET node (not just the first)
                    // This includes the main temp variable ($0) and any computed property temps
                    // ($1, $2, etc.)
                    for (Node child = c.getFirstChild(); child != null; child = child.getNext()) {
                        if (child.getType() != Token.NAME) throw Kit.codeBug();
                        Node stringNode = Node.newString(child.getString());
                        stringNode.setScope((Scope) scopeNode);
                        Node init = child.getFirstChild();
                        if (init == null) {
                            init = new Node(Token.VOID, Node.newNumber(0.0));
                        }
                        newVars.addChildToBack(new Node(Token.SETVAR, stringNode, init));
                    }
                    continue; // Already processed all children, move to next sibling of LETEXPR
                }
                if (current.getType() != Token.NAME) throw Kit.codeBug();
                Node init = current.getFirstChild();
                if (init == null && destructuring) {
                    // a temporary of the pattern, written before it is read
                    continue;
                }
                Node stringNode = Node.newString(current.getString());
                stringNode.setScope((Scope) scopeNode);
                if (init == null) {
                    init = new Node(Token.VOID, Node.newNumber(0.0));
                }
                // a const register is initialized, not assigned (e.g. the name of a class)
                Symbol symbol = ((Scope) scopeNode).getSymbol(current.getString());
                boolean isConst = symbol != null && symbol.getDeclType() == Symbol.Type.CONST;
                newVars.addChildToBack(
                        new Node(isConst ? Token.SETCONSTVAR : Token.SETVAR, stringNode, init));
            }
            if (isExpression) {
                result.addChildToBack(newVars);
                scopeNode.setType(Token.COMMA);
                result.addChildToBack(scopeNode);
                scopeNode.addChildToBack(body);
                if (body instanceof Scope && ((Scope) body).getParentScope() != scopeNode) {
                    Scope scopeParent = ((Scope) body).getParentScope();
                    ((Scope) body).setParentScope((Scope) scopeNode);
                    ((Scope) scopeNode).setParentScope(scopeParent);
                }
            } else {
                result.addChildToBack(new Node(Token.EXPR_VOID, newVars));
                scopeNode.setType(Token.BLOCK);
                result.addChildToBack(scopeNode);
                scopeNode.addChildrenToBack(body);
                if (body instanceof Scope && ((Scope) body).getParentScope() != scopeNode) {
                    Scope scopeParent = ((Scope) body).getParentScope();
                    ((Scope) body).setParentScope((Scope) scopeNode);
                    ((Scope) scopeNode).setParentScope(scopeParent);
                }
            }
        }
        return result;
    }

    private static Node addBeforeCurrent(Node parent, Node previous, Node current, Node toAdd) {
        if (previous == null) {
            if (!(current == parent.getFirstChild())) Kit.codeBug();
            parent.addChildToFront(toAdd);
        } else {
            if (!(current == previous.getNext())) Kit.codeBug();
            parent.addChildAfter(toAdd, previous);
        }
        return toAdd;
    }

    private static Node replaceCurrent(Node parent, Node previous, Node current, Node replacement) {
        if (previous == null) {
            if (!(current == parent.getFirstChild())) Kit.codeBug();
            parent.replaceChild(current, replacement);
        } else if (previous.next == current) {
            // Check cachedPrev.next == current is necessary due to possible
            // tree mutations
            parent.replaceChildAfter(previous, replacement);
        } else {
            parent.replaceChild(current, replacement);
        }
        return replacement;
    }

    /** Whether the temporal dead zone is checked: code compiled for VERSION_ECMASCRIPT. */
    private boolean tdz;

    /** Whether eval code is compiled: its top-level let/const are not global declarations. */
    private boolean inEval;

    /**
     * Whether a function with an activation object keeps the variables that no nested function
     * refers to in registers (VERSION_ECMASCRIPT: code evaluated in its scope at runtime, other
     * than direct eval, no longer sees them).
     */
    private boolean registersWithActivation;

    /** The names that the backends may not move out of the activation object (embedding API). */
    private Set<String> activationNames;

    /**
     * In a function with an activation object, the names of its parameters and variables that live
     * in registers (null: none).
     */
    private Set<String> registerNames;

    /**
     * In a function with an activation object, the let/const bindings of its blocks that live in
     * registers (null: none): they are variables of the function, still resolved through the scope
     * of their block (see #registerLexicals).
     */
    private Set<Symbol> registerLexicals;

    /** The switch scopes whose register bindings are already made uninitialized on entry. */
    private final Set<Scope> uninitializedSwitches =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** The block scopes left without a binding in a scope object: they need no runtime scope. */
    private final Set<Scope> unwrappedScopes = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Above this many variables in registers, a function with an activation object keeps them all
     * in it: a variable takes up to three JVM locals, of the 1024 that the compiled code may use.
     */
    private static final int MAX_REGISTER_VARS = 256;

    /**
     * In a function with an activation object, the names of its parameters and variables that
     * nested functions refer to, which it reads at a fixed position of the activation (null: none).
     */
    private Set<String> activationVarNames;

    /**
     * Keeps in registers the parameters and variables of a function with an activation object that
     * no nested function can refer to: only those need to be properties of the activation object.
     * The function itself reads the others at a fixed position of its activation. Not done when
     * names can be resolved dynamically (direct eval, with), for generators (their registers do not
     * survive a yield) and when the arguments object can alias the parameters (a non-strict
     * function with parameters: an unmapped arguments object only holds the values passed).
     * Function declarations, catch parameters, let/const and class bindings stay names of the
     * activation object.
     */
    private void keepVarsInRegisters(FunctionNode fn) {
        if (fn.isGenerator()
                || fn.isES6Generator()
                || (fn.requiresArgumentObject()
                        && ((!fn.isInStrictMode() && fn.getParamCount() != 0)
                                || fn.getSymbol("arguments") != null))) {
            return;
        }
        Set<String> referenced = new HashSet<>();
        Set<String> dynamic = new HashSet<>();
        Set<String> declared = new HashSet<>();
        if (!scanNames(fn, referenced, dynamic, declared, false)
                || !scanNestedFunctions(fn, referenced, dynamic, declared, true)) {
            return;
        }
        if (activationNames != null) {
            dynamic.addAll(activationNames);
        }
        Set<String> declarations = new HashSet<>();
        for (FunctionNode nested : fn.getFunctions()) {
            if (nested.getName() != null
                    && nested.getFunctionType() == FunctionNode.FUNCTION_STATEMENT) {
                declarations.add(nested.getName());
            }
        }
        // the let/const bindings that only the function refers to: variables of the function, the
        // bindings of its blocks added after its own
        Set<Symbol> lexicals = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRegisterLexicals(fn, fn, referenced, dynamic, lexicals);
        List<Symbol> blockLexicals = new ArrayList<>();
        for (Symbol symbol : lexicals) {
            if (symbol.getContainingTable() != fn) {
                blockLexicals.add(symbol);
            }
        }
        if (lexicals.size() > MAX_REGISTER_VARS) {
            lexicals.clear();
            blockLexicals.clear();
        }
        if (!blockLexicals.isEmpty()) {
            fn.addBlockVariables(blockLexicals);
        }
        List<Symbol> symbols = fn.getSymbols();
        boolean[] inRegister = new boolean[symbols.size()];
        boolean[] inSlot = new boolean[symbols.size()];
        Set<String> registers = new HashSet<>();
        Set<String> slots = new HashSet<>();
        for (Symbol symbol : symbols) {
            Symbol.Type type = symbol.getDeclType();
            String name = symbol.getName();
            if (lexicals.contains(symbol)) {
                inRegister[symbol.getIndex()] = true;
                continue;
            }
            if (type == Symbol.Type.FUNCTION_VAR || declarations.contains(name)) {
                // a function declaration, stored by name in the activation: never in a register,
                // at its position when nested functions refer to it
                if (referenced.contains(name)
                        && !dynamic.contains(name)
                        && !name.equals(fn.getName())) {
                    inSlot[symbol.getIndex()] = true;
                    slots.add(name);
                }
                continue;
            }
            if ((type != Symbol.Type.VAR && type != Symbol.Type.LP) || dynamic.contains(name)) {
                continue;
            }
            if (!referenced.contains(name)) {
                inRegister[symbol.getIndex()] = true;
                registers.add(name);
            } else if (!name.equals(fn.getName())) {
                // not the name of a function expression, which NativeCall binds too
                inSlot[symbol.getIndex()] = true;
                slots.add(name);
            }
        }
        if (registers.size() + lexicals.size() > MAX_REGISTER_VARS) {
            // the bindings of the blocks are variables of the function now: they stay registers
            for (Symbol symbol : symbols) {
                if (!lexicals.contains(symbol)) {
                    inRegister[symbol.getIndex()] = false;
                }
            }
            registers.clear();
        }
        if (!registers.isEmpty() || !lexicals.isEmpty()) {
            fn.setRegisterVars(inRegister);
            registerNames = registers.isEmpty() ? null : registers;
            registerLexicals = lexicals.isEmpty() ? null : lexicals;
        }
        if (!slots.isEmpty()) {
            fn.setActivationVars(inSlot);
            activationVarNames = slots;
            Map<String, Integer> outer = new HashMap<>();
            for (String name : slots) {
                int index = fn.getSymbol(name).getIndex();
                if (!declared.contains(name)
                        && !"arguments".equals(name)
                        && index <= ScriptRuntime.OUTER_VAR_INDEX) {
                    outer.put(name, index);
                }
            }
            if (!outer.isEmpty()) {
                addOuterVars(fn, outer, 1);
            }
        }
    }

    /**
     * Adds to lexicals the let/const bindings of the scopes in node (not entering nested functions)
     * that can live in registers of fn: no nested function refers to their name, and their temporal
     * dead zone is known from the syntax (a declaration of a single name, outside a switch, see
     * classifyTdz).
     */
    private static void collectRegisterLexicals(
            Node node,
            FunctionNode fn,
            Set<String> referenced,
            Set<String> dynamic,
            Set<Symbol> lexicals) {
        if (node instanceof Scope
                && !isSwitchScope((Scope) node)
                && ((Scope) node).getSymbolTable() != null) {
            boolean letScope = node.getType() == Token.LET || node.getType() == Token.LETEXPR;
            Set<Symbol> found = letScope ? new HashSet<>() : lexicals;
            for (Symbol symbol : ((Scope) node).getSymbolTable().values()) {
                String name = symbol.getName();
                if (isLexical(symbol)
                        && !referenced.contains(name)
                        && !dynamic.contains(name)
                        && !name.equals(fn.getName())
                        && isSingleNameDeclaration(symbol.getNode())) {
                    found.add(symbol);
                }
            }
            if (letScope && found.size() == ((Scope) node).getSymbolTable().size()) {
                // the head of a for loop or a let expression: initialized together, either as
                // variables or in a scope object (see visitLet)
                lexicals.addAll(found);
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectRegisterLexicals(child, fn, referenced, dynamic, lexicals);
        }
    }

    /**
     * Whether scope holds the let/const bindings of the clauses of a switch: IRFactory moves them
     * from the switch to a block around it (see IRFactory#transformSwitch).
     */
    private static boolean isSwitchScope(Scope scope) {
        if (scope instanceof SwitchStatement) {
            return true;
        }
        // the switch follows the statements that make its bindings uninitialized, if any
        for (Node child = scope.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof SwitchStatement) {
                return true;
            }
            if (child.getType() != Token.EXPR_VOID) {
                return false;
            }
        }
        return false;
    }

    /** The clause of a switch that a node of the syntax tree is in, or null. */
    private static SwitchCase switchClause(AstNode node) {
        for (AstNode n = node; n != null; n = n.getParent()) {
            if (n instanceof SwitchCase) {
                return (SwitchCase) n;
            }
            if (n instanceof SwitchStatement || n instanceof FunctionNode) {
                return null;
            }
        }
        return null;
    }

    /** Whether a reference to name in node is outside the clause of its declaration. */
    private static boolean hasOtherClauseReference(Node node, String name, SwitchCase clause) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Name
                    && name.equals(((Name) child).getIdentifier())
                    && switchClause((Name) child) != clause) {
                return true;
            }
            if (hasOtherClauseReference(child, name, clause)) {
                return true;
            }
        }
        return false;
    }

    /** Whether node is a scope whose bindings all live in registers (see #registerLexicals). */
    private boolean inRegisters(Node node) {
        return node instanceof Scope
                && registerLexicals != null
                && ((Scope) node).getSymbolTable() != null
                && registerLexicals.containsAll(((Scope) node).getSymbolTable().values());
    }

    /**
     * Whether the declaration of a binding is a let/const declaration of a single name (not a class
     * declaration, whose binding the class definition handles).
     */
    private static boolean isSingleNameDeclaration(Node node) {
        return node instanceof VariableInitializer
                && isInSyntaxTree((VariableInitializer) node)
                && ((VariableInitializer) node).getTarget() instanceof Name
                && !(((VariableInitializer) node).getInitializer() instanceof ClassNode);
    }

    /**
     * Lets the nested functions of fn, at any depth, read the variables of a function at a fixed
     * position of its activation: only those that no scope inside the function declares, which the
     * nested functions resolve to it whenever they do not resolve them themselves.
     */
    private static void addOuterVars(ScriptNode fn, Map<String, Integer> vars, int depth) {
        if (depth > 0x7F) {
            return;
        }
        for (FunctionNode nested : fn.getFunctions()) {
            for (Map.Entry<String, Integer> var : vars.entrySet()) {
                nested.addOuterVar(var.getKey(), (depth << 24) | var.getValue());
            }
            addOuterVars(nested, vars, depth + 1);
        }
    }

    /**
     * Adds to referenced the names referred to in the nested functions of fn, at any depth, to
     * dynamic their names and catch parameters, and to declared the names they declare; returns
     * false if one of them evaluates code or uses with.
     */
    private static boolean scanNestedFunctions(
            ScriptNode fn,
            Set<String> referenced,
            Set<String> dynamic,
            Set<String> declared,
            boolean direct) {
        for (FunctionNode nested : fn.getFunctions()) {
            if (nested.getName() != null
                    && (!direct || nested.getFunctionType() != FunctionNode.FUNCTION_STATEMENT)) {
                // the name of a function expression is bound inside it; a declaration of the
                // function itself is a binding of its own (see keepVarsInRegisters)
                dynamic.add(nested.getName());
            }
            if (nested.getSymbolTable() != null) {
                declared.addAll(nested.getSymbolTable().keySet());
            }
            Node destructuring = (Node) nested.getProp(Node.DESTRUCTURING_PARAMS);
            if (!scanNames(nested, referenced, dynamic, declared, true)
                    || !scanNames(
                            nested.getGeneratorParamInitBlock(),
                            referenced,
                            dynamic,
                            declared,
                            true)
                    || !scanNames(destructuring, referenced, dynamic, declared, true)
                    || !scanNestedFunctions(nested, referenced, dynamic, declared, false)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds the names referred to in the children of node (if collect), the catch parameters to
     * dynamic and the names that their scopes declare to declared, not entering nested functions;
     * returns false on a direct eval or a with statement.
     */
    private static boolean scanNames(
            Node node,
            Set<String> names,
            Set<String> dynamic,
            Set<String> declared,
            boolean collect) {
        if (node == null) {
            return true;
        }
        for (Node n = node.getFirstChild(); n != null; n = n.getNext()) {
            if (!scanNode(n, names, dynamic, declared, collect)) {
                return false;
            }
        }
        return true;
    }

    /** {@link #scanNames} for a node and its children. */
    private static boolean scanNode(
            Node n, Set<String> names, Set<String> dynamic, Set<String> declared, boolean collect) {
        int type = n.getType();
        if (type == Token.ENTERWITH
                || type == Token.DOTQUERY
                || n.getIntProp(Node.SPECIALCALL_PROP, Node.NON_SPECIALCALL)
                        != Node.NON_SPECIALCALL) {
            return false;
        }
        if (n instanceof Scope && ((Scope) n).getSymbolTable() != null) {
            declared.addAll(((Scope) n).getSymbolTable().keySet());
        }
        if (type == Token.CATCH_SCOPE) {
            // a catch parameter is bound at runtime, without a symbol: its name resolves
            // dynamically in the catch block
            if (n.getFirstChild() != null && n.getFirstChild().getType() == Token.NAME) {
                dynamic.add(n.getFirstChild().getString());
            }
        } else if (collect) {
            if (n instanceof Name) {
                names.add(((Name) n).getIdentifier());
            } else if (type == Token.NAME || type == Token.BINDNAME || type == Token.TYPEOFNAME) {
                names.add(n.getString());
            }
        }
        // computed property keys are kept out of the children
        Object ids = n.getProp(Node.OBJECT_IDS_PROP);
        if (ids instanceof Object[]) {
            for (Object id : (Object[]) ids) {
                if (id instanceof Node && !scanNode((Node) id, names, dynamic, declared, collect)) {
                    return false;
                }
            }
        }
        Object name = n.getProp(Node.NAME_PROP);
        if (name instanceof Node && !scanNode((Node) name, names, dynamic, declared, collect)) {
            return false;
        }
        return scanNames(n, names, dynamic, declared, collect);
    }

    /**
     * The top-level let/const of a script are global lexical declarations: they are not variables
     * of the script (properties of the global object), and are declared in the global environment's
     * declarative record when the script starts (ScriptRuntime#declareGlobalLexicals).
     */
    private static void declareGlobalLexicals(ScriptNode tree) {
        if (tree.getSymbolTable() == null) {
            return;
        }
        StringBuilder declarations = new StringBuilder();
        for (Symbol symbol : tree.getSymbolTable().values()) {
            if (isLexical(symbol)) {
                if (declarations.length() > 0) {
                    declarations.append(',');
                }
                declarations
                        .append(symbol.getDeclType() == Symbol.Type.CONST ? 'C' : 'L')
                        .append(symbol.getName());
            }
        }
        if (declarations.length() > 0) {
            tree.removeLexicalVariables();
            tree.putProp(Node.GLOBAL_LEXICALS_PROP, declarations.toString());
            Node declare = Node.newString(Token.GLOBAL_LEXICALS, declarations.toString());
            declare.setLineColumnNumber(Math.max(tree.getLineno(), 1), 1);
            tree.addChildToFront(declare);
        }
    }

    /** Block scopes already wrapped in a LET for their scope object. */
    private final Set<Scope> wrappedScopes = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final int TDZ_NONE = 0;
    private static final int TDZ_RUNTIME = 1;
    private static final int TDZ_THROW = 2;

    private static boolean isLexical(Symbol symbol) {
        return symbol.getDeclType() == Symbol.Type.LET || symbol.getDeclType() == Symbol.Type.CONST;
    }

    // Only bindings declared by a let/const statement (their symbol records the declaration) start
    // uninitialized: parameters, catch parameters and other desugared bindings are let bindings
    // too,
    // but are initialized before their scope's statements run.

    /**
     * A function's own let/const bindings live in its activation (a NativeCall) and start in their
     * temporal dead zone (storing the marker in a const leaves it to be initialized).
     */
    private void initFunctionLexicalsUninitialized(ScriptNode tree) {
        if (tree.getSymbolTable() == null) {
            return;
        }
        for (Symbol symbol : tree.getSymbolTable().values()) {
            // only bindings declared by a let statement of the body: destructured parameters are
            // also let bindings, but are initialized before the body runs; those in registers have
            // their temporal dead zone checked from the syntax
            if (isLexical(symbol)
                    && symbol.getNode() != null
                    && (registerLexicals == null || !registerLexicals.contains(symbol))) {
                boolean isConst = symbol.getDeclType() == Symbol.Type.CONST;
                Node set =
                        new Node(
                                isConst ? Token.SETCONST : Token.SETNAME,
                                Node.newString(Token.BINDNAME, symbol.getName()),
                                new Node(Token.TDZ_SENTINEL));
                set.putIntProp(Node.TDZ_DECLARATION_PROP, 1);
                // the code generators compile the last child, the function body
                tree.getLastChild().addChildToFront(new Node(Token.EXPR_VOID, set));
            }
        }
    }

    /**
     * Whether "let x;" must store undefined: for code compiled for VERSION_ECMASCRIPT (the binding
     * is uninitialized until then; the let bindings of eval code at its top level excepted).
     */
    private boolean initializesLet(
            ScriptNode tree, Scope scope, Node name, boolean createScopeObjects) {
        Scope defining = scope.getDefiningScope(name.getString());
        boolean scriptLevel = tree.getType() == Token.SCRIPT && defining == tree;
        if (tdz) {
            return !(scriptLevel && inEval);
        }
        // before VERSION_ECMASCRIPT, let x; is not evaluated
        return false;
    }

    /**
     * Classifies a reference to a let/const binding for its temporal dead zone (code compiled for
     * VERSION_ECMASCRIPT): TDZ_THROW when it is in the same function, textually before the end of
     * the declaration (each execution of it is in the dead zone); TDZ_RUNTIME when it can only be
     * known at runtime (from a closure, in a with statement, or across switch cases); TDZ_NONE
     * otherwise. Runtime reads are checked by the name lookups (ScriptRuntime.checkInitialized);
     * only runtime writes need an explicit check. Bindings in registers never hold the sentinel.
     */
    /** Whether name resolves, in the syntax tree, to a let, const or class binding. */
    private static boolean isLexicalName(String name, Scope scope) {
        if (scope == null) {
            return false;
        }
        Scope defining = scope.getDefiningScope(name);
        if (defining == null) {
            // beyond the function, through the syntax tree
            for (AstNode node = scope.getTop(); node != null && defining == null; ) {
                node = node.getParent();
                if (node instanceof Scope) {
                    defining = ((Scope) node).getDefiningScope(name);
                }
            }
        }
        if (defining == null) {
            return false;
        }
        Symbol symbol = defining.getSymbol(name);
        return symbol != null && isLexical(symbol);
    }

    private int classifyTdz(
            ScriptNode tree, Node reference, Scope scope, boolean createScopeObjects) {
        if (!(reference instanceof Name)) {
            return TDZ_NONE; // a name created by the compiler
        }
        String name = reference.getString();
        Scope defining = scope.getDefiningScope(name);
        if (defining == null) {
            // scope chains stop at the function: a binding of an enclosing function is found
            // through the syntax tree
            defining = enclosingDefiningScope((Name) reference, name);
            if (defining == null) {
                return TDZ_NONE;
            }
        }
        Symbol symbol = defining.getSymbol(name);
        if (symbol == null || !isLexical(symbol)) {
            return TDZ_NONE;
        }
        ScriptNode top = defining instanceof ScriptNode ? (ScriptNode) defining : defining.getTop();
        if (top.getType() == Token.SCRIPT && defining == top && inEval) {
            return TDZ_NONE; // eval code's own let/const are variables of the eval
        }
        boolean inWith = false;
        for (Node loop : loops) {
            inWith |= loop.getType() == Token.WITH;
        }
        if (top != tree || inWith) {
            return TDZ_RUNTIME;
        }
        if (!isInSyntaxTree((Name) reference)) {
            // a name created by a desugaring (the initialization of a destructuring declaration,
            // the loop variable of for-in/of, the read of a compound assignment): its writes
            // initialize, and its reads are checked by the name lookup anyway
            return TDZ_NONE;
        }
        if (!(symbol.getNode() instanceof AstNode) || !isInSyntaxTree((AstNode) symbol.getNode())) {
            // the declaration's position is unknown (a destructuring declaration)
            return createScopeObjects ? TDZ_RUNTIME : TDZ_NONE;
        }
        AstNode declaration = (AstNode) symbol.getNode();
        int end = declaration.getAbsolutePosition() + declaration.getLength();
        if (((Name) reference).getAbsolutePosition() < end) {
            return TDZ_THROW;
        }
        if (isSwitchScope(defining)
                && switchClause(declaration) != switchClause((Name) reference)) {
            // a clause can be entered past the declaration of an earlier clause
            return TDZ_RUNTIME;
        }
        return TDZ_NONE;
    }

    /** Whether the node is attached to its script's syntax tree, so its position is meaningful. */
    private static boolean isInSyntaxTree(AstNode node) {
        AstNode n = node;
        while (n.getParent() != null) {
            n = n.getParent();
        }
        return n instanceof ScriptNode && node.getPosition() >= 0;
    }

    private static Scope enclosingDefiningScope(Name reference, String name) {
        for (AstNode node = reference.getParent(); node != null; node = node.getParent()) {
            if (node instanceof Scope) {
                Scope found = ((Scope) node).getDefiningScope(name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Whether a reference to a let/const binding reads a register, which does not check its
     * temporal dead zone: a binding of a function without scope objects.
     */
    private static boolean inRegister(Node reference, Scope scope, boolean createScopeObjects) {
        return !createScopeObjects && scope.getDefiningScope(reference.getString()) != null;
    }

    /** A read of name that throws the ReferenceError while it is uninitialized. */
    private static Node checkedRead(String name) {
        Node read = Node.newString(Token.NAME, name);
        read.putIntProp(Node.TDZ_CHECKED_PROP, 1);
        Node check = Node.newString(Token.CHECK_TDZ, name);
        check.addChildToBack(read);
        return check;
    }

    /**
     * An expression that throws the ReferenceError for name, see ScriptRuntime#checkInitialized.
     */
    private static Node tdzError(String name) {
        Node check = Node.newString(Token.CHECK_TDZ, name);
        check.addChildToBack(new Node(Token.TDZ_SENTINEL));
        return check;
    }

    /**
     * Applies the temporal dead zone rules to a name reference; returns its replacement, if any.
     */
    private Node checkTemporalDeadZone(
            ScriptNode tree,
            Node parent,
            Node previous,
            Node node,
            Scope scope,
            boolean createScopeObjects) {
        switch (node.getType()) {
            case Token.NAME:
            case Token.TYPEOFNAME:
                {
                    // a read: a runtime check is done by the name lookup itself, but not by a
                    // register
                    int kind = classifyTdz(tree, node, scope, createScopeObjects);
                    if (kind == TDZ_THROW) {
                        return replaceCurrent(parent, previous, node, tdzError(node.getString()));
                    }
                    if (kind == TDZ_RUNTIME && inRegister(node, scope, createScopeObjects)) {
                        Node check = checkedRead(node.getString());
                        if (node.getType() == Token.TYPEOFNAME) {
                            check = new Node(Token.TYPEOF, check);
                        }
                        return replaceCurrent(parent, previous, node, check);
                    }
                    return node;
                }
            case Token.INC:
            case Token.DEC:
                {
                    Node target = node.getFirstChild();
                    if (target.getType() != Token.NAME) {
                        return node;
                    }
                    int kind = classifyTdz(tree, target, scope, createScopeObjects);
                    if (kind == TDZ_THROW) {
                        return replaceCurrent(parent, previous, node, tdzError(target.getString()));
                    }
                    target.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                    if (kind == TDZ_RUNTIME && inRegister(target, scope, createScopeObjects)) {
                        // the binding is checked, then incremented
                        node.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                        Node comma = new Node(Token.COMMA, checkedRead(target.getString()));
                        replaceCurrent(parent, previous, node, comma);
                        comma.addChildToBack(node);
                        return comma;
                    }
                    return node;
                }
            case Token.SETNAME:
            case Token.STRICT_SETNAME:
                {
                    if (node.getIntProp(Node.TDZ_DECLARATION_PROP, 0) == 1) {
                        return node;
                    }
                    Node target = node.getFirstChild();
                    int kind = classifyTdz(tree, target, scope, createScopeObjects);
                    if (kind == TDZ_NONE
                            && node.getIntProp(Node.DESTRUCTURING_TARGET_PROP, 0) == 1
                            && isLexicalName(target.getString(), scope)) {
                        // a destructuring assignment target: checked when it is stored
                        kind = TDZ_RUNTIME;
                    }
                    if (kind == TDZ_THROW) {
                        // the value is evaluated, then storing it throws
                        Node value = target.getNext();
                        node.removeChild(value);
                        return replaceCurrent(
                                parent,
                                previous,
                                node,
                                new Node(Token.COMMA, value, tdzError(target.getString())));
                    }
                    if (kind == TDZ_RUNTIME) {
                        // PutValue throws once the value is evaluated: the binding is checked
                        // between the evaluation of the value and the store
                        Node read = Node.newString(Token.NAME, target.getString());
                        read.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                        node.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                        Node check = Node.newString(Token.CHECK_TDZ, target.getString());
                        check.addChildToBack(read);
                        Node value = target.getNext();
                        node.removeChild(value);
                        node.addChildToBack(new Node(Token.TDZ_AFTER, value, check));
                        return node;
                    }
                    return node;
                }
            default:
                return node;
        }
    }

    private Deque<Node> loops;
    private Deque<Node> loopEnds;
    private boolean hasFinally;
}
