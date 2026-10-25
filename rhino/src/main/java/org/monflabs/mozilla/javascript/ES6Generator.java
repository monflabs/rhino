/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.Symbol.Kind.REGULAR;

import java.io.Serial;

public final class ES6Generator extends ScriptableObject {
    @Serial private static final long serialVersionUID = -1617667918827493330L;

    static final SymbolKey GENERATOR_TAG = new SymbolKey("GeneratorPrototype", REGULAR);

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(GENERATOR_TAG)
                        .withMethod(CTOR, "next", 1, ES6Generator::js_next)
                        .withMethod(CTOR, "return", 1, ES6Generator::js_return)
                        .withMethod(CTOR, "throw", 1, ES6Generator::js_throw)
                        .withMethod(CTOR, SymbolKey.ITERATOR, 0, ES6Generator::js_iterator)
                        .withProp(
                                CTOR,
                                SymbolKey.TO_STRING_TAG,
                                value("Generator", DONTENUM | READONLY))
                        .build();
    }

    private JSFunction function;
    private Object savedState;
    private String lineSource;
    private int lineNumber;
    private State state = State.SUSPENDED_START;
    private Object delegee;

    static ScriptableObject init(Context cx, TopLevel scope, boolean sealed) {

        NativeObject prototype = new NativeObject();
        boolean latest = cx.isLatestEcmaScript();
        DESCRIPTOR.populateGlobal(cx, scope, prototype, sealed && !latest);

        var iterCtor = (JSFunction) scope.get("Iterator", scope);
        prototype.setPrototype((Scriptable) iterCtor.getPrototypeProperty());
        if (latest) {
            // @@iterator is inherited from %Iterator.prototype% (27.1.4.13)
            prototype.delete(SymbolKey.ITERATOR);
            if (sealed) {
                prototype.sealObject();
            }
        }

        // Need to access Generator prototype when constructing
        // Generator instances, but don't have a generator constructor
        // to use to find the prototype. Use the "associateValue"
        // approach instead.
        if (scope != null) {
            scope.associateValue(GENERATOR_TAG, prototype);
        }

        return prototype;
    }

    /** Only for constructing the prototype object. */
    private ES6Generator() {}

    public ES6Generator(VarScope scope, JSFunction function, Object savedState) {
        this.function = function;
        this.savedState = savedState;
        // Set parent and prototype properties.
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        this.setParentScope(top);
        // Per ES6 spec, generator instance's [[Prototype]] should be
        // the generator function's .prototype property.
        Object functionPrototype = ScriptableObject.getProperty(function, "prototype");
        if (functionPrototype instanceof Scriptable) {
            this.setPrototype((Scriptable) functionPrototype);
        } else {
            // If function.prototype is not an Object, use the intrinsic default prototype
            // Ref: Ecma 2026, 10.1.14 GetPrototypeFromConstructor step 4.
            // See test262: language/statements/generators/default-proto.js
            ScriptableObject prototype =
                    (ScriptableObject) ScriptableObject.getTopScopeValue(top, GENERATOR_TAG);
            this.setPrototype(prototype);
        }
    }

    @Override
    public String getClassName() {
        return "Generator";
    }

    /** Resumes the body with the strictness of the generator function, whoever resumes it. */
    private Object resume(Context cx, VarScope scope, int op, Object value) {
        if (!cx.isLatestEcmaScript() && !function.getDescriptor().isAsync()) {
            return function.resumeGenerator(cx, scope, op, savedState, value);
        }
        boolean parentStrict = ScriptRuntime.enterFunctionStrictness(cx, function.isStrict());
        try {
            return function.resumeGenerator(cx, scope, op, savedState, value);
        } finally {
            ScriptRuntime.exitFunctionStrictness(cx, parentStrict);
        }
    }

    boolean isStrict() {
        return function.isStrict();
    }

    boolean isBodyOf(JSFunction f) {
        return function == f;
    }

    private static ES6Generator realThis(Object thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, ES6Generator.class);
    }

    private static Object js_return(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        ES6Generator generator = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;
        if (generator.delegee == null) {
            return generator.resumeAbruptLocal(cx, s, NativeGenerator.GENERATOR_CLOSE, value);
        }
        return generator.resumeDelegeeReturn(cx, s, value);
    }

    private static Object js_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        ES6Generator generator = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;
        if (generator.delegee == null) {
            return generator.resumeLocal(cx, s, value);
        }
        return generator.resumeDelegee(cx, s, value);
    }

    private static Object js_throw(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        ES6Generator generator = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;
        if (generator.delegee == null) {
            return generator.resumeAbruptLocal(cx, s, NativeGenerator.GENERATOR_THROW, value);
        }
        return generator.resumeDelegeeThrow(cx, s, value);
    }

    private static Object js_iterator(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return thisObj;
    }

    private Scriptable resumeDelegee(Context cx, VarScope scope, Object value) {
        Object result = delegeeNext(cx, scope, value);
        if (result instanceof DelegeeDone) {
            // Return a result to the original generator
            return resumeLocal(cx, scope, ((DelegeeDone) result).value);
        }
        return (Scriptable) result;
    }

    /** The value of a yield* whose delegee is done, with which the generator resumes. */
    private static final class DelegeeDone {
        final Object value;

        DelegeeDone(Object value) {
            this.value = value;
        }
    }

    /**
     * Calls the next method of the delegee: its result, or a DelegeeDone when it is done, which
     * resumes the generator (in a loop of resumeLocal: a yield* whose delegee completes at once,
     * thousands of times, would otherwise use as many stack frames).
     */
    private Object delegeeNext(Context cx, VarScope scope, Object value) {
        try {
            // yield* calls next with the received value, also undefined (ECMA-262 15.5.5);
            // without an argument for undefined before VERSION_ECMASCRIPT
            Object[] nextArgs =
                    cx.isLatestEcmaScript() || !Undefined.isUndefined(value)
                            ? new Object[] {value}
                            : ScriptRuntime.emptyArgs;

            var nextFn = ScriptRuntime.getPropAndThis(delegee, ES6Iterator.NEXT_METHOD, cx, scope);
            Object nr = nextFn.call(cx, scope, nextArgs);

            Scriptable nextResult = ScriptableObject.ensureScriptable(nr);
            if (ScriptRuntime.isIteratorDone(cx, nextResult)) {
                // Iterator is "done".
                delegee = null;
                return new DelegeeDone(
                        ScriptableObject.getProperty(nextResult, ES6Iterator.VALUE_PROPERTY));
            }
            // Otherwise, we have a normal result and should continue
            return nextResult;

        } catch (RhinoException re) {
            // Exceptions from the delegee should be handled by the enclosing
            // generator, including if they're because functions can't be found.
            delegee = null;
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }
    }

    private Scriptable resumeDelegeeThrow(Context cx, VarScope scope, Object value) {
        boolean returnCalled = false;
        try {
            // Delegate to "throw" method. If it's not defined we'll get an error here.
            var throwFn = ScriptRuntime.getPropAndThis(delegee, "throw", cx, scope);
            Object throwResult = throwFn.call(cx, scope, new Object[] {value});

            if (ScriptRuntime.isIteratorDone(cx, throwResult)) {
                // Iterator is "done".
                try {
                    // Return a result to the original generator, but first optionally call "return"
                    returnCalled = true;
                    callReturnOptionally(cx, scope, Undefined.instance);
                } finally {
                    delegee = null;
                }
                return resumeLocal(
                        cx,
                        scope,
                        ScriptRuntime.getObjectProp(
                                throwResult, ES6Iterator.VALUE_PROPERTY, cx, scope));
            }
            // Otherwise, we have a normal result and should continue
            return ensureScriptable(throwResult);

        } catch (RhinoException re) {
            // Handle all exceptions, including missing methods, by delegating to original.
            try {
                if (!returnCalled) {
                    try {
                        callReturnOptionally(cx, scope, Undefined.instance);
                    } catch (RhinoException re2) {
                        return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re2);
                    }
                }
            } finally {
                delegee = null;
            }
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }
    }

    private Scriptable resumeDelegeeReturn(Context cx, VarScope scope, Object value) {
        try {
            // Call "return" but don't throw if it can't be found
            Object retResult = callReturnOptionally(cx, scope, value);
            // See https://tc39.es/ecma262/#sec-iteratorclose (2026, 7.4.11, 4b)
            // This calls GetMethod: https://tc39.es/ecma262/#sec-getmethod (2026, 7.3.10, 2)
            // We need to check if the return value is present and after the call
            // if return value is not undefined treat it same as null
            if (retResult != null && !Undefined.isUndefined(retResult)) {
                if (ScriptRuntime.isIteratorDone(cx, retResult)) {
                    // Iterator is "done".
                    delegee = null;
                    // Return a result to the original generator
                    return resumeAbruptLocal(
                            cx,
                            scope,
                            NativeGenerator.GENERATOR_CLOSE,
                            ScriptRuntime.getObjectPropNoWarn(
                                    retResult, ES6Iterator.VALUE_PROPERTY, cx, scope));
                } else {
                    // Not actually done yet!
                    return ensureScriptable(retResult);
                }
            }

            // No "return" -- let the original iterator return the value.
            delegee = null;
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_CLOSE, value);

        } catch (RhinoException re) {
            // Exceptions from the delegee should be handled by the enclosing
            // generator, including if they're because functions can't be found.
            delegee = null;
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }
    }

    Scriptable resumeLocal(Context cx, VarScope scope, Object value) {
        for (; ; ) {
            if (state == State.COMPLETED) {
                return ES6Iterator.makeIteratorResult(cx, scope, Boolean.TRUE);
            }
            if (state == State.EXECUTING) {
                throw ScriptRuntime.typeErrorById("msg.generator.executing");
            }
            Object resumed = resumeLocalOnce(cx, scope, value);
            if (!(resumed instanceof DelegeeDone)) {
                return (Scriptable) resumed;
            }
            value = ((DelegeeDone) resumed).value;
        }
    }

    /** One resumption of the generator: its result, or a DelegeeDone to resume it again. */
    private Object resumeLocalOnce(Context cx, VarScope scope, Object value) {
        Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, Boolean.FALSE);
        state = State.EXECUTING;

        try {
            Object r = resume(cx, scope, NativeGenerator.GENERATOR_SEND, value);

            if (r instanceof YieldStarResult) {
                // This special result tells us that we are executing a "yield *"
                return delegate(cx, scope, (YieldStarResult) r);
            }

            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, r);

        } catch (NativeGenerator.GeneratorClosedException gce) {
            // a return() whose finally block yielded completes now, with its value
            state = State.COMPLETED;
            if (cx.isLatestEcmaScript() || function.getDescriptor().isAsync()) {
                ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, gce.getValue());
            }
        } catch (JavaScriptException jse) {
            state = State.COMPLETED;
            if (jse.getValue() instanceof NativeIterator.StopIteration) {
                ScriptableObject.putProperty(
                        result,
                        ES6Iterator.VALUE_PROPERTY,
                        ((NativeIterator.StopIteration) jse.getValue()).getValue());
            } else {
                lineNumber = jse.lineNumber();
                lineSource = jse.lineSource();
                if (jse.getValue() instanceof RhinoException) {
                    throw (RhinoException) jse.getValue();
                }
                throw jse;
            }
        } catch (RhinoException re) {
            // an exception thrown out of the generator's body completes it (it stayed resumable
            // before VERSION_ECMASCRIPT)
            if (cx.isLatestEcmaScript()) {
                state = State.COMPLETED;
            }
            lineNumber = re.lineNumber();
            lineSource = re.lineSource();
            throw re;
        } finally {
            if (state == State.COMPLETED) {
                ScriptableObject.putProperty(result, ES6Iterator.DONE_PROPERTY, Boolean.TRUE);
            } else {
                state = State.SUSPENDED_YIELD;
            }
        }
        return result;
    }

    /**
     * Starts the yield* whose operand the generator's body suspended with: the first result of its
     * iterator, or a DelegeeDone to resume the generator with.
     */
    private Object delegate(Context cx, VarScope scope, YieldStarResult ysResult) {
        state = State.SUSPENDED_YIELD;
        try {
            delegee = ScriptRuntime.callIterator(ysResult.getResult(), cx, scope);
        } catch (RhinoException re) {
            // Need to handle exceptions if the iterator cannot be called.
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }

        Object delResult;
        try {
            // Re-execute but update state in case we end up back here
            // Value shall be Undefined based on the very complex spec!
            delResult = delegeeNext(cx, scope, Undefined.instance);
        } finally {
            state = State.EXECUTING;
        }
        if (delResult instanceof DelegeeDone) {
            // resumed by resumeLocal
            state = State.SUSPENDED_YIELD;
            return delResult;
        }
        if (ScriptRuntime.isIteratorDone(cx, delResult)) {
            state = State.COMPLETED;
        }
        return delResult;
    }

    Scriptable resumeAbruptLocal(Context cx, VarScope scope, int op, Object value) {
        if (state == State.EXECUTING) {
            throw ScriptRuntime.typeErrorById("msg.generator.executing");
        }
        if (state == State.SUSPENDED_START) {
            // Throw right away if we never started
            state = State.COMPLETED;
        }

        Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, Boolean.FALSE);
        if (state == State.COMPLETED) {
            if (op == NativeGenerator.GENERATOR_THROW) {
                throw new JavaScriptException(value, lineSource, lineNumber);
            }
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, value);
            ScriptableObject.putProperty(result, ES6Iterator.DONE_PROPERTY, Boolean.TRUE);
            return result;
        }

        state = State.EXECUTING;

        Object throwValue = value;
        if (op == NativeGenerator.GENERATOR_CLOSE) {
            if (!(value instanceof NativeGenerator.GeneratorClosedException)) {
                throwValue = new NativeGenerator.GeneratorClosedException(value);
            }
        } else {
            if (value instanceof JavaScriptException) {
                throwValue = ((JavaScriptException) value).getValue();
            } else if (value instanceof RhinoException) {
                throwValue = ScriptRuntime.wrapException((Throwable) value, scope, cx);
            }
        }

        try {
            Object r = resume(cx, scope, op, throwValue);
            if (r instanceof YieldStarResult && cx.isLatestEcmaScript()) {
                // a yield* in the catch or finally block that the throw or the return ran
                Object delegated = delegate(cx, scope, (YieldStarResult) r);
                if (delegated instanceof DelegeeDone) {
                    return resumeLocal(cx, scope, ((DelegeeDone) delegated).value);
                }
                if (state != State.COMPLETED) {
                    state = State.SUSPENDED_YIELD;
                }
                return (Scriptable) delegated;
            }
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, r);
            // If we get here without an exception we can still run.
            state = State.SUSPENDED_YIELD;

        } catch (NativeGenerator.GeneratorClosedException gce) {
            state = State.COMPLETED;
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, gce.getValue());
        } catch (JavaScriptException jse) {
            state = State.COMPLETED;
            if (jse.getValue() instanceof NativeIterator.StopIteration) {
                ScriptableObject.putProperty(
                        result,
                        ES6Iterator.VALUE_PROPERTY,
                        ((NativeIterator.StopIteration) jse.getValue()).getValue());
            } else {
                lineNumber = jse.lineNumber();
                lineSource = jse.lineSource();
                if (jse.getValue() instanceof RhinoException) {
                    throw (RhinoException) jse.getValue();
                }
                if (value instanceof JavaScriptException
                        && jse.getValue() == throwValue
                        && cx.isLatestEcmaScript()) {
                    // the exception of a delegee that the generator did not catch: the original,
                    // whose script stack shows where it was thrown
                    throw (JavaScriptException) value;
                }
                throw jse;
            }
        } catch (RhinoException re) {
            state = State.COMPLETED;
            lineNumber = re.lineNumber();
            lineSource = re.lineSource();
            throw re;
        } finally {
            // After an abrupt completion we are always, umm, complete,
            // and we will never delegate to the delegee again
            if (state == State.COMPLETED) {
                delegee = null;
                ScriptableObject.putProperty(result, ES6Iterator.DONE_PROPERTY, Boolean.TRUE);
            }
        }
        return result;
    }

    private Object callReturnOptionally(Context cx, VarScope scope, Object value) {
        Object[] retArgs =
                Undefined.isUndefined(value) ? ScriptRuntime.emptyArgs : new Object[] {value};
        // Delegate to "return" method. If it's not defined we ignore it
        Object retFnObj =
                ScriptRuntime.getObjectPropNoWarn(delegee, ES6Iterator.RETURN_METHOD, cx, scope);
        // Treat a return method that's null or undefined as if it doesn't exist
        // See https://tc39.es/ecma262/#sec-getmethod (2026, 7.3.10, 2)
        if (retFnObj != null && !Undefined.isUndefined(retFnObj)) {
            if (!(retFnObj instanceof Callable)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.isnt.function",
                        ES6Iterator.RETURN_METHOD,
                        ScriptRuntime.typeof(retFnObj));
            }
            return ((Callable) retFnObj).call(cx, scope, ensureScriptable(delegee), retArgs);
        }
        return null;
    }

    enum State {
        SUSPENDED_START,
        SUSPENDED_YIELD,
        EXECUTING,
        COMPLETED
    }

    public static final class YieldStarResult {
        private Object result;

        public YieldStarResult(Object result) {
            this.result = result;
        }

        Object getResult() {
            return result;
        }
    }
}
