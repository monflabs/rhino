/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // a function whose arguments may be numbers is optimized after the functions that call it
        Set<OptFunctionNode> done = new HashSet<>();
        for (Map.Entry<OptFunctionNode, Boolean> e : functions.entrySet()) {
            if (!calls.containsKey(e.getKey())) {
                optimizeFunction(e.getKey(), e.getValue());
                done.add(e.getKey());
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
                    optimizeFunction(f, functions.get(f));
                    done.add(f);
                    progress = true;
                }
            }
        }
        // calls from each other (recursion): arguments of any type
        for (OptFunctionNode f : calls.keySet()) {
            if (!done.contains(f)) {
                optimizeFunction(f, functions.get(f));
            }
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
                            && fnode.getParamCount() != 0
                            && !fnode.requiresActivation()
                            && !fnode.hasRestParameter()
                            && !fnode.hasDestructuredParams()
                            && names.getOrDefault(fnode.getName(), 0) == calleeNames(fCalls)) {
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
                // a parameter of a direct call target is passed as its object and double
                boolean passedThrough =
                        caller != null
                                && caller.isTargetOfDirectCall()
                                && arg.getType() == Token.GETVAR
                                && caller.isParameter(caller.getVarIndex(arg));
                numbers[i] &= caller != null && !passedThrough && isNumberValue(arg);
                i++;
            }
        }
        return numbers;
    }

    private void optimizeFunction(OptFunctionNode theFunction, boolean nested) {
        if (theFunction.fnode.requiresActivation()) return;

        inDirectCallFunction = theFunction.isTargetOfDirectCall();
        // nested functions were not optimized before, hence never had the legacy typing
        bigIntSafeParameters = nested || compilerEnv.isLatestEcmaScript();
        this.theFunction = theFunction;

        ArrayList<Node> statementsArray = new ArrayList<>();
        buildStatementList_r(theFunction.fnode, statementsArray);
        Node[] theStatementNodes = new Node[statementsArray.size()];
        statementsArray.toArray(theStatementNodes);

        Block.runFlowAnalyzes(theFunction, theStatementNodes);

        if (!theFunction.fnode.requiresActivation()) {
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
                    } else {
                        rewriteAsObjectChildren(n, child);
                    }
                    return NoType;
                }
            default:
                {
                    rewriteAsObjectChildren(n, n.getFirstChild());
                    return NoType;
                }
        }
    }

    private void rewriteAsObjectChildren(Node n, Node child) {
        // Force optimized children to be objects
        while (child != null) {
            Node nextChild = child.getNext();
            int type = rewriteForNumberVariables(child, NoType);
            if (type == NumberType) {
                if (!convertParameter(child)) {
                    n.removeChild(child);
                    Node nuChild = new Node(Token.TO_OBJECT, child);
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
    private boolean inDirectCallFunction;
    private boolean bigIntSafeParameters;
    OptFunctionNode theFunction;
    private boolean parameterUsedInNumberContext;
}
