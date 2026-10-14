/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import org.monflabs.mozilla.javascript.NativeAsyncFromSyncIterator.IteratorRecord;

/**
 * Array.fromAsync ( asyncItems [ , mapfn [ , thisArg ] ] ) (ECMA-262 23.1.2.2): an async closure
 * that awaits each value of an async iterator, a sync iterator or an array-like, and each result of
 * mapfn. Each step after an await runs in the reaction of the awaited promise.
 */
final class ArrayFromAsync {
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private final TopLevel scope;
    private final NativePromise.Capability capability;
    private Callable mapfn;
    private Object thisArg = Undefined.SCRIPTABLE_UNDEFINED;
    private Scriptable target;
    private IteratorRecord iterator;
    private Scriptable arrayLike;
    private long length;
    private long k;

    private ArrayFromAsync(TopLevel scope, NativePromise.Capability capability) {
        this.scope = scope;
        this.capability = capability;
    }

    static Object js_fromAsync(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        TopLevel top = ScriptableObject.getTopLevelScope(s);
        ArrayFromAsync state = new ArrayFromAsync(top, NativePromise.newCapability(cx, top));
        try {
            state.start(cx, f, thisObj, args);
        } catch (RhinoException e) {
            state.reject(cx, NativeAsyncFromSyncIterator.errorValue(cx, top, e));
        }
        return state.capability.promise;
    }

    private void start(Context cx, JSFunction f, Object constructor, Object[] args) {
        Object items = args.length > 0 ? args[0] : Undefined.instance;
        Object mapArg = args.length > 1 ? args[1] : Undefined.instance;
        if (!Undefined.isUndefined(mapArg)) {
            if (!(mapArg instanceof Callable)) {
                throw ScriptRuntime.notFunctionError(mapArg);
            }
            mapfn = (Callable) mapArg;
            if (args.length > 2) {
                thisArg = args[2];
            }
        }
        Callable asyncMethod = ScriptRuntime.getMethod(cx, scope, items, SymbolKey.ASYNC_ITERATOR);
        Callable syncMethod =
                asyncMethod == null
                        ? ScriptRuntime.getMethod(cx, scope, items, SymbolKey.ITERATOR)
                        : null;
        if (asyncMethod != null) {
            iterator =
                    NativeAsyncFromSyncIterator.iteratorFromMethod(cx, scope, items, asyncMethod);
        } else if (syncMethod != null) {
            iterator =
                    NativeAsyncFromSyncIterator.asyncFromSync(
                            scope,
                            NativeAsyncFromSyncIterator.iteratorFromMethod(
                                    cx, scope, items, syncMethod));
        }
        if (iterator != null) {
            target = NativeArray.callConstructorOrCreateArray(cx, f, scope, constructor, 0, false);
            stepIterator(cx);
        } else {
            arrayLike = ScriptRuntime.toObject(cx, scope, items);
            length = NativeArray.getLengthProperty(cx, arrayLike);
            if (!(constructor instanceof Constructable) && length > 4294967295L) {
                // ArrayCreate ( length )
                throw ScriptRuntime.rangeErrorById("msg.arraylength.bad");
            }
            target =
                    NativeArray.callConstructorOrCreateArray(
                            cx, f, scope, constructor, length, true);
            stepArrayLike(cx);
        }
    }

    /** The loop over an iterator: await the next result, map it, define it. */
    private void stepIterator(Context cx) {
        if (k >= MAX_SAFE_INTEGER) {
            closeThenReject(
                    cx,
                    NativeAsyncFromSyncIterator.errorValue(
                            cx, scope, ScriptRuntime.typeErrorById("msg.arraylength.too.big")));
            return;
        }
        if (!(iterator.nextMethod instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(iterator.nextMethod, ES6Iterator.NEXT_METHOD);
        }
        Object nextResult =
                ((Callable) iterator.nextMethod)
                        .call(cx, scope, iterator.iterator, ScriptRuntime.emptyArgs);
        await(
                cx,
                nextResult,
                (lcx, result) -> {
                    if (!ScriptRuntime.isObject(result)) {
                        throw ScriptRuntime.typeErrorById("msg.iterator.result.not.object");
                    }
                    Scriptable r = (Scriptable) result;
                    if (ScriptRuntime.toBoolean(get(r, ES6Iterator.DONE_PROPERTY))) {
                        NativeArray.setLengthProperty(lcx, target, k);
                        resolve(lcx, target);
                        return;
                    }
                    Object value = get(r, ES6Iterator.VALUE_PROPERTY);
                    if (mapfn == null) {
                        defineAndStep(lcx, value);
                        return;
                    }
                    Object mapped;
                    try {
                        mapped = mapfn.call(lcx, scope, thisArg, new Object[] {value, (double) k});
                    } catch (RhinoException e) {
                        closeThenReject(lcx, NativeAsyncFromSyncIterator.errorValue(lcx, scope, e));
                        return;
                    }
                    awaitThenClose(
                            lcx, mapped, (mcx, mappedValue) -> defineAndStep(mcx, mappedValue));
                },
                this::reject);
    }

    private void defineAndStep(Context cx, Object value) {
        try {
            ArrayLikeAbstractOperations.defineElem(cx, target, k, value);
        } catch (RhinoException e) {
            closeThenReject(cx, NativeAsyncFromSyncIterator.errorValue(cx, scope, e));
            return;
        }
        k++;
        stepIterator(cx);
    }

    /** The loop over an array-like: await each element and its mapping. */
    private void stepArrayLike(Context cx) {
        if (k >= length) {
            NativeArray.setLengthProperty(cx, target, length);
            resolve(cx, target);
            return;
        }
        Object kValue = NativeArray.getElem(cx, arrayLike, k);
        await(
                cx,
                kValue,
                (lcx, value) -> {
                    if (mapfn == null) {
                        defineNext(lcx, value);
                        return;
                    }
                    Object mapped =
                            mapfn.call(lcx, scope, thisArg, new Object[] {value, (double) k});
                    await(lcx, mapped, this::defineNext, this::reject);
                },
                this::reject);
    }

    private void defineNext(Context cx, Object value) {
        ArrayLikeAbstractOperations.defineElem(cx, target, k, value);
        k++;
        stepArrayLike(cx);
    }

    private interface Step {
        void run(Context cx, Object value);
    }

    /** Await ( value ): the step runs in the reaction; a throw of the step rejects. */
    private void await(Context cx, Object value, Step onFulfilled, Step onRejected) {
        NativePromise.await(
                cx,
                scope,
                value,
                (lcx, ls, t, args) -> {
                    try {
                        onFulfilled.run(lcx, args.length > 0 ? args[0] : Undefined.instance);
                    } catch (RhinoException e) {
                        reject(lcx, NativeAsyncFromSyncIterator.errorValue(lcx, scope, e));
                    }
                    return Undefined.instance;
                },
                (lcx, ls, t, args) -> {
                    onRejected.run(lcx, args.length > 0 ? args[0] : Undefined.instance);
                    return Undefined.instance;
                });
    }

    /**
     * An Await of the iterator loop: a rejection closes the iterator (IfAbruptCloseAsyncIterator).
     */
    private void awaitThenClose(Context cx, Object value, Step onFulfilled) {
        await(cx, value, onFulfilled, this::closeThenReject);
    }

    /**
     * AsyncIteratorClose ( iteratorRecord, completion ) (7.4.13) for a throw completion: the result
     * of return() is awaited, then the original reason rejects.
     */
    private void closeThenReject(Context cx, Object reason) {
        Object returnResult;
        try {
            Object returnMethod =
                    ScriptableObject.getProperty(iterator.iterator, ES6Iterator.RETURN_METHOD);
            if (!(returnMethod instanceof Callable)) {
                reject(cx, reason);
                return;
            }
            returnResult =
                    ((Callable) returnMethod)
                            .call(cx, scope, iterator.iterator, ScriptRuntime.emptyArgs);
        } catch (RhinoException ignored) {
            reject(cx, reason);
            return;
        }
        await(cx, returnResult, (lcx, v) -> reject(lcx, reason), (lcx, v) -> reject(lcx, reason));
    }

    private static Object get(Scriptable o, String name) {
        Object value = ScriptableObject.getProperty(o, name);
        return value == Scriptable.NOT_FOUND ? Undefined.instance : value;
    }

    private void resolve(Context cx, Object value) {
        capability.resolve.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {value});
    }

    private void reject(Context cx, Object reason) {
        capability.reject.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {reason});
    }
}
