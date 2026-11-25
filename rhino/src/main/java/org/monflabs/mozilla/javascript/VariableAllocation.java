/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.Symbol;

/**
 * Where the variables of a function with an activation object live, for {@link NodeTransformer}: in
 * registers, at a fixed position of the activation, or by name in it. An optimization, with no
 * effect on the semantics of the function.
 */
final class VariableAllocation {
    /**
     * Above this many variables in registers, a function with an activation object keeps them all
     * in it: a variable takes up to three JVM locals, of the 1024 that the compiled code may use.
     */
    private static final int MAX_REGISTER_VARS = 256;

    /** The names of the parameters and variables that live in registers (null: none). */
    Set<String> registerNames;

    /**
     * The let/const bindings of the blocks that live in registers (null: none): they are variables
     * of the function, still resolved through the scope of their block.
     */
    Set<Symbol> registerLexicals;

    /**
     * The names of the parameters and variables that nested functions refer to, which the function
     * reads at a fixed position of the activation (null: none).
     */
    Set<String> activationVarNames;

    private VariableAllocation() {}

    /**
     * Keeps in registers the parameters and variables of a function with an activation object that
     * no nested function can refer to: only those need to be properties of the activation object.
     * The function itself reads the others at a fixed position of its activation. Not done when
     * names can be resolved dynamically (direct eval, with), and when the arguments object can
     * alias the parameters (a non-strict function with parameters: an unmapped arguments object
     * only holds the values passed). A generator keeps no register (they do not survive a yield)
     * and reads its own variables by name, but its nested functions read them at their position.
     * Function declarations, catch parameters, let/const and class bindings stay names of the
     * activation object.
     */
    static VariableAllocation of(FunctionNode fn, Set<String> activationNames) {
        VariableAllocation allocation = new VariableAllocation();
        allocation.allocate(fn, activationNames);
        return allocation;
    }

    private void allocate(FunctionNode fn, Set<String> activationNames) {
        boolean generator = fn.isGenerator() || fn.isES6Generator();
        if ((fn.requiresArgumentObject()
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
        if (!generator) {
            collectRegisterLexicals(fn, fn, referenced, dynamic, lexicals);
        }
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
                if (!generator) {
                    inRegister[symbol.getIndex()] = true;
                    registers.add(name);
                }
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
            if (!generator) {
                activationVarNames = slots;
            }
            Map<String, Integer> outer = new HashMap<>();
            for (String name : slots) {
                int index = fn.getSymbol(name).getIndex();
                if (!declared.contains(name)
                        && !"arguments".equals(name)
                        && index <= FunctionNode.MAX_OUTER_VAR_INDEX) {
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
                && !TemporalDeadZone.isSwitchScope((Scope) node)
                && ((Scope) node).getSymbolTable() != null) {
            boolean letScope = node.getType() == Token.LET || node.getType() == Token.LETEXPR;
            Set<Symbol> found = letScope ? new HashSet<>() : lexicals;
            for (Symbol symbol : ((Scope) node).getSymbolTable().values()) {
                String name = symbol.getName();
                if (TemporalDeadZone.isLexical(symbol)
                        && !referenced.contains(name)
                        && !dynamic.contains(name)
                        && !name.equals(fn.getName())
                        && TemporalDeadZone.isSingleNameDeclaration(symbol.getNode())) {
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
     * Lets the nested functions of fn, at any depth, read the variables of a function at a fixed
     * position of its activation: only those that no scope inside the function declares, which the
     * nested functions resolve to it whenever they do not resolve them themselves.
     */
    private static void addOuterVars(ScriptNode fn, Map<String, Integer> vars, int depth) {
        if (depth > FunctionNode.MAX_OUTER_VAR_DEPTH) {
            return;
        }
        for (FunctionNode nested : fn.getFunctions()) {
            nested.addOuterVars(vars, depth);
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
     * Marks a NAME or SETNAME of a variable that an enclosing function reads at a fixed position of
     * its activation (Node#OUTER_VAR_PROP); returns whether it did.
     */
    static boolean markOuterVar(ScriptNode tree, Node node, Scope scope) {
        if (!(tree instanceof FunctionNode) || !((FunctionNode) tree).hasOuterVars()) {
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
        if (nameSource.getScope() != null || scope.getDefiningScope(name) != null) {
            return false;
        }
        Integer var = ((FunctionNode) tree).getOuterVar(name);
        if (var == null) {
            return false;
        }
        node.putIntProp(Node.OUTER_VAR_PROP, var);
        return true;
    }
}
