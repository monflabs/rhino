/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.Symbol.Kind.REGULAR;

import java.io.Serial;

/**
 * An Async-from-Sync Iterator object (ECMA-262 27.1.6): the async iterator over a sync iterator
 * that GetIterator(obj, async) returns when obj has no @@asyncIterator method. Its methods settle a
 * promise with the results of the sync iterator, whose values are awaited.
 */
final class NativeAsyncFromSyncIterator extends ScriptableObject {
    @Serial private static final long serialVersionUID = 1L;

    static final SymbolKey TAG = new SymbolKey("AsyncFromSyncIteratorPrototype", REGULAR);

    private static final ClassDescriptor DESCRIPTOR =
            new ClassDescriptor.Builder(TAG)
                    .withMethod(CTOR, "next", 1, NativeAsyncFromSyncIterator::js_next)
                    .withMethod(CTOR, "return", 1, NativeAsyncFromSyncIterator::js_return)
                    .withMethod(CTOR, "throw", 1, NativeAsyncFromSyncIterator::js_throw)
                    .build();

    /** An iterator record: the iterator and its next method (7.4.1). */
    static final class IteratorRecord {
        final Scriptable iterator;
        final Object nextMethod;

        IteratorRecord(Scriptable iterator, Object nextMethod) {
            this.iterator = iterator;
            this.nextMethod = nextMethod;
        }
    }

    private final IteratorRecord syncIterator;

    private NativeAsyncFromSyncIterator(IteratorRecord syncIterator) {
        this.syncIterator = syncIterator;
    }

    @Override
    public String getClassName() {
        return "Object";
    }

    /** Creates %AsyncFromSyncIteratorPrototype%, after %AsyncIteratorPrototype%. */
    static void init(Context cx, TopLevel scope, boolean sealed) {
        NativeObject prototype = new NativeObject();
        DESCRIPTOR.populateGlobal(cx, scope, prototype, sealed);
        prototype.setPrototype(
                (Scriptable)
                        ScriptableObject.getTopScopeValue(
                                scope, NativeAsyncGenerator.ASYNC_ITERATOR_TAG));
        scope.associateValue(TAG, prototype);
    }

    /** GetIterator ( obj, async ) (7.4.3). */
    static IteratorRecord getAsyncIterator(Context cx, VarScope scope, Object obj) {
        Object method = ScriptRuntime.getMethod(cx, scope, obj, SymbolKey.ASYNC_ITERATOR);
        if (method == null) {
            Object syncMethod = ScriptRuntime.getMethod(cx, scope, obj, SymbolKey.ITERATOR);
            if (syncMethod == null) {
                throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(obj));
            }
            return asyncFromSync(scope, iteratorFromMethod(cx, scope, obj, syncMethod));
        }
        return iteratorFromMethod(cx, scope, obj, method);
    }

    /** CreateAsyncFromSyncIterator ( syncIteratorRecord ) (27.1.6.1). */
    static IteratorRecord asyncFromSync(VarScope scope, IteratorRecord sync) {
        NativeAsyncFromSyncIterator async = new NativeAsyncFromSyncIterator(sync);
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        async.setParentScope(top);
        async.setPrototype((Scriptable) ScriptableObject.getTopScopeValue(top, TAG));
        return new IteratorRecord(
                async, ScriptableObject.getProperty(async, ES6Iterator.NEXT_METHOD));
    }

    /** GetIteratorFromMethod ( obj, method ) (7.4.2). */
    static IteratorRecord iteratorFromMethod(
            Context cx, VarScope scope, Object obj, Object method) {
        if (!(method instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(method);
        }
        Object iterator =
                ((Callable) method)
                        .call(
                                cx,
                                scope,
                                ScriptRuntime.toObjectOrNull(cx, obj, scope),
                                ScriptRuntime.emptyArgs);
        if (!(iterator instanceof Scriptable) || !ScriptRuntime.isObject(iterator)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
        }
        Object next = ScriptableObject.getProperty((Scriptable) iterator, ES6Iterator.NEXT_METHOD);
        return new IteratorRecord(
                (Scriptable) iterator, next == Scriptable.NOT_FOUND ? Undefined.instance : next);
    }

    private static Object call(
            Context cx, VarScope scope, Object fn, Scriptable thisObj, Object[] args) {
        if (!(fn instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(fn);
        }
        return ((Callable) fn).call(cx, scope, thisObj, args);
    }

    private static Object[] optionalArg(Object[] args) {
        return args.length > 0 ? new Object[] {args[0]} : ScriptRuntime.emptyArgs;
    }

    static Object errorValue(Context cx, VarScope scope, RhinoException re) {
        return re instanceof JavaScriptException
                ? ((JavaScriptException) re).getValue()
                : ScriptRuntime.wrapException(re, scope, cx);
    }

    private static void settle(Context cx, VarScope scope, Callable f, Object arg) {
        f.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {arg});
    }

    /** %AsyncFromSyncIteratorPrototype%.next ( [ value ] ) (27.1.6.2.1). */
    private static Object js_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeAsyncFromSyncIterator self = (NativeAsyncFromSyncIterator) thisObj;
        NativePromise.Capability capability = NativePromise.newCapability(cx, s);
        Object result;
        try {
            result =
                    call(
                            cx,
                            s,
                            self.syncIterator.nextMethod,
                            self.syncIterator.iterator,
                            optionalArg(args));
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
            }
        } catch (RhinoException re) {
            settle(cx, s, capability.reject, errorValue(cx, s, re));
            return capability.promise;
        }
        return continuation(cx, s, (Scriptable) result, capability, self.syncIterator, true);
    }

    /** %AsyncFromSyncIteratorPrototype%.return ( [ value ] ) (27.1.6.2.2). */
    private static Object js_return(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeAsyncFromSyncIterator self = (NativeAsyncFromSyncIterator) thisObj;
        NativePromise.Capability capability = NativePromise.newCapability(cx, s);
        Object result;
        try {
            Object method = ScriptRuntime.getMethod(cx, s, self.syncIterator.iterator, "return");
            if (method == null) {
                Object value = args.length > 0 ? args[0] : Undefined.instance;
                settle(
                        cx,
                        s,
                        capability.resolve,
                        ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE, value));
                return capability.promise;
            }
            result = call(cx, s, method, self.syncIterator.iterator, optionalArg(args));
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
            }
        } catch (RhinoException re) {
            settle(cx, s, capability.reject, errorValue(cx, s, re));
            return capability.promise;
        }
        return continuation(cx, s, (Scriptable) result, capability, self.syncIterator, false);
    }

    /** %AsyncFromSyncIteratorPrototype%.throw ( [ value ] ) (27.1.6.2.3). */
    private static Object js_throw(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeAsyncFromSyncIterator self = (NativeAsyncFromSyncIterator) thisObj;
        NativePromise.Capability capability = NativePromise.newCapability(cx, s);
        Object result;
        try {
            Object method = ScriptRuntime.getMethod(cx, s, self.syncIterator.iterator, "throw");
            if (method == null) {
                // the delegation protocol was violated: close the iterator, then reject
                closeSync(cx, s, self.syncIterator);
                throw ScriptRuntime.typeErrorById("msg.iterator.no.throw");
            }
            result = call(cx, s, method, self.syncIterator.iterator, optionalArg(args));
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
            }
        } catch (RhinoException re) {
            settle(cx, s, capability.reject, errorValue(cx, s, re));
            return capability.promise;
        }
        return continuation(cx, s, (Scriptable) result, capability, self.syncIterator, true);
    }

    /** IteratorClose of the sync iterator with a normal completion. */
    private static void closeSync(Context cx, VarScope scope, IteratorRecord record) {
        Object method = ScriptRuntime.getMethod(cx, scope, record.iterator, "return");
        if (method != null) {
            Object result = call(cx, scope, method, record.iterator, ScriptRuntime.emptyArgs);
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
            }
        }
    }

    /** AsyncFromSyncIteratorContinuation ( result, promiseCapability, ... ) (27.1.6.4). */
    private static Object continuation(
            Context cx,
            VarScope scope,
            Scriptable result,
            NativePromise.Capability capability,
            IteratorRecord syncIterator,
            boolean closeOnRejection) {
        boolean done;
        Object value;
        try {
            done = ScriptRuntime.toBoolean(ScriptableObject.getProperty(result, "done"));
            value = ScriptableObject.getProperty(result, "value");
            if (value == Scriptable.NOT_FOUND) {
                value = Undefined.instance;
            }
        } catch (RhinoException re) {
            settle(cx, scope, capability.reject, errorValue(cx, scope, re));
            return capability.promise;
        }
        Callable onRejected =
                (lcx, ls, t, a) -> {
                    if (!done && closeOnRejection) {
                        // IteratorClose with a throw completion: the rejection wins
                        try {
                            closeSync(lcx, scope, syncIterator);
                        } catch (RhinoException ignored) {
                            // ignored
                        }
                    }
                    throw new JavaScriptException(a[0], null, 0);
                };
        try {
            NativePromise.then(
                    cx,
                    scope,
                    value,
                    (lcx, ls, t, a) -> ES6Iterator.makeIteratorResult(lcx, scope, done, a[0]),
                    onRejected,
                    capability);
        } catch (RhinoException re) {
            // PromiseResolve threw: the iterator is closed if the value was not the last one
            Object error = errorValue(cx, scope, re);
            if (!done && closeOnRejection) {
                try {
                    closeSync(cx, scope, syncIterator);
                } catch (RhinoException ignored) {
                    // the error of PromiseResolve wins
                }
            }
            settle(cx, scope, capability.reject, error);
        }
        return capability.promise;
    }
}
