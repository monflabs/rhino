/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.Set;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.ClassNode;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.SwitchCase;
import org.monflabs.mozilla.javascript.ast.SwitchStatement;
import org.monflabs.mozilla.javascript.ast.Symbol;
import org.monflabs.mozilla.javascript.ast.VariableInitializer;

/**
 * The temporal dead zone of let, const and class bindings, for {@link NodeTransformer}: which
 * references can be in it, and the checks added to the tree (code compiled for VERSION_ECMASCRIPT).
 * Only bindings declared by a let/const statement (their symbol records the declaration) start
 * uninitialized: parameters, catch parameters and other desugared bindings are let bindings too,
 * but are initialized before the statements of their scope run.
 */
final class TemporalDeadZone {
    private static final int NONE = 0;
    private static final int RUNTIME = 1;
    private static final int THROW = 2;

    /** Whether eval code is compiled: its top-level let/const are variables of the eval. */
    private final boolean inEval;

    TemporalDeadZone(boolean inEval) {
        this.inEval = inEval;
    }

    /**
     * Makes the bindings of the clauses of a switch uninitialized whenever the switch is entered,
     * when they live in registers: for the clauses entered past their declarations (see #classify).
     */
    static void initSwitchLexicalsUninitialized(Scope scope) {
        for (Symbol symbol : scope.getSymbolTable().values()) {
            if (isLexical(symbol)
                    && isDeclaration(symbol.getNode())
                    && hasOtherClauseReference(
                            scope, symbol.getName(), switchClause((AstNode) symbol.getNode()))) {
                Node set =
                        new Node(
                                symbol.getDeclType() == Symbol.Type.CONST
                                        ? Token.SETCONST
                                        : Token.SETNAME,
                                Node.newString(Token.BINDNAME, symbol.getName()),
                                new Node(Token.TDZ_SENTINEL));
                set.putIntProp(Node.TDZ_DECLARATION_PROP, 1);
                scope.addChildToFront(new Node(Token.EXPR_VOID, set));
            }
        }
    }

    static boolean isLexical(Symbol symbol) {
        return symbol.getDeclType() == Symbol.Type.LET || symbol.getDeclType() == Symbol.Type.CONST;
    }

    /**
     * A function's own let/const bindings live in its activation (a NativeCall) and start in their
     * temporal dead zone (storing the marker in a const leaves it to be initialized).
     */
    static void initFunctionLexicalsUninitialized(ScriptNode tree, Set<Symbol> registerLexicals) {
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

    /** Whether name resolves, in the syntax tree, to a let, const or class binding. */
    static boolean isLexicalName(String name, Scope scope) {
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

    /**
     * Classifies a reference to a let/const binding for its temporal dead zone (code compiled for
     * VERSION_ECMASCRIPT): THROW when it is in the same function, textually before the end of the
     * declaration (each execution of it is in the dead zone); RUNTIME when it can only be known at
     * runtime (from a closure, in a with statement, or across switch cases); NONE otherwise.
     * Runtime reads are checked by the name lookups (ScriptRuntime.checkInitialized); only runtime
     * writes need an explicit check. Bindings in registers never hold the sentinel.
     */
    private int classify(
            ScriptNode tree,
            Node reference,
            Scope scope,
            boolean createScopeObjects,
            boolean inWith) {
        if (!(reference instanceof Name)) {
            return NONE; // a name created by the compiler
        }
        String name = reference.getString();
        Scope defining = scope.getDefiningScope(name);
        if (defining == null) {
            // scope chains stop at the function: a binding of an enclosing function is found
            // through the syntax tree
            defining = NodeTransformer.enclosingDefiningScope((Name) reference, name);
            if (defining == null) {
                return NONE;
            }
        }
        Symbol symbol = defining.getSymbol(name);
        if (symbol == null || !isLexical(symbol)) {
            return NONE;
        }
        ScriptNode top = defining instanceof ScriptNode ? (ScriptNode) defining : defining.getTop();
        if (top.getType() == Token.SCRIPT && defining == top && inEval) {
            return NONE; // eval code's own let/const are variables of the eval
        }
        if (top != tree || inWith) {
            return RUNTIME;
        }
        if (!isInSyntaxTree((Name) reference)) {
            // a name created by a desugaring (the initialization of a destructuring declaration,
            // the loop variable of for-in/of, the read of a compound assignment): its writes
            // initialize, and its reads are checked by the name lookup anyway
            return NONE;
        }
        if (!(symbol.getNode() instanceof AstNode) || !isInSyntaxTree((AstNode) symbol.getNode())) {
            // the declaration's position is unknown (a destructuring declaration)
            return createScopeObjects ? RUNTIME : NONE;
        }
        AstNode declaration = (AstNode) symbol.getNode();
        int end = declaration.getAbsolutePosition() + declaration.getLength();
        if (((Name) reference).getAbsolutePosition() < end) {
            return THROW;
        }
        if (isSwitchScope(defining)
                && switchClause(declaration) != switchClause((Name) reference)) {
            // a clause can be entered past the declaration of an earlier clause
            return RUNTIME;
        }
        return NONE;
    }

    /** Whether the node is attached to its script's syntax tree, so its position is meaningful. */
    static boolean isInSyntaxTree(AstNode node) {
        AstNode n = node;
        while (n.getParent() != null) {
            n = n.getParent();
        }
        return n instanceof ScriptNode && node.getPosition() >= 0;
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
    Node check(
            ScriptNode tree,
            Node parent,
            Node previous,
            Node node,
            Scope scope,
            boolean createScopeObjects,
            boolean inWith) {
        switch (node.getType()) {
            case Token.NAME:
            case Token.TYPEOFNAME:
                {
                    // a read: a runtime check is done by the name lookup itself, but not by a
                    // register
                    int kind = classify(tree, node, scope, createScopeObjects, inWith);
                    if (kind == THROW) {
                        return NodeTransformer.replaceCurrent(
                                parent, previous, node, tdzError(node.getString()));
                    }
                    if (kind == RUNTIME && inRegister(node, scope, createScopeObjects)) {
                        Node check = checkedRead(node.getString());
                        if (node.getType() == Token.TYPEOFNAME) {
                            check = new Node(Token.TYPEOF, check);
                        }
                        return NodeTransformer.replaceCurrent(parent, previous, node, check);
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
                    int kind = classify(tree, target, scope, createScopeObjects, inWith);
                    if (kind == THROW) {
                        return NodeTransformer.replaceCurrent(
                                parent, previous, node, tdzError(target.getString()));
                    }
                    target.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                    if (kind == RUNTIME && inRegister(target, scope, createScopeObjects)) {
                        // the binding is checked, then incremented
                        node.putIntProp(Node.TDZ_CHECKED_PROP, 1);
                        Node comma = new Node(Token.COMMA, checkedRead(target.getString()));
                        NodeTransformer.replaceCurrent(parent, previous, node, comma);
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
                    int kind = classify(tree, target, scope, createScopeObjects, inWith);
                    if (kind == NONE
                            && node.getIntProp(Node.DESTRUCTURING_TARGET_PROP, 0) == 1
                            && isLexicalName(target.getString(), scope)) {
                        // a destructuring assignment target: checked when it is stored
                        kind = RUNTIME;
                    }
                    if (kind == THROW) {
                        // the value is evaluated, then storing it throws
                        Node value = target.getNext();
                        node.removeChild(value);
                        return NodeTransformer.replaceCurrent(
                                parent,
                                previous,
                                node,
                                new Node(Token.COMMA, value, tdzError(target.getString())));
                    }
                    if (kind == RUNTIME) {
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

    /**
     * Whether scope holds the let/const bindings of the clauses of a switch: IRFactory moves them
     * from the switch to a block around it (see IRFactory#transformSwitch).
     */
    static boolean isSwitchScope(Scope scope) {
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
    static SwitchCase switchClause(AstNode node) {
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
    static boolean hasOtherClauseReference(Node node, String name, SwitchCase clause) {
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

    /**
     * Whether the declaration of a binding is a let/const declaration, of a name or a pattern (not
     * a class declaration, whose binding the class definition handles).
     */
    static boolean isDeclaration(Node node) {
        return node instanceof VariableInitializer
                && isInSyntaxTree((VariableInitializer) node)
                && !(((VariableInitializer) node).getInitializer() instanceof ClassNode);
    }

    /**
     * Whether the declaration of a binding is a let/const declaration of a single name (not a class
     * declaration, whose binding the class definition handles).
     */
    static boolean isSingleNameDeclaration(Node node) {
        return node instanceof VariableInitializer
                && isInSyntaxTree((VariableInitializer) node)
                && ((VariableInitializer) node).getTarget() instanceof Name
                && !(((VariableInitializer) node).getInitializer() instanceof ClassNode);
    }
}
