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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.Jump;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.SwitchStatement;
import org.monflabs.mozilla.javascript.ast.Symbol;

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
            if (createScopeObjects
                    && (type == Token.BLOCK || type == Token.LOOP || type == Token.ARRAYCOMP)
                    && (node instanceof Scope)) {
                Scope newScope = (Scope) node;
                if (newScope.getSymbolTable() != null && wrappedScopes.add(newScope)) {
                    // transform to let statement so we get a with statement
                    // created to contain scoped let variables
                    Node let = new Node(type == Token.ARRAYCOMP ? Token.LETEXPR : Token.LET);
                    Node innerLet = new Node(Token.LET);
                    let.addChildToBack(innerLet);
                    for (Symbol symbol : newScope.getSymbolTable().values()) {
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
                    if (!createScopeObjects) {
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
                                    unwind = enumClose(n);
                                } else if (elemtype == Token.TRY) {
                                    Jump jsrnode = new Jump(Token.JSR);
                                    jsrnode.target = ((Jump) n).getFinally();
                                    unwind = jsrnode;
                                } else {
                                    unwind = new Node(Token.LEAVE_SCOPE);
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
                                            addBeforeCurrent(parent, previous, node, enumClose(n));
                                }
                                break;
                            }

                            int elemtype = n.getType();
                            if (isForOf(n)) {
                                previous = addBeforeCurrent(parent, previous, node, enumClose(n));
                            } else if (elemtype == Token.WITH
                                    || elemtype == Token.SCOPEEXPR
                                    || elemtype == Token.SCOPE_BLOCK) {
                                Node leave = new Node(Token.LEAVE_SCOPE);
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
                        // Turn name to var for faster access if possible
                        if (createScopeObjects) {
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

    private static Node enumClose(Node forOf) {
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
                Node stringNode = Node.newString(current.getString());
                stringNode.setScope((Scope) scopeNode);
                Node init = current.getFirstChild();
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
            // also let bindings, but are initialized before the body runs
            if (isLexical(symbol) && symbol.getNode() != null) {
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
     * Whether "let x;" must store undefined: always for code compiled for VERSION_ECMASCRIPT (the
     * binding is uninitialized until then), and for bindings in registers, which keep the value of
     * a previous loop iteration. A script's own let bindings (global code) are left alone.
     */
    private boolean initializesLet(
            ScriptNode tree, Scope scope, Node name, boolean createScopeObjects) {
        Scope defining = scope.getDefiningScope(name.getString());
        boolean scriptLevel = tree.getType() == Token.SCRIPT && defining == tree;
        if (tdz) {
            return !(scriptLevel && inEval);
        }
        return !createScopeObjects && !scriptLevel;
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
        if (defining instanceof SwitchStatement) {
            return createScopeObjects ? TDZ_RUNTIME : TDZ_NONE;
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
                // a read: a runtime check is done by the name lookup itself
                if (classifyTdz(tree, node, scope, createScopeObjects) == TDZ_THROW) {
                    return replaceCurrent(parent, previous, node, tdzError(node.getString()));
                }
                return node;
            case Token.INC:
            case Token.DEC:
                {
                    Node target = node.getFirstChild();
                    if (target.getType() == Token.NAME
                            && classifyTdz(tree, target, scope, createScopeObjects) == TDZ_THROW) {
                        return replaceCurrent(parent, previous, node, tdzError(target.getString()));
                    }
                    target.putIntProp(Node.TDZ_CHECKED_PROP, 1);
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
                        Node read = Node.newString(Token.NAME, target.getString());
                        read.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                        node.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                        Node check = Node.newString(Token.CHECK_TDZ, target.getString());
                        check.addChildToBack(read);
                        Node comma = new Node(Token.COMMA);
                        Node result = replaceCurrent(parent, previous, node, comma);
                        comma.addChildToBack(check);
                        comma.addChildToBack(node);
                        return result;
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
