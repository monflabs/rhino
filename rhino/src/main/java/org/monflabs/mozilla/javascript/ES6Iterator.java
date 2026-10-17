/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;

import java.io.Serial;

public abstract class ES6Iterator extends ScriptableObject {

    @Serial private static final long serialVersionUID = 2438373029140003950L;

    public static final String NEXT_METHOD = "next";
    public static final String DONE_PROPERTY = "done";
    public static final String RETURN_PROPERTY = "return";
    public static final String VALUE_PROPERTY = "value";
    public static final String RETURN_METHOD = "return";

    private static final ClassDescriptor.BuiltInJSCodeExec<JSFunction> NEXT = ES6Iterator::js_next;

    /** Returned by {@link #step} once the iterator is done. */
    static final Object DONE = new Object();

    /** Whether the function is the original next method of a built-in iterator. */
    static boolean isNextMethod(Object f) {
        return ClassDescriptor.isBuiltIn(f, NEXT);
    }

    public static ClassDescriptor makeDescriptor(String name, String tag) {
        // Need a way to associate the built object with the tag. We
        // kind of need this in general, and at the top level, but we
        // can bodge it for now.
        return new ClassDescriptor.Builder(name)
                .withMethod(CTOR, "next", 0, NEXT)
                .withMethod(CTOR, SymbolKey.ITERATOR, 1, ES6Iterator::js_iterator)
                .withProp(CTOR, SymbolKey.TO_STRING_TAG, value(tag, DONTENUM | READONLY))
                .build();
    }

    public static void initialize(
            ClassDescriptor desc,
            Context cx,
            TopLevel scope,
            ScriptableObject obj,
            boolean sealed,
            String name) {
        Scriptable iteratorPrototype = NativeIteratorPrototype.iteratorPrototype(scope);
        if (iteratorPrototype == null) {
            scope.associateValue(name, desc.populateGlobal(cx, scope, obj, sealed));
            return;
        }
        // VERSION_ECMASCRIPT: @@iterator is inherited from %Iterator.prototype% (27.1.4.13)
        var global = desc.populateGlobal(cx, scope, obj, false);
        global.delete(SymbolKey.ITERATOR);
        global.setPrototype(iteratorPrototype);
        if (sealed) {
            global.sealObject();
        }
        scope.associateValue(name, global);
    }

    protected boolean exhausted = false;
    private String tag;

    protected ES6Iterator() {}

    protected ES6Iterator(VarScope scope, String tag) {
        // Set parent and prototype properties. Since we don't have a
        // "Iterator" constructor in the top scope, we stash the
        // prototype in the top scope's associated value.
        this.tag = tag;
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        this.setParentScope(top);
        ScriptableObject prototype = (ScriptableObject) ScriptableObject.getTopScopeValue(top, tag);
        setPrototype(prototype);
    }

    private static ES6Iterator realThis(Object thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, ES6Iterator.class);
    }

    private static Object js_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        ES6Iterator iterator = realThis(thisObj);
        return iterator.next(cx, s);
    }

    private static Object js_iterator(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return thisObj;
    }

    protected abstract boolean isDone(Context cx, VarScope scope);

    protected abstract Object nextValue(Context cx, VarScope scope);

    protected Object next(Context cx, VarScope scope) {
        Object value = step(cx, scope);
        return value == DONE
                ? makeIteratorResult(cx, scope, Boolean.TRUE)
                : makeIteratorResult(cx, scope, Boolean.FALSE, value);
    }

    /** {@link #next} without its result object: the next value, or {@link #DONE}. */
    Object step(Context cx, VarScope scope) {
        if (isDone(cx, scope) || this.exhausted) {
            this.exhausted = true;
            return DONE;
        }
        return nextValue(cx, scope);
    }

    protected String getTag() {
        return tag;
    }

    // 25.1.1.3 The IteratorResult Interface
    static Scriptable makeIteratorResult(Context cx, VarScope scope, Boolean done) {
        return makeIteratorResult(cx, scope, done, Undefined.instance);
    }

    static Scriptable makeIteratorResult(Context cx, VarScope scope, Boolean done, Object value) {
        if (cx.isLatestEcmaScript()) {
            // CreateIteratorResultObject (7.4.14): own data properties of an ordinary object
            NativeObject result = new NativeObject();
            ScriptRuntime.setBuiltinProtoAndParent(result, scope, TopLevel.Builtins.Object);
            result.defineProperty(VALUE_PROPERTY, value, EMPTY);
            result.defineProperty(DONE_PROPERTY, done, EMPTY);
            return result;
        }
        final Scriptable iteratorResult = cx.newObject(scope);
        ScriptableObject.putProperty(iteratorResult, VALUE_PROPERTY, value);
        ScriptableObject.putProperty(iteratorResult, DONE_PROPERTY, done);
        return iteratorResult;
    }
}
