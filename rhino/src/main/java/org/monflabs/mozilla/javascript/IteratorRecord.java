/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.List;

/**
 * An iterator record (ECMA-262 7.4.1) as used by array destructuring: GetIterator,
 * IteratorStepValue and IteratorClose. An array whose iteration is not observable (the original
 * Array.prototype[Symbol.iterator] and %ArrayIteratorPrototype%.next) is read by index, which gives
 * the same result without creating an iterator.
 */
final class IteratorRecord {
    private final Scriptable iterator;
    private final Object nextMethod;
    private final Scriptable array;

    /** A built-in iterator with its original next method, stepped without result objects. */
    private final ES6Iterator builtIn;

    private long index;
    private boolean done;

    /** The record opened before this one in the same guarded statement, see #guard. */
    private IteratorRecord previous;

    private IteratorRecord(
            Scriptable iterator, Object nextMethod, Scriptable array, ES6Iterator builtIn) {
        this.iterator = iterator;
        this.nextMethod = nextMethod;
        this.array = array;
        this.builtIn = builtIn;
    }

    /** GetIterator(value, sync). */
    static IteratorRecord open(Object value, Context cx, VarScope scope) {
        if (value != null
                && value.getClass() == NativeArray.class
                && iteratesByIndex((NativeArray) value, scope)) {
            return new IteratorRecord(null, null, (Scriptable) value, null);
        }
        if (value == null || Undefined.isUndefined(value)) {
            throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(value));
        }
        Scriptable object = ScriptRuntime.toObject(cx, scope, value);
        if (!(object instanceof ScriptableObject)
                && (!(object instanceof SymbolScriptable)
                        || !ScriptableObject.hasProperty(object, SymbolKey.ITERATOR))) {
            // a host object that does not implement symbol keys, or has no Symbol.iterator
            throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(value));
        }
        Object method = ScriptableObject.getProperty(object, SymbolKey.ITERATOR);
        if (method instanceof JSFunction
                && ((JSFunction) method).getDescriptor() == NativeArray.VALUES_DESCRIPTOR
                && value instanceof Scriptable
                && hasOriginalArrayIteratorNext(scope)) {
            return new IteratorRecord(null, null, (Scriptable) value, null);
        }
        if (!(method instanceof Callable)) {
            throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(value));
        }
        Object iterator = ((Callable) method).call(cx, scope, object, ScriptRuntime.emptyArgs);
        if (!(iterator instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.not.object", "[Symbol.iterator]");
        }
        Scriptable it = (Scriptable) iterator;
        Object next = ScriptableObject.getProperty(it, "next");
        // iterator results are not observable before VERSION_ECMASCRIPT only
        boolean builtIn =
                it instanceof ES6Iterator
                        && ES6Iterator.isNextMethodOf(next, (ES6Iterator) it)
                        && cx.isLatestEcmaScript()
                        && ScriptRuntime.hasTopCall(cx);
        return new IteratorRecord(it, next, null, builtIn ? (ES6Iterator) it : null);
    }

    /**
     * Whether an array inherits the original iteration methods of the realm of scope: its iterator
     * reads it by index. The slots of these methods are cached in the realm and checked in place.
     */
    private static boolean iteratesByIndex(NativeArray array, VarScope scope) {
        Scriptable proto = array.getPrototype();
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        ArrayIteration iteration = top.arrayIteration;
        if (iteration == null || iteration.arrayPrototype != proto || !iteration.isIntact()) {
            iteration = ArrayIteration.of(proto, top);
            if (iteration == null) {
                return false;
            }
            top.arrayIteration = iteration;
        }
        return array.getMap().query(SymbolKey.ITERATOR, 0) == null;
    }

    /**
     * The original Array.prototype[Symbol.iterator] and %ArrayIteratorPrototype%.next of a realm,
     * with the slots that hold them: they are unchanged while the slots are still in their maps
     * ({@link Slot#stale}) and hold the same values.
     */
    static final class ArrayIteration {
        final Scriptable arrayPrototype;
        private final Slot<?> iterator;
        private final Object values;
        private final Slot<?> next;
        private final Object nextMethod;

        private ArrayIteration(
                Scriptable arrayPrototype,
                Slot<?> iterator,
                Object values,
                Slot<?> next,
                Object nextMethod) {
            this.arrayPrototype = arrayPrototype;
            this.iterator = iterator;
            this.values = values;
            this.next = next;
            this.nextMethod = nextMethod;
        }

        boolean isIntact() {
            return !iterator.stale
                    && iterator.value == values
                    && !next.stale
                    && next.value == nextMethod;
        }

        /** The iteration methods of the realm of top, if arrayPrototype has the original ones. */
        static ArrayIteration of(Scriptable arrayPrototype, TopLevel top) {
            if (arrayPrototype != ScriptableObject.getClassPrototype(top, "Array")
                    || !(arrayPrototype instanceof ScriptableObject)) {
                return null;
            }
            Slot<?> iterator =
                    ((ScriptableObject) arrayPrototype).getMap().query(SymbolKey.ITERATOR, 0);
            if (!(iterator instanceof StandardSlot)
                    || !(iterator.value instanceof JSFunction)
                    || ((JSFunction) iterator.value).getDescriptor()
                            != NativeArray.VALUES_DESCRIPTOR) {
                return null;
            }
            Scriptable iteratorPrototype = NativeArrayIterator.prototype(top);
            if (!(iteratorPrototype instanceof ScriptableObject)) {
                return null;
            }
            Slot<?> next = ((ScriptableObject) iteratorPrototype).getMap().query("next", 0);
            if (!(next instanceof StandardSlot)
                    || !(next.value instanceof JSFunction)
                    || ((JSFunction) next.value).getDescriptor()
                            != NativeArrayIterator.NEXT_DESCRIPTOR) {
                return null;
            }
            return new ArrayIteration(arrayPrototype, iterator, iterator.value, next, next.value);
        }
    }

    static boolean hasOriginalArrayIteratorNext(VarScope scope) {
        Scriptable prototype = NativeArrayIterator.prototype(scope);
        if (prototype == null) {
            return false;
        }
        Object next = ScriptableObject.getProperty(prototype, "next");
        return next instanceof JSFunction
                && ((JSFunction) next).getDescriptor() == NativeArrayIterator.NEXT_DESCRIPTOR;
    }

    boolean isDone() {
        return done;
    }

    /** IteratorStepValue: the next value, or undefined once the iterator is done. */
    Object step(Context cx, VarScope scope) {
        if (done) {
            return Undefined.instance;
        }
        if (array != null) {
            if (index >= NativeArray.getLengthProperty(cx, array)) {
                done = true;
                return Undefined.instance;
            }
            if (array instanceof NativeArray) {
                Object value = array.get((int) index, array);
                if (value != Scriptable.NOT_FOUND) {
                    index++;
                    return value;
                }
            }
            return ScriptRuntime.getObjectIndex(array, (double) index++, cx, scope);
        }
        try {
            if (builtIn != null) {
                Object value = builtIn.step(cx, scope);
                if (value == ES6Iterator.DONE) {
                    done = true;
                    return Undefined.instance;
                }
                return value;
            }
            if (!(nextMethod instanceof Callable)) {
                throw ScriptRuntime.notFunctionError(nextMethod, "next");
            }
            Object result =
                    ((Callable) nextMethod).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            if (!(result instanceof Scriptable)) {
                throw ScriptRuntime.typeErrorById("msg.iterator.not.object", "next");
            }
            Scriptable r = (Scriptable) result;
            Object isDone = ScriptableObject.getProperty(r, "done");
            if (isDone != Scriptable.NOT_FOUND && ScriptRuntime.toBoolean(isDone)) {
                done = true;
                return Undefined.instance;
            }
            Object value = ScriptableObject.getProperty(r, "value");
            return value == Scriptable.NOT_FOUND ? Undefined.instance : value;
        } catch (RuntimeException e) {
            done = true;
            throw e;
        }
    }

    /** The remaining values, as an array (a rest element). */
    Scriptable rest(Context cx, VarScope scope) {
        List<Object> values = new ArrayList<>();
        while (true) {
            Object value = step(cx, scope);
            if (done) {
                break;
            }
            values.add(value);
        }
        return cx.newArray(ScriptableObject.getTopLevelScope(scope), values.toArray());
    }

    /**
     * Adds a record to the guard of its statement (the head of a list), unless closing it can never
     * be needed (an array read by index); returns the new head. Records already done are dropped.
     */
    static Object guard(IteratorRecord record, Object guard) {
        IteratorRecord head = (IteratorRecord) guard;
        while (head != null && head.done) {
            head = head.previous;
        }
        if (record.iterator == null || record.done) {
            return head;
        }
        record.previous = head;
        return record;
    }

    /**
     * IteratorClose after an abrupt completion of the guarded statement, for each record not done:
     * the error being thrown wins over any error of the return method.
     */
    static void closeAll(Object guard, Context cx, VarScope scope) {
        for (IteratorRecord r = (IteratorRecord) guard; r != null; r = r.previous) {
            if (!r.done && r.iterator != null) {
                r.done = true;
                try {
                    Object returnMethod =
                            ScriptableObject.getProperty(r.iterator, ES6Iterator.RETURN_METHOD);
                    if (returnMethod instanceof Callable) {
                        ((Callable) returnMethod)
                                .call(cx, scope, r.iterator, ScriptRuntime.emptyArgs);
                    }
                } catch (RhinoException ignored) {
                    // the completion that is propagating takes precedence
                }
            }
        }
    }

    /**
     * IteratorClose for the return completion of a generator: the first error of a return method is
     * thrown, the iterators opened before it are then closed as for a throw.
     */
    static void closeAllForReturn(Object guard, Context cx, VarScope scope) {
        RhinoException error = null;
        for (IteratorRecord r = (IteratorRecord) guard; r != null; r = r.previous) {
            if (r.done || r.iterator == null) {
                continue;
            }
            if (error == null) {
                try {
                    r.close(cx, scope);
                } catch (RhinoException e) {
                    error = e;
                }
            } else {
                r.done = true;
                try {
                    Object returnMethod =
                            ScriptableObject.getProperty(r.iterator, ES6Iterator.RETURN_METHOD);
                    if (returnMethod instanceof Callable) {
                        ((Callable) returnMethod)
                                .call(cx, scope, r.iterator, ScriptRuntime.emptyArgs);
                    }
                } catch (RhinoException ignored) {
                    // the error being thrown takes precedence
                }
            }
        }
        if (error != null) {
            throw error;
        }
    }

    /** IteratorClose after a normal completion, when the iterator is not done. */
    void close(Context cx, VarScope scope) {
        if (done || iterator == null) {
            return;
        }
        done = true;
        Object returnMethod = ScriptableObject.getProperty(iterator, ES6Iterator.RETURN_METHOD);
        if (returnMethod == Scriptable.NOT_FOUND
                || returnMethod == null
                || Undefined.isUndefined(returnMethod)) {
            return;
        }
        if (!(returnMethod instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(returnMethod, ES6Iterator.RETURN_METHOD);
        }
        Object result =
                ((Callable) returnMethod).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
        if (!(result instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.not.object", "return");
        }
    }
}
