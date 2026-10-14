/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.Symbol.Kind.REGULAR;

import java.io.Serial;
import org.monflabs.mozilla.javascript.NativeAsyncFromSyncIterator.IteratorRecord;

/**
 * An Iterator Helper object (ECMA-262 27.1.2.1): the lazy result of Iterator.prototype.map, filter,
 * take, drop and flatMap. The spec defines it as a generator over the closure of the method; its
 * states are the generator ones, and each next runs the closure to its next yield.
 */
final class NativeIteratorHelper extends ScriptableObject {
    @Serial private static final long serialVersionUID = 1L;

    private static final SymbolKey TAG = new SymbolKey("IteratorHelperPrototype", REGULAR);

    private static final ClassDescriptor DESCRIPTOR =
            new ClassDescriptor.Builder(TAG)
                    .withPrimitiveThis()
                    .withMethod(CTOR, "next", 0, NativeIteratorHelper::js_next)
                    .withMethod(CTOR, "return", 0, NativeIteratorHelper::js_return)
                    .withProp(
                            CTOR,
                            SymbolKey.TO_STRING_TAG,
                            value("Iterator Helper", DONTENUM | READONLY))
                    .build();

    /** The value of a closure that returned rather than yielded. */
    private static final Object DONE = new Object();

    enum Kind {
        MAP,
        FILTER,
        TAKE,
        DROP,
        FLAT_MAP
    }

    private enum State {
        SUSPENDED_START,
        SUSPENDED_YIELD,
        EXECUTING,
        COMPLETED
    }

    private final Kind kind;
    private final IteratorRecord iterated;
    private final Callable callback;

    /** The remaining count of take and drop, possibly infinite. */
    private double remaining;

    private long counter;

    /** The iterator of the value flatMap is yielding from, or null. */
    private IteratorRecord inner;

    private State state = State.SUSPENDED_START;

    NativeIteratorHelper(
            VarScope scope,
            Kind kind,
            IteratorRecord iterated,
            Callable callback,
            double remaining) {
        this.kind = kind;
        this.iterated = iterated;
        this.callback = callback;
        this.remaining = remaining;
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        setParentScope(top);
        setPrototype((Scriptable) ScriptableObject.getTopScopeValue(top, TAG));
    }

    /** Creates %IteratorHelperPrototype% (27.1.2.1), after %Iterator.prototype%. */
    static void init(Context cx, TopLevel scope, Scriptable iteratorPrototype, boolean sealed) {
        NativeObject prototype = new NativeObject();
        DESCRIPTOR.populate(cx, scope, prototype, sealed);
        prototype.setPrototype(iteratorPrototype);
        scope.associateValue(TAG, prototype);
    }

    @Override
    public String getClassName() {
        return "Object";
    }

    private static NativeIteratorHelper realThis(Object thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, NativeIteratorHelper.class);
    }

    /** %IteratorHelperPrototype%.next ( ) (27.1.2.1.1): GeneratorResume. */
    private static Object js_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeIteratorHelper helper = realThis(thisObj);
        helper.validate();
        if (helper.state == State.COMPLETED) {
            return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
        }
        helper.state = State.EXECUTING;
        Object value;
        try {
            value = helper.resume(cx, s);
        } catch (RuntimeException e) {
            helper.state = State.COMPLETED;
            throw e;
        }
        if (value == DONE) {
            helper.state = State.COMPLETED;
            return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
        }
        helper.state = State.SUSPENDED_YIELD;
        return ES6Iterator.makeIteratorResult(cx, s, Boolean.FALSE, value);
    }

    /**
     * %IteratorHelperPrototype%.return ( ) (27.1.2.1.2): the closure is suspended at a yield, where
     * a return completion closes the underlying iterators.
     */
    private static Object js_return(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeIteratorHelper helper = realThis(thisObj);
        helper.validate();
        if (helper.state != State.COMPLETED) {
            helper.state =
                    helper.state == State.SUSPENDED_START ? State.COMPLETED : State.EXECUTING;
            try {
                if (helper.inner != null) {
                    try {
                        NativeIteratorPrototype.close(cx, s, helper.inner.iterator);
                    } catch (RhinoException e) {
                        NativeIteratorPrototype.closeOnThrow(cx, s, helper.iterated.iterator);
                        throw e;
                    }
                }
                NativeIteratorPrototype.close(cx, s, helper.iterated.iterator);
            } finally {
                helper.state = State.COMPLETED;
            }
        }
        return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
    }

    /** GeneratorValidate (27.5.3.2): a helper cannot be resumed from its own closure. */
    private void validate() {
        if (state == State.EXECUTING) {
            throw ScriptRuntime.typeErrorById("msg.generator.executing");
        }
    }

    /** Runs the closure to its next yield: the value yielded, or DONE when it returns. */
    private Object resume(Context cx, VarScope s) {
        switch (kind) {
            case MAP:
                {
                    Scriptable r = NativeIteratorPrototype.step(cx, s, iterated);
                    if (r == null) {
                        return DONE;
                    }
                    return NativeIteratorPrototype.callOrClose(
                            cx, s, iterated, callback, NativeIteratorPrototype.value(r), counter++);
                }
            case FILTER:
                for (; ; ) {
                    Scriptable r = NativeIteratorPrototype.step(cx, s, iterated);
                    if (r == null) {
                        return DONE;
                    }
                    Object value = NativeIteratorPrototype.value(r);
                    if (ScriptRuntime.toBoolean(
                            NativeIteratorPrototype.callOrClose(
                                    cx, s, iterated, callback, value, counter++))) {
                        return value;
                    }
                }
            case TAKE:
                {
                    if (remaining == 0) {
                        NativeIteratorPrototype.close(cx, s, iterated.iterator);
                        return DONE;
                    }
                    if (remaining != Double.POSITIVE_INFINITY) {
                        remaining--;
                    }
                    Scriptable r = NativeIteratorPrototype.step(cx, s, iterated);
                    return r == null ? DONE : NativeIteratorPrototype.value(r);
                }
            case DROP:
                {
                    while (remaining > 0) {
                        if (remaining != Double.POSITIVE_INFINITY) {
                            remaining--;
                        }
                        if (NativeIteratorPrototype.step(cx, s, iterated) == null) {
                            return DONE;
                        }
                    }
                    Scriptable r = NativeIteratorPrototype.step(cx, s, iterated);
                    return r == null ? DONE : NativeIteratorPrototype.value(r);
                }
            case FLAT_MAP:
                return resumeFlatMap(cx, s);
            default:
                throw new IllegalStateException(kind.toString());
        }
    }

    /** The closure of Iterator.prototype.flatMap (27.1.4.6). */
    private Object resumeFlatMap(Context cx, VarScope s) {
        for (; ; ) {
            if (inner == null) {
                Scriptable r = NativeIteratorPrototype.step(cx, s, iterated);
                if (r == null) {
                    return DONE;
                }
                Object mapped =
                        NativeIteratorPrototype.callOrClose(
                                cx,
                                s,
                                iterated,
                                callback,
                                NativeIteratorPrototype.value(r),
                                counter++);
                try {
                    inner = NativeIteratorPrototype.getIteratorFlattenable(cx, s, mapped, false);
                } catch (RhinoException e) {
                    NativeIteratorPrototype.closeOnThrow(cx, s, iterated.iterator);
                    throw e;
                }
            }
            try {
                Scriptable r = NativeIteratorPrototype.step(cx, s, inner);
                if (r != null) {
                    return NativeIteratorPrototype.value(r);
                }
            } catch (RhinoException e) {
                inner = null;
                NativeIteratorPrototype.closeOnThrow(cx, s, iterated.iterator);
                throw e;
            }
            inner = null;
        }
    }
}
