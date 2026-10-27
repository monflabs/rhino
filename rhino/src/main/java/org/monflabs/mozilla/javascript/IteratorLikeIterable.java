/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This is a class that makes it easier to iterate over "iterator-like" objects as defined in the
 * ECMAScript spec. The caller is responsible for retrieving an object that implements the
 * "iterator" pattern. This class will follow that pattern and throw appropriate JavaScript
 * exceptions.
 *
 * <p>The pattern that the target class should follow is: * It must have a function property called
 * "next" * The function must return an object with a boolean value called "done". * If "done" is
 * true, then the returned object should also contain a "value" property. * If it has a function
 * property called "return" then it will be called when the caller is done iterating.
 */
public class IteratorLikeIterable implements Iterable<Object>, Closeable {
    private final Context cx;
    private final VarScope scope;
    private final Callable next;
    private final Scriptable iterator;
    private boolean closed;

    /** Before VERSION_ECMASCRIPT, return is read when the iteration starts (or null). */
    private final boolean eagerReturn;

    private final Callable returnFunc;

    /** A built-in iterator with its original next method, stepped without result objects. */
    private final ES6Iterator builtIn;

    public IteratorLikeIterable(Context cx, VarScope scope, Object target) {
        this.cx = cx;
        this.scope = scope;
        // This will throw if "next" is not a function or undefined
        var nextCall = ScriptRuntime.getPropAndThis(target, ES6Iterator.NEXT_METHOD, cx, scope);
        next = nextCall.getCallable();
        iterator = nextCall.getThis();
        eagerReturn = !cx.isLatestEcmaScript();
        Callable ret = null;
        if (eagerReturn) {
            Object retObj =
                    ScriptRuntime.getObjectPropNoWarn(
                            target, ES6Iterator.RETURN_PROPERTY, cx, scope);
            if ((retObj != null) && !Undefined.isUndefined(retObj)) {
                if (!(retObj instanceof Callable)) {
                    throw ScriptRuntime.notFunctionError(
                            target, retObj, ES6Iterator.RETURN_PROPERTY);
                }
                ret = (Callable) retObj;
            }
        }
        returnFunc = ret;
        builtIn =
                !eagerReturn
                                && iterator instanceof ES6Iterator
                                && ES6Iterator.isNextMethodOf(next, (ES6Iterator) iterator)
                                && ScriptRuntime.hasTopCall(cx)
                        ? (ES6Iterator) iterator
                        : null;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (eagerReturn) {
                if (returnFunc != null) {
                    returnFunc.call(cx, scope, iterator, ScriptRuntime.emptyArgs);
                }
                return;
            }
            // IteratorClose (7.4.11): "return" is read when the iteration is closed
            Object retObj =
                    ScriptRuntime.getObjectPropNoWarn(
                            iterator, ES6Iterator.RETURN_PROPERTY, cx, scope);
            if ((retObj != null) && !Undefined.isUndefined(retObj)) {
                if (!(retObj instanceof Callable)) {
                    throw ScriptRuntime.notFunctionError(
                            iterator, retObj, ES6Iterator.RETURN_PROPERTY);
                }
                ((Callable) retObj).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            }
        }
    }

    @Override
    public Itr iterator() {
        return new Itr();
    }

    public final class Itr implements Iterator<Object> {
        private Object nextVal;
        private boolean isDone;

        @Override
        public boolean hasNext() {
            if (isDone) {
                return false;
            }
            if (builtIn != null) {
                Object value = builtIn.step(cx, scope);
                if (value == ES6Iterator.DONE) {
                    isDone = true;
                    return false;
                }
                nextVal = value;
                return true;
            }
            Object val = next.call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            // This will throw if "val" is not an object.
            // "getObjectPropNoWarn" won't, so do this as follows.
            Object doneval =
                    ScriptableObject.getProperty(
                            ScriptableObject.ensureScriptable(val), ES6Iterator.DONE_PROPERTY);
            if (doneval == Scriptable.NOT_FOUND) {
                doneval = Undefined.instance;
            }
            // It's OK if done is undefined.
            if (ScriptRuntime.toBoolean(doneval)) {
                isDone = true;
                return false;
            }
            nextVal = ScriptRuntime.getObjectPropNoWarn(val, ES6Iterator.VALUE_PROPERTY, cx, scope);
            return true;
        }

        @Override
        public Object next() {
            if (isDone) {
                throw new NoSuchElementException();
            }
            return nextVal;
        }

        /** Find out if "hasNext" returned done without invoking the function again. */
        public boolean isDone() {
            return isDone;
        }

        /** Manually set "done." Used for exception handling in promises. */
        public void setDone(boolean done) {
            this.isDone = done;
        }
    }
}
