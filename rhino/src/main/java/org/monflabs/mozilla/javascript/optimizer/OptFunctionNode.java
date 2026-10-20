/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.optimizer;

import org.monflabs.mozilla.javascript.Kit;
import org.monflabs.mozilla.javascript.Node;
import org.monflabs.mozilla.javascript.Token;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.ScriptNode;

public final class OptFunctionNode {
    OptFunctionNode(FunctionNode fnode) {
        this.fnode = fnode;
        fnode.setCompilerData(this);
    }

    public static OptFunctionNode get(ScriptNode scriptOrFn, int i) {
        FunctionNode fnode = scriptOrFn.getFunctionNode(i);
        return (OptFunctionNode) fnode.getCompilerData();
    }

    public static OptFunctionNode get(ScriptNode scriptOrFn) {
        return (OptFunctionNode) scriptOrFn.getCompilerData();
    }

    public boolean isTargetOfDirectCall() {
        return directTargetIndex >= 0;
    }

    public int getDirectTargetIndex() {
        return directTargetIndex;
    }

    void setDirectTargetIndex(int directTargetIndex) {
        // One time action
        if (directTargetIndex < 0 || this.directTargetIndex >= 0) Kit.codeBug();
        this.directTargetIndex = directTargetIndex;
    }

    void setParameterNumberContext(boolean b) {
        itsParameterNumberContext = b;
    }

    public boolean getParameterNumberContext() {
        return itsParameterNumberContext;
    }

    public int getVarCount() {
        return fnode.getParamAndVarCount();
    }

    /**
     * A parameter that the direct calls pass as an object and a double, which can hold any value
     * (not one of the number parameters, see #hasNumberArgument).
     */
    public boolean isParameter(int varIndex) {
        return varIndex < fnode.getParamCount() && !isNumberVar(varIndex);
    }

    public boolean isNumberVar(int varIndex) {
        return numberVarFlags != null && numberVarFlags[varIndex];
    }

    void setIsNumberVar(int varIndex) {
        // a parameter only when every call passes a number
        if (varIndex < fnode.getParamCount() && !hasNumberArgument(varIndex)) Kit.codeBug();
        if (numberVarFlags == null) {
            numberVarFlags = new boolean[fnode.getParamAndVarCount()];
        }
        numberVarFlags[varIndex] = true;
    }

    /** Whether every call of the function passes a number to this parameter. */
    boolean hasNumberArgument(int paramIndex) {
        return numberArguments != null && numberArguments[paramIndex];
    }

    void setNumberArguments(boolean[] numberArguments) {
        this.numberArguments = numberArguments;
    }

    public int getVarIndex(Node n) {
        int index = n.getIntProp(Node.VARIABLE_PROP, -1);
        if (index == -1) {
            Node node;
            int type = n.getType();
            if (type == Token.GETVAR) {
                node = n;
            } else if (type == Token.SETVAR || type == Token.SETCONSTVAR) {
                node = n.getFirstChild();
            } else {
                throw Kit.codeBug();
            }
            index = fnode.getIndexForNameNode(node);
            if (index < 0) throw Kit.codeBug();
            n.putIntProp(Node.VARIABLE_PROP, index);
        }
        return index;
    }

    public final FunctionNode fnode;

    /**
     * Whether the function can read its this: a this, a super or a direct eval in its body or in
     * its arrow functions, which share its this.
     */
    boolean readsThis() {
        if (readsThis == null) {
            readsThis = readsThis_r(fnode);
        }
        return readsThis;
    }

    private static boolean readsThis_r(Node node) {
        if (node instanceof ScriptNode) {
            ScriptNode scriptOrFn = (ScriptNode) node;
            for (int i = 0, n = scriptOrFn.getFunctionCount(); i != n; ++i) {
                FunctionNode nested = scriptOrFn.getFunctionNode(i);
                if (nested.getFunctionType() == FunctionNode.ARROW_FUNCTION
                        && readsThis_r(nested)) {
                    return true;
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            int type = child.getType();
            if (type == Token.THIS
                    || type == Token.SUPER
                    || ((type == Token.CALL || type == Token.NEW)
                            && child.getIntProp(Node.SPECIALCALL_PROP, Node.NON_SPECIALCALL)
                                    != Node.NON_SPECIALCALL)) {
                return true;
            }
            if (!(child instanceof ScriptNode) && readsThis_r(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean[] numberVarFlags;
    private boolean[] numberArguments;
    private Boolean readsThis;
    private int directTargetIndex = -1;
    private boolean itsParameterNumberContext;
    boolean itsContainsCalls0;
    boolean itsContainsCalls1;
}
