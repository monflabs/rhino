/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import org.monflabs.mozilla.javascript.CompilerEnvirons;
import org.monflabs.mozilla.javascript.Node;
import org.monflabs.mozilla.javascript.Token;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.ScriptNode;

class Optimizer {

    static final int NoType = 0;
    static final int NumberType = 1;
    static final int AnyType = 3;

    // It is assumed that (NumberType | AnyType) == AnyType

    Optimizer(CompilerEnvirons compilerEnv) {
        this.compilerEnv = compilerEnv;
    }

    void optimize(ScriptNode tree) {
        Map<OptFunctionNode, Boolean> functions = new LinkedHashMap<>();
        collectFunctions_r(tree, functions);
        Map<OptFunctionNode, List<Node>> calls = new HashMap<>();
        Map<Node, OptFunctionNode> callers = new HashMap<>();
        collectNumberArgumentCandidates_r(tree, calls, callers);

        // a function whose arguments may be numbers is analyzed after the functions that call it
        Set<OptFunctionNode> done = new HashSet<>();
        for (OptFunctionNode f : functions.keySet()) {
            if (!calls.containsKey(f)) {
                analyzeFunction(f, functions.get(f));
                done.add(f);
            }
        }
        for (boolean progress = true; progress; ) {
            progress = false;
            for (Map.Entry<OptFunctionNode, List<Node>> e : calls.entrySet()) {
                OptFunctionNode f = e.getKey();
                if (done.contains(f)) {
                    continue;
                }
                boolean ready = true;
                for (Node call : e.getValue()) {
                    ready &= done.contains(callers.get(call));
                }
                if (ready) {
                    f.setNumberArguments(numberArguments(f, e.getValue(), callers));
                    analyzeFunction(f, functions.get(f));
                    done.add(f);
                    progress = true;
                }
            }
        }
        Map<OptFunctionNode, List<Node>> returnValues = returnValues(calls);
        // calls from each other (recursion), and the functions they call
        Map<OptFunctionNode, boolean[]> pending = new HashMap<>();
        for (OptFunctionNode f : calls.keySet()) {
            if (!done.contains(f)) {
                boolean[] numbers = new boolean[f.fnode.getParamCount()];
                Arrays.fill(numbers, true);
                pending.put(f, numbers);
            }
        }
        recursiveNumberArguments(pending, calls, callers, returnValues);
        for (Map.Entry<OptFunctionNode, boolean[]> e : pending.entrySet()) {
            e.getKey().setNumberArguments(e.getValue());
            analyzeFunction(e.getKey(), functions.get(e.getKey()));
        }

        numberReturns(returnValues);
        for (Map.Entry<OptFunctionNode, Boolean> e : functions.entrySet()) {
            rewriteFunction(e.getKey(), e.getValue());
        }
    }

    /**
     * The number parameters of functions that call each other: assumed to be numbers, until a call
     * may pass something else. A call from one of them passes a number when it computes it from
     * numbers, before the caller is analyzed: the number parameters it never assigns, and the calls
     * of the functions returning numbers, which are found along (see #numberReturns).
     */
    private static void recursiveNumberArguments(
            Map<OptFunctionNode, boolean[]> pending,
            Map<OptFunctionNode, List<Node>> calls,
            Map<Node, OptFunctionNode> callers,
            Map<OptFunctionNode, List<Node>> returnValues) {
        if (pending.isEmpty()) {
            return;
        }
        Map<OptFunctionNode, IntPredicate> numberVars = new HashMap<>();
        for (Map.Entry<OptFunctionNode, boolean[]> e : pending.entrySet()) {
            OptFunctionNode f = e.getKey();
            boolean[] numbers = e.getValue();
            BitSet assigned = new BitSet();
            collectAssignedVars_r(f, f.fnode, assigned);
            numberVars.put(f, i -> i < numbers.length && numbers[i] && !assigned.get(i));
        }
        Map<OptFunctionNode, Boolean> numberReturns = new HashMap<>();
        for (OptFunctionNode f : returnValues.keySet()) {
            numberReturns.put(f, Boolean.TRUE);
        }
        for (boolean changed = true; changed; ) {
            changed = false;
            for (Map.Entry<OptFunctionNode, boolean[]> e : pending.entrySet()) {
                boolean[] numbers = e.getValue();
                for (Node call : calls.get(e.getKey())) {
                    OptFunctionNode caller = callers.get(call);
                    int i = 0;
                    for (Node arg = call.getFirstChild().getNext();
                            arg != null;
                            arg = arg.getNext(), i++) {
                        if (numbers[i]
                                && (caller == null
                                        || !isNumberExpression(
                                                arg,
                                                caller,
                                                numberVars.getOrDefault(
                                                        caller, caller::isNumberVar),
                                                numberReturns))) {
                            numbers[i] = false;
                            changed = true;
                        }
                    }
                }
            }
            for (Map.Entry<OptFunctionNode, List<Node>> e : returnValues.entrySet()) {
                OptFunctionNode f = e.getKey();
                if (numberReturns.get(f)
                        && !allNumbers(
                                e.getValue(),
                                f,
                                numberVars.getOrDefault(f, f::isNumberVar),
                                numberReturns)) {
                    numberReturns.put(f, Boolean.FALSE);
                    changed = true;
                }
            }
        }
    }

    private static boolean allNumbers(
            List<Node> values,
            OptFunctionNode f,
            IntPredicate numberVar,
            Map<OptFunctionNode, Boolean> numberReturns) {
        for (Node value : values) {
            if (!isNumberExpression(value, f, numberVar, numberReturns)) {
                return false;
            }
        }
        return true;
    }

    private static void collectAssignedVars_r(OptFunctionNode f, Node node, BitSet vars) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            int type = child.getType();
            if (type == Token.SETVAR || type == Token.SETCONSTVAR) {
                vars.set(f.getVarIndex(child));
            } else if ((type == Token.INC || type == Token.DEC)
                    && child.getFirstChild().getType() == Token.GETVAR) {
                vars.set(f.getVarIndex(child.getFirstChild()));
            }
            if (!(child instanceof ScriptNode)) {
                collectAssignedVars_r(f, child, vars);
            }
        }
    }

    /**
     * The functions that only their direct calls can call, with the values of their returns, if
     * they always return one.
     */
    private static Map<OptFunctionNode, List<Node>> returnValues(
            Map<OptFunctionNode, List<Node>> calls) {
        Map<OptFunctionNode, List<Node>> returns = new HashMap<>();
        for (Map.Entry<OptFunctionNode, List<Node>> e : calls.entrySet()) {
            OptFunctionNode f = e.getKey();
            boolean onlyCalls = !f.fnode.isGenerator() && !f.fnode.isAsync();
            for (Node call : e.getValue()) {
                onlyCalls &= call.getType() == Token.CALL;
            }
            List<Node> values = new ArrayList<>();
            if (onlyCalls && collectReturnValues_r(f.fnode, values)) {
                returns.put(f, values);
            }
        }
        return returns;
    }

    /**
     * Marks the functions that always return a number: each return has a value that is a number,
     * the calls of these functions being assumed to return numbers until one may not. This includes
     * the ones assumed by #recursiveNumberArguments, which knew less about the variables.
     */
    private static void numberReturns(Map<OptFunctionNode, List<Node>> returnValues) {
        Map<OptFunctionNode, Boolean> numberReturns = new HashMap<>();
        for (OptFunctionNode f : returnValues.keySet()) {
            numberReturns.put(f, Boolean.TRUE);
        }
        for (boolean changed = true; changed; ) {
            changed = false;
            for (Map.Entry<OptFunctionNode, List<Node>> e : returnValues.entrySet()) {
                OptFunctionNode f = e.getKey();
                if (numberReturns.get(f)
                        && !allNumbers(e.getValue(), f, f::isNumberVar, numberReturns)) {
                    numberReturns.put(f, Boolean.FALSE);
                    changed = true;
                }
            }
        }
        for (Map.Entry<OptFunctionNode, Boolean> e : numberReturns.entrySet()) {
            if (e.getValue()) {
                e.getKey().setReturnsNumber();
            }
        }
    }

    /**
     * Collects the values of the returns of a function; false if one has none, or the function
     * returns otherwise (a return in a try, a yield).
     */
    private static boolean collectReturnValues_r(Node node, List<Node> values) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            int type = child.getType();
            if (type == Token.TRY
                    || type == Token.RETURN_RESULT
                    || type == Token.YIELD
                    || type == Token.YIELD_STAR
                    || (type == Token.RETURN && child.getFirstChild() == null)) {
                return false;
            }
            if (type == Token.RETURN) {
                values.add(child.getFirstChild());
            }
            if (!(child instanceof ScriptNode) && !collectReturnValues_r(child, values)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an expression of a function always evaluates to a number (or throws): a number
     * literal, a number variable, an arithmetic operation that cannot give a BigInt (on a number,
     * since a BigInt and a number throw a TypeError), or a direct call of a function returning a
     * number.
     */
    private static boolean isNumberExpression(
            Node n,
            OptFunctionNode f,
            IntPredicate numberVar,
            Map<OptFunctionNode, Boolean> numberReturns) {
        switch (n.getType()) {
            case Token.NUMBER:
            case Token.POS:
            case Token.URSH:
                return true;
            case Token.GETVAR:
                return numberVar.test(f.getVarIndex(n));
            case Token.INC:
            case Token.DEC:
            case Token.NEG:
            case Token.BITNOT:
                {
                    Node child = n.getFirstChild();
                    return (n.getType() == Token.NEG
                                    || n.getType() == Token.BITNOT
                                    || child.getType() == Token.GETVAR)
                            && isNumberExpression(child, f, numberVar, numberReturns);
                }
            case Token.SUB:
            case Token.MUL:
            case Token.DIV:
            case Token.MOD:
            case Token.EXP:
            case Token.BITAND:
            case Token.BITOR:
            case Token.BITXOR:
            case Token.LSH:
            case Token.RSH:
                return isNumberExpression(n.getFirstChild(), f, numberVar, numberReturns)
                        || isNumberExpression(n.getLastChild(), f, numberVar, numberReturns);
            case Token.ADD:
                return isNumberExpression(n.getFirstChild(), f, numberVar, numberReturns)
                        && isNumberExpression(n.getLastChild(), f, numberVar, numberReturns);
            case Token.HOOK:
                {
                    Node ifTrue = n.getFirstChild().getNext();
                    return isNumberExpression(ifTrue, f, numberVar, numberReturns)
                            && isNumberExpression(ifTrue.getNext(), f, numberVar, numberReturns);
                }
            case Token.COMMA:
            case Token.SETVAR:
            case Token.SETCONSTVAR:
                return isNumberExpression(n.getLastChild(), f, numberVar, numberReturns);
            case Token.CALL:
                {
                    Object target = n.getProp(Node.DIRECTCALL_PROP);
                    return target != null
                            && numberReturns != null
                            && numberReturns.getOrDefault(target, Boolean.FALSE);
                }
            default:
                return false;
        }
    }

    /** The functions of the tree, and whether each is nested in another function. */
    private static void collectFunctions_r(
            ScriptNode scriptOrFn, Map<OptFunctionNode, Boolean> functions) {
        for (int i = 0, n = scriptOrFn.getFunctionCount(); i != n; ++i) {
            OptFunctionNode f = OptFunctionNode.get(scriptOrFn, i);
            functions.put(f, scriptOrFn.getType() == Token.FUNCTION);
            collectFunctions_r(f.fnode, functions);
        }
    }

    /**
     * The function declarations nested in a function that only its direct calls can call, with
     * those calls: the name is used nowhere else in the enclosing function (a value, an assignment,
     * typeof), which has no direct eval or with. Every call then passes its arguments in the code
     * optimized for the caller.
     */
    private static void collectNumberArgumentCandidates_r(
            ScriptNode scriptOrFn,
            Map<OptFunctionNode, List<Node>> calls,
            Map<Node, OptFunctionNode> callers) {
        int functionCount = scriptOrFn.getFunctionCount();
        if (scriptOrFn.getType() == Token.FUNCTION && functionCount != 0) {
            Map<String, Integer> names = new HashMap<>();
            Map<OptFunctionNode, List<Node>> directCalls = new HashMap<>();
            if (scanNames_r(scriptOrFn, null, names, directCalls, callers)) {
                for (int i = 0; i != functionCount; ++i) {
                    OptFunctionNode f = OptFunctionNode.get(scriptOrFn, i);
                    FunctionNode fnode = f.fnode;
                    List<Node> fCalls = directCalls.get(f);
                    if (fCalls != null
                            && fnode.getFunctionType() == FunctionNode.FUNCTION_STATEMENT
                            && !fnode.requiresActivation()
                            && !fnode.hasRestParameter()
                            && !fnode.hasDestructuredParams()
                            && names.getOrDefault(fnode.getName(), 0) == calleeNames(fCalls)
                            && reachDeclaration(fCalls)) {
                        calls.put(f, fCalls);
                    }
                }
            }
        }
        for (int i = 0; i != functionCount; ++i) {
            collectNumberArgumentCandidates_r(
                    OptFunctionNode.get(scriptOrFn, i).fnode, calls, callers);
        }
    }

    /**
     * Counts the names used in a function and the functions nested in it, and collects their direct
     * calls; returns false if one has a direct eval or a with.
     */
    private static boolean scanNames_r(
            Node node,
            OptFunctionNode caller,
            Map<String, Integer> names,
            Map<OptFunctionNode, List<Node>> directCalls,
            Map<Node, OptFunctionNode> callers) {
        if (node instanceof ScriptNode) {
            ScriptNode scriptOrFn = (ScriptNode) node;
            caller = OptFunctionNode.get(scriptOrFn);
            for (int i = 0, n = scriptOrFn.getFunctionCount(); i != n; ++i) {
                if (!scanNames_r(
                        scriptOrFn.getFunctionNode(i), null, names, directCalls, callers)) {
                    return false;
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            int type = child.getType();
            if (type == Token.ENTERWITH
                    || ((type == Token.CALL || type == Token.NEW)
                            && child.getIntProp(Node.SPECIALCALL_PROP, Node.NON_SPECIALCALL)
                                    != Node.NON_SPECIALCALL)) {
                return false;
            }
            // not a property name, nor the statement of a declaration
            if (child instanceof Name && type != Token.STRING && type != Token.FUNCTION) {
                names.merge(child.getString(), 1, Integer::sum);
            }
            OptFunctionNode target = (OptFunctionNode) child.getProp(Node.DIRECTCALL_PROP);
            if (target != null) {
                directCalls.computeIfAbsent(target, k -> new ArrayList<>()).add(child);
                callers.put(child, caller);
            }
            if (!(child instanceof ScriptNode)
                    && !scanNames_r(child, caller, names, directCalls, callers)) {
                return false;
            }
        }
        return true;
    }

    /** The uses of the name of the target that are the callee of its direct calls. */
    /**
     * Whether every call reaches the declaration of its target when it is not assigned: not a
     * method call of the same name (o.f()), nor a call of a binding of the name in between (a
     * parameter f). Another callee would return anything.
     */
    private static boolean reachDeclaration(List<Node> calls) {
        for (Node call : calls) {
            if (call.getIntProp(Node.DIRECT_TARGET_PROP, 0) == 0) {
                return false;
            }
        }
        return true;
    }

    private static int calleeNames(List<Node> calls) {
        int count = 0;
        for (Node call : calls) {
            Node callee = call.getFirstChild();
            if (callee instanceof Name && callee.getType() != Token.STRING) {
                count++;
            }
        }
        return count;
    }

    /** By parameter, whether every direct call of a function passes it a double. */
    private static boolean[] numberArguments(
            OptFunctionNode f, List<Node> calls, Map<Node, OptFunctionNode> callers) {
        boolean[] numbers = new boolean[f.fnode.getParamCount()];
        Arrays.fill(numbers, true);
        for (Node call : calls) {
            OptFunctionNode caller = callers.get(call);
            int i = 0;
            for (Node arg = call.getFirstChild().getNext(); arg != null; arg = arg.getNext()) {
                numbers[i] &= caller != null && isNumberArgument(caller, arg);
                i++;
            }
        }
        return numbers;
    }

    /** Whether an argument of a direct call from a function already analyzed is a number. */
    private static boolean isNumberArgument(OptFunctionNode caller, Node arg) {
        return isNumberExpression(arg, caller, caller::isNumberVar, null);
    }

    /**
     * Whether a function has variables in registers: all of them, or with an activation object, the
     * ones that no nested function refers to (VERSION_ECMASCRIPT).
     */
    private static boolean hasRegisterVars(OptFunctionNode f) {
        return !f.fnode.requiresActivation() || f.fnode.getRegisterVars() != null;
    }

    /** Finds the variables of a function that only hold numbers. */
    private void analyzeFunction(OptFunctionNode theFunction, boolean nested) {
        if (!hasRegisterVars(theFunction)) return;

        ArrayList<Node> statementsArray = new ArrayList<>();
        buildStatementList_r(theFunction.fnode, statementsArray);
        Node[] theStatementNodes = new Node[statementsArray.size()];
        statementsArray.toArray(theStatementNodes);

        // an arithmetic operation on two BigInts gives a BigInt: before VERSION_ECMASCRIPT,
        // top-level
        // functions keep the legacy typing (their result as a number)
        Block.runFlowAnalyzes(
                theFunction, theStatementNodes, nested || compilerEnv.isLatestEcmaScript());
        statements.put(theFunction, theStatementNodes);
    }

    private void rewriteFunction(OptFunctionNode theFunction, boolean nested) {
        if (!hasRegisterVars(theFunction)) return;

        inDirectCallFunction = theFunction.isTargetOfDirectCall();
        // nested functions were not optimized before, hence never had the legacy typing
        bigIntSafeParameters = nested || compilerEnv.isLatestEcmaScript();
        this.theFunction = theFunction;
        Node[] theStatementNodes = statements.get(theFunction);

        /*
         * Now that we know which local vars are in fact always
         * Numbers, we re-write the tree to take advantage of
         * that. Any arithmetic or assignment op involving just
         * Number typed vars is marked so that the codegen will
         * generate non-object code.
         */
        parameterUsedInNumberContext = false;
        for (Node theStatementNode : theStatementNodes) {
            rewriteForNumberVariables(theStatementNode, NumberType);
        }
        theFunction.setParameterNumberContext(parameterUsedInNumberContext);
    }

    /*
            Each directCall parameter is passed as a pair of values - an object
            and a double. The value passed depends on the type of value available at
            the call site. If a double is available, the object in java/lang/Void.TYPE
            is passed as the object value, and if an object value is available, then
            0.0 is passed as the double value.

            The receiving routine always tests the object value before proceeding.
            If the parameter is being accessed in a 'Number Context' then the code
            sequence is :
            if ("parameter_objectValue" == java/lang/Void.TYPE)
                ...fine..., use the parameter_doubleValue
            else
                toNumber(parameter_objectValue)

            and if the parameter is being referenced in an Object context, the code is
            if ("parameter_objectValue" == java/lang/Void.TYPE)
                Double.valueOf(parameter_doubleValue)
            else
                ...fine..., use the parameter_objectValue

            If the receiving code never uses the doubleValue, it is converted on
            entry to a Double instead.
    */

    /*
            We're referencing a node in a Number context (i.e. we'd prefer it
            was a double value). If the node is a parameter in a directCall
            function, mark it as being referenced in this context.
    */
    private void markDCPNumberContext(Node n) {
        if (inDirectCallFunction && n.getType() == Token.GETVAR) {
            int varIndex = theFunction.getVarIndex(n);
            if (theFunction.isParameter(varIndex)) {
                parameterUsedInNumberContext = true;
            }
        }
    }

    private boolean convertParameter(Node n) {
        if (inDirectCallFunction && n.getType() == Token.GETVAR) {
            int varIndex = theFunction.getVarIndex(n);
            if (theFunction.isParameter(varIndex)) {
                n.removeProp(Node.ISNUMBER_PROP);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the code of a node, an argument of a direct call, produces a double: typed as a
     * number, and not a comparison (whose ISNUMBER_PROP types its operands).
     */
    static boolean isNumberValue(Node n) {
        switch (n.getType()) {
            case Token.LE:
            case Token.LT:
            case Token.GE:
            case Token.GT:
                return false;
            default:
                return n.getIntProp(Node.ISNUMBER_PROP, -1) == Node.BOTH;
        }
    }

    /**
     * Whether an arithmetic operation on these operands, typed as numbers, may have two BigInt
     * operands: a parameter (which can hold a BigInt) and another parameter or an operand of
     * unknown type. A parameter and a number throw a TypeError either way.
     */
    private boolean mayBeBigIntOperation(Node lChild, int lType, Node rChild, int rType) {
        if (!bigIntSafeParameters) {
            return false;
        }
        boolean lParam = isDirectCallParameter(lChild);
        boolean rParam = isDirectCallParameter(rChild);
        return (lParam && (rParam || rType != NumberType))
                || (rParam && (lParam || lType != NumberType));
    }

    private boolean isDirectCallParameter(Node n) {
        return inDirectCallFunction
                && n.getType() == Token.GETVAR
                && theFunction.isParameter(theFunction.getVarIndex(n));
    }

    private int rewriteForNumberVariables(Node n, int desired) {
        switch (n.getType()) {
            case Token.EXPR_VOID:
                {
                    Node child = n.getFirstChild();
                    int type = rewriteForNumberVariables(child, NumberType);
                    if (type == NumberType) n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                    return NoType;
                }
            case Token.NUMBER:
                n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                return NumberType;

            case Token.GETVAR:
                {
                    int varIndex = theFunction.getVarIndex(n);
                    if (inDirectCallFunction
                            && theFunction.isParameter(varIndex)
                            && desired == NumberType) {
                        n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        return NumberType;
                    } else if (theFunction.isNumberVar(varIndex)) {
                        n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        return NumberType;
                    }
                    return NoType;
                }

            case Token.INC:
            case Token.DEC:
                {
                    Node child = n.getFirstChild();
                    if (child.getIntProp(Node.SUPER_PROPERTY_ACCESS, 0) == 1) {
                        // Don't optimize super.prop++ and related
                        return NoType;
                    }
                    int type = rewriteForNumberVariables(child, NumberType);
                    if (child.getType() == Token.GETVAR) {
                        if (type == NumberType && !convertParameter(child)) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                            markDCPNumberContext(child);
                            return NumberType;
                        }
                        return NoType;
                    } else if (child.getType() == Token.GETELEM
                            || child.getType() == Token.GETPROP) {
                        return type;
                    }
                    return NoType;
                }
            case Token.SETVAR:
            case Token.SETCONSTVAR:
                {
                    Node lChild = n.getFirstChild();
                    Node rChild = lChild.getNext();
                    int rType = rewriteForNumberVariables(rChild, NumberType);
                    int varIndex = theFunction.getVarIndex(n);
                    if (inDirectCallFunction && theFunction.isParameter(varIndex)) {
                        if (rType == NumberType) {
                            if (!convertParameter(rChild)) {
                                n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                                return NumberType;
                            }
                            markDCPNumberContext(rChild);
                            return NoType;
                        }
                        return rType;
                    } else if (theFunction.isNumberVar(varIndex)) {
                        if (rType != NumberType) {
                            n.removeChild(rChild);
                            n.addChildToBack(new Node(Token.TO_DOUBLE, rChild));
                        }
                        n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        markDCPNumberContext(rChild);
                        return NumberType;
                    } else {
                        if (rType == NumberType) {
                            if (!convertParameter(rChild)) {
                                n.removeChild(rChild);
                                n.addChildToBack(new Node(Token.TO_OBJECT, rChild));
                            }
                        }
                        return NoType;
                    }
                }
            case Token.LE:
            case Token.LT:
            case Token.GE:
            case Token.GT:
                {
                    Node lChild = n.getFirstChild();
                    Node rChild = lChild.getNext();
                    int lType = rewriteForNumberVariables(lChild, NumberType);
                    int rType = rewriteForNumberVariables(rChild, NumberType);
                    markDCPNumberContext(lChild);
                    markDCPNumberContext(rChild);

                    if (convertParameter(lChild)) {
                        if (convertParameter(rChild)) {
                            return NoType;
                        } else if (rType == NumberType) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.RIGHT);
                        }
                    } else if (convertParameter(rChild)) {
                        if (lType == NumberType) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.LEFT);
                        }
                    } else {
                        if (lType == NumberType) {
                            if (rType == NumberType) {
                                n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                            } else {
                                n.putIntProp(Node.ISNUMBER_PROP, Node.LEFT);
                            }
                        } else {
                            if (rType == NumberType) {
                                n.putIntProp(Node.ISNUMBER_PROP, Node.RIGHT);
                            }
                        }
                    }
                    // we actually build a boolean value
                    return NoType;
                }

            case Token.ADD:
                {
                    Node lChild = n.getFirstChild();
                    Node rChild = lChild.getNext();
                    int lType = rewriteForNumberVariables(lChild, NumberType);
                    int rType = rewriteForNumberVariables(rChild, NumberType);

                    if (convertParameter(lChild)) {
                        if (convertParameter(rChild)) {
                            return NoType;
                        }
                        if (rType == NumberType) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.RIGHT);
                        }
                    } else {
                        if (convertParameter(rChild)) {
                            if (lType == NumberType) {
                                n.putIntProp(Node.ISNUMBER_PROP, Node.LEFT);
                            }
                        } else {
                            if (lType == NumberType) {
                                if (rType == NumberType) {
                                    n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                                    return NumberType;
                                }
                                n.putIntProp(Node.ISNUMBER_PROP, Node.LEFT);
                            } else {
                                if (rType == NumberType) {
                                    n.putIntProp(Node.ISNUMBER_PROP, Node.RIGHT);
                                }
                            }
                        }
                    }
                    return NoType;
                }

            case Token.BITXOR:
            case Token.BITOR:
            case Token.BITAND:
            case Token.RSH:
            case Token.LSH:
            case Token.SUB:
            case Token.MUL:
            case Token.DIV:
            case Token.MOD:
            case Token.EXP:
                {
                    Node lChild = n.getFirstChild();
                    Node rChild = lChild.getNext();
                    int lType = rewriteForNumberVariables(lChild, NumberType);
                    int rType = rewriteForNumberVariables(rChild, NumberType);
                    if (mayBeBigIntOperation(lChild, lType, rChild, rType)) {
                        convertParameter(lChild);
                        convertParameter(rChild);
                        return AnyType;
                    }
                    markDCPNumberContext(lChild);
                    markDCPNumberContext(rChild);
                    if (lType == NumberType) {
                        if (rType == NumberType) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                            return NumberType;
                        }
                        if (!convertParameter(rChild)) {
                            n.removeChild(rChild);
                            n.addChildToBack(new Node(Token.TO_DOUBLE, rChild));
                            n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        }
                        return NumberType;
                    }
                    if (rType == NumberType) {
                        if (!convertParameter(lChild)) {
                            n.removeChild(lChild);
                            n.addChildToFront(new Node(Token.TO_DOUBLE, lChild));
                            n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        }
                        return NumberType;
                    }
                    // Numeric Type (Number or BigInt)
                    return AnyType;
                }

            case Token.BITNOT:
            case Token.POS:
            case Token.NEG:
                {
                    Node child = n.getFirstChild();
                    int type = rewriteForNumberVariables(child, NumberType);
                    if (type == NumberType && !convertParameter(child)) {
                        n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        markDCPNumberContext(child);
                        return NumberType;
                    }
                    return NoType;
                }

            case Token.SETELEM:
            case Token.SETELEM_OP:
                {
                    Node arrayBase = n.getFirstChild();
                    Node arrayIndex = arrayBase.getNext();
                    Node rValue = arrayIndex.getNext();
                    int baseType = rewriteForNumberVariables(arrayBase, NumberType);
                    if (baseType == NumberType) {
                        if (!convertParameter(arrayBase)) {
                            n.removeChild(arrayBase);
                            n.addChildToFront(new Node(Token.TO_OBJECT, arrayBase));
                        }
                    }
                    int indexType = rewriteForNumberVariables(arrayIndex, NumberType);
                    if (indexType == NumberType) {
                        if (!convertParameter(arrayIndex)) {
                            // setting the ISNUMBER_PROP signals the codegen
                            // to use the OptRuntime.setObjectIndex that takes
                            // a double index
                            n.putIntProp(Node.ISNUMBER_PROP, Node.LEFT);
                        }
                    }
                    int rValueType = rewriteForNumberVariables(rValue, NumberType);
                    if (rValueType == NumberType) {
                        if (!convertParameter(rValue)) {
                            n.removeChild(rValue);
                            n.addChildToBack(new Node(Token.TO_OBJECT, rValue));
                        }
                    }
                    return NoType;
                }
            case Token.GETELEM:
                {
                    Node arrayBase = n.getFirstChild();
                    Node arrayIndex = arrayBase.getNext();
                    int baseType = rewriteForNumberVariables(arrayBase, NumberType);
                    if (baseType == NumberType) {
                        if (!convertParameter(arrayBase)) {
                            n.removeChild(arrayBase);
                            n.addChildToFront(new Node(Token.TO_OBJECT, arrayBase));
                        }
                    }
                    int indexType = rewriteForNumberVariables(arrayIndex, NumberType);
                    if (indexType == NumberType) {
                        if (!convertParameter(arrayIndex)) {
                            // setting the ISNUMBER_PROP signals the codegen
                            // to use the OptRuntime.getObjectIndex that takes
                            // a double index
                            n.putIntProp(Node.ISNUMBER_PROP, Node.RIGHT);
                        }
                    }
                    return NoType;
                }
            case Token.CALL:
                {
                    Node child = n.getFirstChild(); // the function node
                    // must be an object
                    rewriteAsObjectChildren(child, child.getFirstChild());
                    child = child.getNext(); // the first arg

                    OptFunctionNode target = (OptFunctionNode) n.getProp(Node.DIRECTCALL_PROP);
                    if (target != null) {
                        /*
                            we leave each child as a Number if it can be. The codegen will
                            handle moving the pairs of parameters.
                        */
                        while (child != null) {
                            int type = rewriteForNumberVariables(child, NumberType);
                            if (type == NumberType) {
                                markDCPNumberContext(child);
                            }
                            child = child.getNext();
                        }
                        if (target.returnsNumber()) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                            return NumberType;
                        }
                    } else {
                        rewriteAsObjectChildren(n, child);
                    }
                    return NoType;
                }
            case Token.HOOK:
                {
                    Node cond = n.getFirstChild();
                    Node ifTrue = cond.getNext();
                    Node ifFalse = ifTrue.getNext();
                    int condType = rewriteForNumberVariables(cond, NoType);
                    boolean trueNumber =
                            rewriteForNumberVariables(ifTrue, NumberType) == NumberType
                                    && !convertParameter(ifTrue);
                    boolean falseNumber =
                            rewriteForNumberVariables(ifFalse, NumberType) == NumberType
                                    && !convertParameter(ifFalse);
                    if (condType == NumberType && !convertParameter(cond)) {
                        wrapAsObject(n, cond);
                    }
                    if (trueNumber && falseNumber) {
                        n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        return NumberType;
                    }
                    if (trueNumber) {
                        wrapAsObject(n, ifTrue);
                    }
                    if (falseNumber) {
                        wrapAsObject(n, ifFalse);
                    }
                    return NoType;
                }
            case Token.RETURN:
                {
                    Node child = n.getFirstChild();
                    if (theFunction.returnsNumber()) {
                        int type = rewriteForNumberVariables(child, NumberType);
                        if (type == NumberType && !convertParameter(child)) {
                            n.putIntProp(Node.ISNUMBER_PROP, Node.BOTH);
                        }
                    } else {
                        rewriteAsObjectChildren(n, child);
                    }
                    return NoType;
                }
            case Token.OBJECTLIT:
            case Token.OBJECT_REST:
                {
                    rewriteComputedKeys(n);
                    rewriteAsObjectChildren(n, n.getFirstChild());
                    return NoType;
                }
            default:
                {
                    rewriteAsObjectChildren(n, n.getFirstChild());
                    return NoType;
                }
        }
    }

    /**
     * The computed keys of an object literal or of an object rest pattern are not children of its
     * node but entries of its OBJECT_IDS_PROP, which the code generator evaluates as objects: a key
     * that reads a number variable, as {[i]: v} does, must be converted like any child.
     */
    private void rewriteComputedKeys(Node n) {
        Object[] ids = (Object[]) n.getProp(Node.OBJECT_IDS_PROP);
        if (ids == null) {
            return;
        }
        for (int i = 0; i < ids.length; i++) {
            if (!(ids[i] instanceof Node)) {
                continue;
            }
            Node id = (Node) ids[i];
            if (id.getType() == Token.COMPUTED_PROPERTY) {
                Node key = id.getFirstChild();
                if (key != null && rewriteForNumberVariables(key, NoType) == NumberType) {
                    wrapAsObject(id, key);
                }
            } else if (n.getType() == Token.OBJECT_REST
                    && rewriteForNumberVariables(id, NoType) == NumberType) {
                // a computed key of a rest pattern is the expression itself (an entry of an
                // object literal that is not a COMPUTED_PROPERTY is something else, a spread)
                ids[i] = toObject(id);
            }
        }
    }

    private static void wrapAsObject(Node parent, Node child) {
        Node next = child.getNext();
        parent.removeChild(child);
        Node nuChild = toObject(child);
        if (next == null) parent.addChildToBack(nuChild);
        else parent.addChildBefore(nuChild, next);
    }

    /**
     * A number converted to an object, in place of the node; the value of a __proto__ property of
     * an object literal stays one (BodyCodegen#getStoreMethodNameForLiteralProperty).
     */
    private static Node toObject(Node child) {
        Node nuChild = new Node(Token.TO_OBJECT, child);
        if (child.getIntProp(Node.PROTO_SETTER_PROP, 0) == 1) {
            nuChild.putIntProp(Node.PROTO_SETTER_PROP, 1);
        }
        return nuChild;
    }

    private void rewriteAsObjectChildren(Node n, Node child) {
        // Force optimized children to be objects
        while (child != null) {
            Node nextChild = child.getNext();
            int type = rewriteForNumberVariables(child, NoType);
            if (type == NumberType) {
                if (!convertParameter(child)) {
                    n.removeChild(child);
                    Node nuChild = toObject(child);
                    if (nextChild == null) n.addChildToBack(nuChild);
                    else n.addChildBefore(nuChild, nextChild);
                }
            }
            child = nextChild;
        }
    }

    private static void buildStatementList_r(Node node, List<Node> statements) {
        int type = node.getType();
        if (type == Token.BLOCK
                || type == Token.LOCAL_BLOCK
                || type == Token.LOOP
                || type == Token.FUNCTION) {
            Node child = node.getFirstChild();
            while (child != null) {
                buildStatementList_r(child, statements);
                child = child.getNext();
            }
        } else {
            statements.add(node);
        }
    }

    private final CompilerEnvirons compilerEnv;
    private final Map<OptFunctionNode, Node[]> statements = new HashMap<>();
    private boolean inDirectCallFunction;
    private boolean bigIntSafeParameters;
    OptFunctionNode theFunction;
    private boolean parameterUsedInNumberContext;
}
