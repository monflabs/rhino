/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * Runs the body of an async function (ECMA-262 27.7.5.1 AsyncFunctionStart). The body is compiled
 * as a generator whose yields are the awaits: the driver runs it up to its first await before the
 * call returns, then resumes it from the reactions to each awaited promise, and settles the promise
 * that the call returned with the completion of the body.
 */
final class AsyncFunctionDriver {
    private final ES6Generator generator;
    private final NativePromise.Capability capability;
    private final VarScope scope;

    private AsyncFunctionDriver(
            ES6Generator generator, NativePromise.Capability capability, VarScope scope) {
        this.generator = generator;
        this.capability = capability;
        this.scope = scope;
    }

    /** Starts the body of an async function and returns its promise. */
    static Object start(Context cx, VarScope scope, ES6Generator generator) {
        NativePromise.Capability capability = NativePromise.newCapability(cx, scope);
        AsyncFunctionDriver driver = new AsyncFunctionDriver(generator, capability, scope);
        // the call that created the generator is still the current activation: the body enters it
        // again, so it is popped while the body runs
        NativeCall creating = cx.currentActivationCall;
        boolean pop = creating != null && generator.isBodyOf(creating.function);
        NativeCall parent = pop ? creating.parentActivationCall : null;
        if (pop) {
            cx.currentActivationCall = parent;
        }
        try {
            driver.step(cx, NativeGenerator.GENERATOR_SEND, Undefined.instance);
        } finally {
            if (pop) {
                creating.parentActivationCall = parent;
                cx.currentActivationCall = creating;
            }
        }
        return capability.promise;
    }

    /**
     * Evaluates the body of a module with a top-level await (16.2.1.6.5 ExecuteModule with a
     * capability): an async generator, already instantiated, whose awaits are AwaitRequests.
     */
    static Object runModule(Context cx, VarScope scope, ES6Generator generator) {
        NativePromise.Capability capability = NativePromise.newCapability(cx, scope);
        new AsyncFunctionDriver(generator, capability, scope)
                .step(cx, NativeGenerator.GENERATOR_SEND, Undefined.instance);
        return capability.promise;
    }

    private final AsyncIteration.Driver iterationDriver =
            new AsyncIteration.Driver() {
                @Override
                public void resume(Context cx, Object value) {
                    step(cx, NativeGenerator.GENERATOR_SEND, value);
                }

                @Override
                public void resumeThrow(Context cx, Object error) {
                    step(cx, NativeGenerator.GENERATOR_THROW, error);
                }

                @Override
                public void await(
                        Context cx,
                        Object value,
                        AsyncIteration.Continuation onValue,
                        AsyncIteration.Continuation onError) {
                    try {
                        NativePromise.await(
                                cx,
                                scope,
                                value,
                                (lcx, ls, t, args) -> {
                                    inTopCall(lcx, onValue, args[0]);
                                    return Undefined.instance;
                                },
                                (lcx, ls, t, args) -> {
                                    inTopCall(lcx, onError, args[0]);
                                    return Undefined.instance;
                                });
                    } catch (RhinoException re) {
                        onError.run(cx, errorValue(cx, re));
                    }
                }

                @Override
                public Object errorValue(Context cx, RhinoException re) {
                    return re instanceof JavaScriptException
                            ? ((JavaScriptException) re).getValue()
                            : ScriptRuntime.wrapException(re, scope, cx);
                }
            };

    private void inTopCall(Context cx, AsyncIteration.Continuation continuation, Object value) {
        if (ScriptRuntime.hasTopCall(cx)) {
            continuation.run(cx, value);
            return;
        }
        ScriptRuntime.doTopCall(
                (lcx, ls, t, args) -> {
                    continuation.run(lcx, value);
                    return Undefined.instance;
                },
                cx,
                scope,
                Undefined.SCRIPTABLE_UNDEFINED,
                ScriptRuntime.emptyArgs,
                generator.isStrict());
    }

    private void step(Context cx, int op, Object value) {
        if (!ScriptRuntime.hasTopCall(cx)) {
            // resumed by a promise job, which runs outside of any script
            ScriptRuntime.doTopCall(
                    (lcx, lscope, thisObj, args) -> {
                        step(lcx, op, value);
                        return Undefined.instance;
                    },
                    cx,
                    scope,
                    Undefined.SCRIPTABLE_UNDEFINED,
                    ScriptRuntime.emptyArgs,
                    generator.isStrict());
            return;
        }
        Scriptable result;
        try {
            result =
                    op == NativeGenerator.GENERATOR_SEND
                            ? generator.resumeLocal(cx, scope, value)
                            : generator.resumeAbruptLocal(cx, scope, op, value);
        } catch (RhinoException re) {
            Object error =
                    re instanceof JavaScriptException
                            ? ((JavaScriptException) re).getValue()
                            : ScriptRuntime.wrapException(re, scope, cx);
            capability.reject.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {error});
            return;
        }
        Object awaited = ScriptableObject.getProperty(result, ES6Iterator.VALUE_PROPERTY);
        if (awaited instanceof NativeAsyncGenerator.AwaitRequest) {
            // the await of a module body
            awaited = ((NativeAsyncGenerator.AwaitRequest) awaited).value;
        }
        if (ScriptRuntime.toBoolean(
                ScriptableObject.getProperty(result, ES6Iterator.DONE_PROPERTY))) {
            capability.resolve.call(
                    cx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {awaited});
            return;
        }
        if (awaited instanceof AsyncIteration.Request) {
            // a step of a for await loop
            ((AsyncIteration.Request) awaited).run(cx, scope, iterationDriver);
            return;
        }
        NativePromise.await(
                cx,
                scope,
                awaited,
                (lcx, lscope, thisObj, args) -> {
                    step(lcx, NativeGenerator.GENERATOR_SEND, args[0]);
                    return Undefined.instance;
                },
                (lcx, lscope, thisObj, args) -> {
                    step(lcx, NativeGenerator.GENERATOR_THROW, args[0]);
                    return Undefined.instance;
                });
    }
}
