/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ScriptableObject.CONST;
import static org.monflabs.mozilla.javascript.ScriptableObject.PERMANENT;

import java.io.Serial;

/**
 * This class implements the activation object.
 *
 * <p>See ECMA 10.1.6
 *
 * @see org.monflabs.mozilla.javascript.Arguments
 * @author Norris Boyd
 */
public final class NativeCall extends DeclarationScope {
    @Serial private static final long serialVersionUID = -7471457301304454454L;

    private static final Object CALL_TAG = "Call";

    NativeCall(
            JSFunction function,
            Context cx,
            VarScope scope,
            Object[] args,
            boolean isArrow,
            boolean argsHasRest,
            boolean requiresArgumentObject) {
        super(scope);
        this.function = function;
        // leave prototype null

        this.originalArgs = (args == null) ? ScriptRuntime.emptyArgs : args;

        // initialize values of arguments
        int paramAndVarCount = function.getParamAndVarCount();
        int paramCount = function.getParamCount();
        // the parameters and variables in registers are not properties of the activation (the rest
        // parameter aside: the compiled code reads its array from here)
        JSDescriptor<JSFunction> desc = function.getDescriptor();
        if (paramAndVarCount != 0) {
            if (argsHasRest) {
                Object[] vals;
                if (args.length >= paramCount) {
                    vals = new Object[args.length - paramCount];
                    System.arraycopy(args, paramCount, vals, 0, args.length - paramCount);
                } else {
                    vals = ScriptRuntime.emptyArgs;
                }

                for (int i = 0; i < paramCount; ++i) {
                    if (desc.isParamOrVarInRegister(i)) {
                        continue;
                    }
                    String name = function.getParamOrVarName(i);
                    Object val = i < args.length ? args[i] : Undefined.instance;
                    defineProperty(name, val, PERMANENT);
                }
                defineProperty(
                        function.getParamOrVarName(paramCount),
                        cx.newArray(scope, vals),
                        PERMANENT);
            } else {
                for (int i = 0; i < paramCount; ++i) {
                    if (desc.isParamOrVarInRegister(i)) {
                        continue;
                    }
                    String name = function.getParamOrVarName(i);
                    Object val = i < args.length ? args[i] : Undefined.instance;
                    defineProperty(name, val, PERMANENT);
                }
            }
        }

        // initialize "arguments" property but only if it was not overridden by
        // the parameter with the same name
        if (requiresArgumentObject && !isArrow && !super.has("arguments", this)) {
            defineProperty("arguments", new Arguments(this, cx), PERMANENT);
        }

        if (paramAndVarCount != 0) {
            for (int i = paramCount; i < paramAndVarCount; ++i) {
                if (desc.isParamOrVarInRegister(i) && !(argsHasRest && i == paramCount)) {
                    continue;
                }
                String name = function.getParamOrVarName(i);
                if (!super.has(name, this)) {
                    if (function.getParamOrVarConst(i)) {
                        defineProperty(name, Undefined.instance, CONST);
                    } else if (function.hasNoFunctionStatementNamed(name)) {
                        // Properties for FUNCTION_STATEMENTs are created in
                        // Interpreter.CallFrame.initializeArgs().
                        defineProperty(name, Undefined.instance, PERMANENT);
                    }
                }
            }
        }
        if (function.getDescriptor().hasSelfNameBinding()) {
            // the immutable binding of a function expression's name, already in the parameters
            defineProperty(function.getFunctionName(), function, PERMANENT);
            selfNameInitialized = true;
        }
    }

    private boolean selfNameInitialized;

    /**
     * Whether storing value in name assigns the immutable binding of a function expression's name
     * (its initialization stores the function itself).
     */
    boolean isSelfName(String name, Object value) {
        return selfNameInitialized && value != function && name.equals(function.getFunctionName());
    }

    @Override
    protected boolean putOwnProperty(String name, VarScope start, Object value, boolean isThrow) {
        if (selfNameInitialized && name.equals(function.getFunctionName()) && start == this) {
            // an assignment to the name of a function expression: ignored (strict code throws,
            // see ScriptRuntime#strictSetName)
            return true;
        }
        return super.putOwnProperty(name, start, value, isThrow);
    }

    public Scriptable getHomeObject() {
        return function.getHomeObject();
    }

    final JSFunction function;

    /** The new.target of the call, read by eval code. */
    Object newTarget = Undefined.instance;

    final Object[] originalArgs;

    transient NativeCall parentActivationCall;
}
