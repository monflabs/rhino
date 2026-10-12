/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * The steps of a for await loop (ECMA-262 14.7.5.6-7) that await. The body of the async function or
 * generator yields a {@link Request} (see Node#AWAIT_KIND_PROP); the driver of the body runs it,
 * awaiting as the specification does, then resumes the body with its result or its error.
 */
final class AsyncIteration {
    private AsyncIteration() {}

    /** What the driver of an async body offers a request. */
    interface Driver {
        void resume(Context cx, Object value);

        void resumeThrow(Context cx, Object error);

        /** Await(value): one of the continuations runs from a promise job. */
        void await(Context cx, Object value, Continuation onValue, Continuation onError);

        Object errorValue(Context cx, RhinoException re);
    }

    interface Continuation {
        void run(Context cx, Object value);
    }

    /** GetIterator(obj, async), as the operand of ENUM_INIT_VALUES_IN_ORDER. */
    static final class Open {
        final NativeAsyncFromSyncIterator.IteratorRecord record;

        Open(NativeAsyncFromSyncIterator.IteratorRecord record) {
            this.record = record;
        }
    }

    /** The state of a for await loop, held by its enumeration. */
    static final class Loop {
        final NativeAsyncFromSyncIterator.IteratorRecord record;
        Object value;

        /** The iterator is done, or the loop is not in its body: it is not closed. */
        boolean inactive = true;

        Loop(NativeAsyncFromSyncIterator.IteratorRecord record) {
            this.record = record;
        }
    }

    abstract static class Request {
        abstract void run(Context cx, VarScope scope, Driver driver);
    }

    /** A step of the loop: the next result, awaited; the body resumes with whether it goes on. */
    static final class Next extends Request {
        private final Loop loop;

        Next(Loop loop) {
            this.loop = loop;
        }

        @Override
        void run(Context cx, VarScope scope, Driver driver) {
            loop.inactive = true;
            Object result;
            try {
                Object next = loop.record.nextMethod;
                if (!(next instanceof Callable)) {
                    throw ScriptRuntime.notFunctionError(next);
                }
                result =
                        ((Callable) next)
                                .call(cx, scope, loop.record.iterator, ScriptRuntime.emptyArgs);
            } catch (RhinoException re) {
                driver.resumeThrow(cx, driver.errorValue(cx, re));
                return;
            }
            driver.await(
                    cx,
                    result,
                    (c, r) -> {
                        try {
                            if (!ScriptRuntime.isObject(r)) {
                                throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
                            }
                            Scriptable res = (Scriptable) r;
                            if (ScriptRuntime.toBoolean(
                                    ScriptableObject.getProperty(res, ES6Iterator.DONE_PROPERTY))) {
                                driver.resume(c, Boolean.FALSE);
                                return;
                            }
                            Object v = ScriptableObject.getProperty(res, "value");
                            loop.value = v == Scriptable.NOT_FOUND ? Undefined.instance : v;
                        } catch (RhinoException re) {
                            driver.resumeThrow(c, driver.errorValue(c, re));
                            return;
                        }
                        loop.inactive = false;
                        driver.resume(c, Boolean.TRUE);
                    },
                    driver::resumeThrow);
        }
    }

    /**
     * AsyncIteratorClose (7.4.13) of a loop left by a jump, or by a throw (abrupt): after a throw,
     * the errors of the return method are ignored, and the body goes on rethrowing.
     */
    static final class Close extends Request {
        private final Loop loop;
        private final boolean abrupt;

        Close(Loop loop, boolean abrupt) {
            this.loop = loop;
            this.abrupt = abrupt;
        }

        @Override
        void run(Context cx, VarScope scope, Driver driver) {
            if (loop == null || loop.inactive) {
                driver.resume(cx, Undefined.instance);
                return;
            }
            loop.inactive = true;
            Object result;
            try {
                Callable method =
                        ScriptRuntime.getMethod(cx, scope, loop.record.iterator, "return");
                if (method == null) {
                    driver.resume(cx, Undefined.instance);
                    return;
                }
                result = method.call(cx, scope, loop.record.iterator, ScriptRuntime.emptyArgs);
            } catch (RhinoException re) {
                if (abrupt) {
                    driver.resume(cx, Undefined.instance);
                } else {
                    driver.resumeThrow(cx, driver.errorValue(cx, re));
                }
                return;
            }
            driver.await(
                    cx,
                    result,
                    (c, r) -> {
                        if (!abrupt && !ScriptRuntime.isObject(r)) {
                            driver.resumeThrow(
                                    c,
                                    driver.errorValue(
                                            c,
                                            ScriptRuntime.typeErrorById(
                                                    "msg.iterator.result.not.object")));
                        } else {
                            driver.resume(c, Undefined.instance);
                        }
                    },
                    (c, e) -> {
                        if (abrupt) {
                            driver.resume(c, Undefined.instance);
                        } else {
                            driver.resumeThrow(c, e);
                        }
                    });
        }
    }
}
