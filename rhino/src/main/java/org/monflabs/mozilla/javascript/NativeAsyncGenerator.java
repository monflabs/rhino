/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.Symbol.Kind.REGULAR;

import java.io.Serial;
import java.util.ArrayDeque;

/**
 * An async generator object (ECMA-262 27.6). Its body is compiled as a generator: a yield of an
 * {@link AwaitRequest} is an await, any other yield is a yield. The requests made by next, return
 * and throw are queued (AsyncGeneratorEnqueue) and served one at a time: the body runs until it
 * yields or completes, and the promise of the request at the head of the queue settles then.
 */
final class NativeAsyncGenerator extends ScriptableObject {
    @Serial private static final long serialVersionUID = 1L;

    static final SymbolKey ASYNC_GENERATOR_TAG = new SymbolKey("AsyncGeneratorPrototype", REGULAR);
    static final SymbolKey ASYNC_ITERATOR_TAG = new SymbolKey("AsyncIteratorPrototype", REGULAR);

    private static final int NEXT = 0;
    private static final int RETURN = 1;
    private static final int THROW = 2;

    private static final ClassDescriptor ASYNC_ITERATOR_DESCRIPTOR;
    private static final ClassDescriptor DESCRIPTOR;

    static {
        ASYNC_ITERATOR_DESCRIPTOR =
                new ClassDescriptor.Builder(ASYNC_ITERATOR_TAG)
                        .withMethod(
                                CTOR,
                                SymbolKey.ASYNC_ITERATOR,
                                0,
                                (cx, f, nt, s, thisObj, args) -> thisObj)
                        .build();
        DESCRIPTOR =
                new ClassDescriptor.Builder(ASYNC_GENERATOR_TAG)
                        .withMethod(
                                CTOR, "next", 1, (cx, f, nt, s, t, a) -> enqueue(cx, s, t, a, NEXT))
                        .withMethod(
                                CTOR,
                                "return",
                                1,
                                (cx, f, nt, s, t, a) -> enqueue(cx, s, t, a, RETURN))
                        .withMethod(
                                CTOR,
                                "throw",
                                1,
                                (cx, f, nt, s, t, a) -> enqueue(cx, s, t, a, THROW))
                        .withProp(
                                CTOR,
                                SymbolKey.TO_STRING_TAG,
                                value("AsyncGenerator", DONTENUM | READONLY))
                        .build();
    }

    /** What an await in the body of an async generator yields (Token#AWAIT). */
    static final class AwaitRequest {
        final Object value;

        AwaitRequest(Object value) {
            this.value = value;
        }
    }

    private static final class Request {
        final int kind;
        final Object value;
        final NativePromise.Capability capability;

        Request(int kind, Object value, NativePromise.Capability capability) {
            this.kind = kind;
            this.value = value;
            this.capability = capability;
        }
    }

    /** What a yield* in the body yields: the async iterator it delegates to. */
    static final class DelegateRequest {
        final NativeAsyncFromSyncIterator.IteratorRecord iterator;

        DelegateRequest(NativeAsyncFromSyncIterator.IteratorRecord iterator) {
            this.iterator = iterator;
        }
    }

    private final ES6Generator body;

    /** The iterator of the yield* the body is suspended in, or null. */
    private NativeAsyncFromSyncIterator.IteratorRecord delegate;

    private final VarScope scope;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private boolean started;
    private boolean done;

    /** Whether the body is running or awaiting for the request at the head of the queue. */
    private boolean busy;

    /** Creates %AsyncIteratorPrototype% and %AsyncGeneratorPrototype% (27.1.3, 27.6.1). */
    static void init(Context cx, TopLevel scope, boolean sealed) {
        NativeObject asyncIteratorPrototype = new NativeObject();
        ASYNC_ITERATOR_DESCRIPTOR.populateGlobal(cx, scope, asyncIteratorPrototype, sealed);
        asyncIteratorPrototype.setPrototype(getObjectPrototype(scope));
        scope.associateValue(ASYNC_ITERATOR_TAG, asyncIteratorPrototype);

        NativeObject prototype = new NativeObject();
        DESCRIPTOR.populateGlobal(cx, scope, prototype, sealed);
        prototype.setPrototype(asyncIteratorPrototype);
        scope.associateValue(ASYNC_GENERATOR_TAG, prototype);
    }

    private NativeAsyncGenerator(VarScope scope, ES6Generator body, JSFunction function) {
        this.body = body;
        this.scope = scope;
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        setParentScope(top);
        Object functionPrototype = ScriptableObject.getProperty(function, "prototype");
        setPrototype(
                functionPrototype instanceof Scriptable
                        ? (Scriptable) functionPrototype
                        : (Scriptable) ScriptableObject.getTopScopeValue(top, ASYNC_GENERATOR_TAG));
    }

    /** The object a call of an async generator function returns: its body has not started. */
    static NativeAsyncGenerator create(VarScope scope, ES6Generator body, JSFunction function) {
        return new NativeAsyncGenerator(scope, body, function);
    }

    @Override
    public String getClassName() {
        return "AsyncGenerator";
    }

    /** AsyncGeneratorEnqueue (27.6.3.2), after AsyncGeneratorValidate rejects a bad receiver. */
    private static Object enqueue(Context cx, VarScope s, Object thisObj, Object[] args, int kind) {
        NativePromise.Capability capability = NativePromise.newCapability(cx, s);
        Object value = args.length > 0 ? args[0] : Undefined.instance;
        if (!(thisObj instanceof NativeAsyncGenerator)) {
            Object error =
                    ScriptRuntime.wrapException(
                            ScriptRuntime.typeErrorById(
                                    "msg.incompat.call",
                                    kind == NEXT ? "next" : kind == RETURN ? "return" : "throw"),
                            s,
                            cx);
            call(cx, s, capability.reject, error);
            return capability.promise;
        }
        NativeAsyncGenerator generator = (NativeAsyncGenerator) thisObj;
        generator.queue.add(new Request(kind, value, capability));
        if (!generator.busy) {
            generator.drain(cx);
        }
        return capability.promise;
    }

    /** Serves the requests at the head of the queue until it is empty or the body awaits. */
    private void drain(Context cx) {
        while (!busy && !queue.isEmpty()) {
            Request request = queue.peek();
            if (!started && request.kind != NEXT) {
                // return or throw before the body started: it never runs (27.6.3.9)
                done = true;
            }
            if (done) {
                settleCompleted(cx, request);
                continue;
            }
            busy = true;
            if (!started) {
                started = true;
                resume(cx, NativeGenerator.GENERATOR_SEND, request.value);
            } else if (delegate != null) {
                if (request.kind == RETURN) {
                    awaitThen(
                            cx,
                            request.value,
                            (c, v) -> delegateStep(c, RETURN, v),
                            (c, e) -> delegateStep(c, THROW, e));
                } else {
                    delegateStep(cx, request.kind, request.value);
                }
            } else if (request.kind == RETURN) {
                // AsyncGeneratorUnwrapYieldResumption: a return awaits its value first
                awaitThen(
                        cx,
                        request.value,
                        (c, v) -> resume(c, NativeGenerator.GENERATOR_CLOSE, v),
                        (c, e) -> resume(c, NativeGenerator.GENERATOR_THROW, e));
            } else {
                resume(
                        cx,
                        request.kind == THROW
                                ? NativeGenerator.GENERATOR_THROW
                                : NativeGenerator.GENERATOR_SEND,
                        request.value);
            }
        }
    }

    /** A request to a completed generator (AsyncGeneratorDrainQueue, 27.6.3.10). */
    private void settleCompleted(Context cx, Request request) {
        if (request.kind == RETURN) {
            // AsyncGeneratorAwaitReturn: the queue waits for the awaited value
            queue.poll();
            busy = true;
            awaitThen(
                    cx,
                    request.value,
                    (c, v) -> {
                        busy = false;
                        call(c, scope, request.capability.resolve, result(c, v, true));
                        drain(c);
                    },
                    (c, e) -> {
                        busy = false;
                        call(c, scope, request.capability.reject, e);
                        drain(c);
                    });
            return;
        }
        queue.poll();
        if (request.kind == THROW) {
            call(cx, scope, request.capability.reject, request.value);
        } else {
            call(cx, scope, request.capability.resolve, result(cx, Undefined.instance, true));
        }
    }

    /** Runs the body until it awaits, yields or completes. */
    private void resume(Context cx, int op, Object value) {
        Scriptable step;
        try {
            step =
                    op == NativeGenerator.GENERATOR_SEND
                            ? body.resumeLocal(cx, scope, value)
                            : body.resumeAbruptLocal(cx, scope, op, value);
        } catch (RhinoException re) {
            Request request = queue.poll();
            done = true;
            busy = false;
            call(cx, scope, request.capability.reject, errorValue(cx, re));
            drain(cx);
            return;
        }
        Object yielded = ScriptableObject.getProperty(step, ES6Iterator.VALUE_PROPERTY);
        if (ScriptRuntime.toBoolean(
                ScriptableObject.getProperty(step, ES6Iterator.DONE_PROPERTY))) {
            Request request = queue.poll();
            done = true;
            busy = false;
            call(cx, scope, request.capability.resolve, result(cx, yielded, true));
            drain(cx);
            return;
        }
        if (yielded instanceof AsyncIteration.Request) {
            // a step of a for await loop
            ((AsyncIteration.Request) yielded).run(cx, scope, iterationDriver);
            return;
        }
        if (yielded instanceof DelegateRequest) {
            delegate = ((DelegateRequest) yielded).iterator;
            delegateStep(cx, NEXT, Undefined.instance);
            return;
        }
        if (yielded instanceof AwaitRequest) {
            awaitThen(
                    cx,
                    ((AwaitRequest) yielded).value,
                    (c, v) -> resume(c, NativeGenerator.GENERATOR_SEND, v),
                    (c, e) -> resume(c, NativeGenerator.GENERATOR_THROW, e));
            return;
        }
        // AsyncGeneratorYield: the value is awaited, then produced
        awaitThen(
                cx,
                yielded,
                (c, v) -> {
                    Request request = queue.poll();
                    busy = false;
                    call(c, scope, request.capability.resolve, result(c, v, false));
                    drain(c);
                },
                (c, e) -> resume(c, NativeGenerator.GENERATOR_THROW, e));
    }

    /**
     * A step of the delegation of a yield* (14.4.14, async): the request is forwarded to the inner
     * iterator, whose awaited result is either yielded as is or ends the delegation.
     */
    private void delegateStep(Context cx, int kind, Object value) {
        NativeAsyncFromSyncIterator.IteratorRecord inner = delegate;
        Object innerResult;
        try {
            if (kind == NEXT) {
                if (!(inner.nextMethod instanceof Callable)) {
                    throw ScriptRuntime.notFunctionError(inner.nextMethod);
                }
                innerResult =
                        ((Callable) inner.nextMethod)
                                .call(cx, scope, inner.iterator, new Object[] {value});
            } else if (kind == THROW) {
                Callable method = ScriptRuntime.getMethod(cx, scope, inner.iterator, "throw");
                if (method == null) {
                    // the inner iterator cannot take a throw: it is closed, then a TypeError
                    delegate = null;
                    closeThen(cx, inner);
                    return;
                }
                innerResult = method.call(cx, scope, inner.iterator, new Object[] {value});
            } else {
                Callable method = ScriptRuntime.getMethod(cx, scope, inner.iterator, "return");
                if (method == null) {
                    // the return completion of the yield* awaits its value (again)
                    delegate = null;
                    awaitThen(
                            cx,
                            value,
                            (c, v) -> resume(c, NativeGenerator.GENERATOR_CLOSE, v),
                            (c, e) -> resume(c, NativeGenerator.GENERATOR_THROW, e));
                    return;
                }
                innerResult = method.call(cx, scope, inner.iterator, new Object[] {value});
            }
        } catch (RhinoException re) {
            delegate = null;
            resume(cx, NativeGenerator.GENERATOR_THROW, errorValue(cx, re));
            return;
        }
        awaitThen(
                cx,
                innerResult,
                (c, result) -> {
                    Object v;
                    boolean done;
                    try {
                        if (!ScriptRuntime.isObject(result)) {
                            throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
                        }
                        done =
                                ScriptRuntime.toBoolean(
                                        ScriptableObject.getProperty(
                                                (Scriptable) result, ES6Iterator.DONE_PROPERTY));
                        v = ScriptableObject.getProperty((Scriptable) result, "value");
                        if (v == Scriptable.NOT_FOUND) {
                            v = Undefined.instance;
                        }
                    } catch (RhinoException re) {
                        delegate = null;
                        resume(c, NativeGenerator.GENERATOR_THROW, errorValue(c, re));
                        return;
                    }
                    if (done) {
                        delegate = null;
                        resume(
                                c,
                                kind == RETURN
                                        ? NativeGenerator.GENERATOR_CLOSE
                                        : NativeGenerator.GENERATOR_SEND,
                                v);
                        return;
                    }
                    // the value of the inner iterator is yielded as is
                    Request request = queue.poll();
                    busy = false;
                    call(c, scope, request.capability.resolve, result(c, v, false));
                    drain(c);
                },
                (c, e) -> {
                    delegate = null;
                    resume(c, NativeGenerator.GENERATOR_THROW, e);
                });
    }

    /** AsyncIteratorClose of an inner iterator that has no throw method, then a TypeError. */
    private void closeThen(Context cx, NativeAsyncFromSyncIterator.IteratorRecord inner) {
        Object innerResult;
        try {
            Callable method = ScriptRuntime.getMethod(cx, scope, inner.iterator, "return");
            if (method == null) {
                resume(cx, NativeGenerator.GENERATOR_THROW, noThrowError(cx));
                return;
            }
            innerResult = method.call(cx, scope, inner.iterator, ScriptRuntime.emptyArgs);
        } catch (RhinoException re) {
            resume(cx, NativeGenerator.GENERATOR_THROW, errorValue(cx, re));
            return;
        }
        awaitThen(
                cx,
                innerResult,
                (c, result) -> {
                    Object error =
                            ScriptRuntime.isObject(result)
                                    ? noThrowError(c)
                                    : errorValue(
                                            c,
                                            ScriptRuntime.typeErrorById(
                                                    "msg.iterator.result.not.object"));
                    resume(c, NativeGenerator.GENERATOR_THROW, error);
                },
                (c, e) -> resume(c, NativeGenerator.GENERATOR_THROW, e));
    }

    private Object noThrowError(Context cx) {
        return errorValue(cx, ScriptRuntime.typeErrorById("msg.iterator.no.throw"));
    }

    private final AsyncIteration.Driver iterationDriver =
            new AsyncIteration.Driver() {
                @Override
                public void resume(Context cx, Object value) {
                    NativeAsyncGenerator.this.resume(cx, NativeGenerator.GENERATOR_SEND, value);
                }

                @Override
                public void resumeThrow(Context cx, Object error) {
                    NativeAsyncGenerator.this.resume(cx, NativeGenerator.GENERATOR_THROW, error);
                }

                @Override
                public void await(
                        Context cx,
                        Object value,
                        AsyncIteration.Continuation onValue,
                        AsyncIteration.Continuation onError) {
                    awaitThen(cx, value, onValue::run, onError::run);
                }

                @Override
                public Object errorValue(Context cx, RhinoException re) {
                    return NativeAsyncGenerator.this.errorValue(cx, re);
                }
            };

    private interface Continuation {
        void run(Context cx, Object value);
    }

    /** Await(value): the continuation runs from a promise job, within a top call. */
    private void awaitThen(Context cx, Object value, Continuation onValue, Continuation onError) {
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
            // PromiseResolve threw (a poisoned constructor): a throw completion of the await
            onError.run(cx, errorValue(cx, re));
        }
    }

    private void inTopCall(Context cx, Continuation continuation, Object value) {
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
                body.isStrict());
    }

    private Object errorValue(Context cx, RhinoException re) {
        return re instanceof JavaScriptException
                ? ((JavaScriptException) re).getValue()
                : ScriptRuntime.wrapException(re, scope, cx);
    }

    private Scriptable result(Context cx, Object value, boolean isDone) {
        return ES6Iterator.makeIteratorResult(cx, scope, isDone, value);
    }

    private static void call(Context cx, VarScope scope, Callable f, Object arg) {
        f.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {arg});
    }
}
