/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.alias;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.PROTO;

import java.io.Serial;

public class NativeSet extends ScriptableObject {
    @Serial private static final long serialVersionUID = -8442212766987072986L;
    private static final String CLASS_NAME = "Set";
    static final String ITERATOR_TAG = "Set Iterator";

    private static final ClassDescriptor DESCRIPTOR;
    private static final ClassDescriptor ITER_DESCRIPTOR =
            ES6Iterator.makeDescriptor(ITERATOR_TAG, ITERATOR_TAG);

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                0,
                                ClassDescriptor.typeError(),
                                NativeSet::js_constructor)
                        .withMethod(PROTO, "add", 1, NativeSet::js_add)
                        .withMethod(PROTO, "delete", 1, NativeSet::js_delete)
                        .withMethod(PROTO, "has", 1, NativeSet::js_has)
                        .withMethod(PROTO, "clear", 0, NativeSet::js_clear)
                        .withMethod(PROTO, "values", 0, NativeSet::js_values)
                        .withProp(PROTO, "keys", alias("values", DONTENUM | READONLY))
                        .withProp(PROTO, SymbolKey.ITERATOR, alias("values", DONTENUM))
                        .withMethod(PROTO, "forEach", 1, NativeSet::js_forEach)
                        .withMethod(PROTO, "entries", 0, NativeSet::js_entries)

                        // ES2025 Set methods
                        .withMethod(PROTO, "intersection", 1, NativeSet::js_intersection)
                        .withMethod(PROTO, "union", 1, NativeSet::js_union)
                        .withMethod(PROTO, "difference", 1, NativeSet::js_difference)
                        .withMethod(
                                PROTO, "symmetricDifference", 1, NativeSet::js_symmetricDifference)
                        .withMethod(PROTO, "isSubsetOf", 1, NativeSet::js_isSubsetOf)
                        .withMethod(PROTO, "isSupersetOf", 1, NativeSet::js_isSupersetOf)
                        .withMethod(PROTO, "isDisjointFrom", 1, NativeSet::js_isDisjointFrom)
                        .withProp(
                                PROTO,
                                "size",
                                (thisObj) -> realThis(thisObj, "size").js_getSize(),
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value(CLASS_NAME, DONTENUM | READONLY))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    private final Hashtable entries = new Hashtable();

    private boolean instanceOfSet = false;

    /** The size accessor of stock Rhino's prototypes (before VERSION_ECMASCRIPT). */
    static final SymbolKey GETSIZE = new SymbolKey("[Symbol.getSize]", Symbol.Kind.REGULAR);

    static Object init(Context cx, VarScope scope, boolean sealed) {
        ES6Iterator.initialize(
                ITER_DESCRIPTOR,
                cx,
                (TopLevel) scope,
                new NativeCollectionIterator(ITERATOR_TAG),
                sealed,
                ITERATOR_TAG);
        NativeObject proto = new NativeObject();
        return DESCRIPTOR.buildConstructor(
                cx,
                scope,
                proto,
                sealed,
                (c, ctor) -> {
                    if (!c.isLatestEcmaScript()) {
                        // the internal accessor of stock Rhino, before VERSION_ECMASCRIPT
                        proto.defineProperty(
                                c,
                                scope,
                                NativeSet.GETSIZE,
                                (thisObj) -> realThis(thisObj, "size").js_getSize(),
                                null,
                                DONTENUM | PERMANENT);
                    }
                });
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    private static Scriptable js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet ns = new NativeSet();
        ns.instanceOfSet = true;
        if (args.length > 0) {
            loadFromIterable(cx, s, ns, NativeMap.key(args));
        }
        ScriptRuntime.setBuiltinProtoAndParent(ns, f, nt, s, TopLevel.Builtins.Set);
        return ns;
    }

    private static Object js_add(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet realThis = realThis(thisObj, "add");
        var k = NativeMap.key(args);
        return realThis.js_add(k);
    }

    private Object js_add(Object k) {
        // Special handling of "negative zero" from the spec.
        if ((k instanceof Number) && ((Number) k).doubleValue() == ScriptRuntime.negativeZero) {
            entries.put(ScriptRuntime.zeroObj, ScriptRuntime.zeroObj);
            return this;
        }
        entries.put(k, k);
        return this;
    }

    private static Object js_delete(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet realThis = realThis(thisObj, "add");
        var arg = NativeMap.key(args);
        return realThis.js_delete(arg);
    }

    private Object js_delete(Object arg) {
        return entries.deleteEntry(arg);
    }

    private static Object js_has(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet realThis = realThis(thisObj, "add");
        var arg = NativeMap.key(args);
        return realThis.js_has(arg);
    }

    private Object js_has(Object arg) {
        // Special handling of "negative zero" from the spec.
        if ((arg instanceof Number) && ((Number) arg).doubleValue() == ScriptRuntime.negativeZero) {
            return entries.has(ScriptRuntime.zeroObj);
        }
        return entries.has(arg);
    }

    private static Object js_clear(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet realThis = realThis(thisObj, "add");
        return realThis.js_clear();
    }

    private Object js_clear() {
        entries.clear();
        return Undefined.instance;
    }

    private Object js_getSize() {
        return entries.size();
    }

    private static Object js_values(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet realThis = realThis(thisObj, "add");
        return realThis(thisObj, "values")
                .js_iterator(f.getDeclarationScope(), NativeCollectionIterator.Type.VALUES);
    }

    private static Object js_entries(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSet realThis = realThis(thisObj, "add");
        return realThis(thisObj, "values")
                .js_iterator(f.getDeclarationScope(), NativeCollectionIterator.Type.BOTH);
    }

    private Object js_iterator(VarScope scope, NativeCollectionIterator.Type type) {
        return new NativeCollectionIterator(scope, ITERATOR_TAG, type, entries.iterator());
    }

    private static Object js_forEach(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return realThis(thisObj, "forEach")
                .js_forEach(
                        cx,
                        f.getDeclarationScope(),
                        NativeMap.key(args),
                        args.length > 1 ? args[1] : Undefined.SCRIPTABLE_UNDEFINED);
    }

    private Object js_forEach(Context cx, VarScope scope, Object arg1, Object arg2) {
        if (!(arg1 instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(arg1);
        }
        final Function f = (Function) arg1;

        for (Hashtable.Entry entry : entries) {
            final Hashtable.Entry e = entry;
            f.call(cx, scope, arg2, new Object[] {e.value, e.value, this});
        }
        return Undefined.instance;
    }

    /**
     * If an "iterable" object was passed to the constructor, there are many many things to do. This
     * is common code with NativeWeakSet.
     */
    static void loadFromIterable(Context cx, VarScope scope, ScriptableObject set, Object arg1) {
        if ((arg1 == null) || Undefined.instance.equals(arg1)) {
            return;
        }

        // Call the "[Symbol.iterator]" property as a function.
        Object ito = ScriptRuntime.callIterator(arg1, cx, scope);
        if (Undefined.instance.equals(ito)) {
            // Per spec, ignore if the iterator returns undefined
            return;
        }

        // Find the "add" function of our own prototype, since it might have
        // been replaced. Since we're not fully constructed yet, create a dummy instance
        // so that we can get our own prototype.
        ScriptableObject dummy = ensureScriptableObject(cx.newObject(scope, set.getClassName()));
        var addCall = ScriptRuntime.getPropAndThis(dummy.getPrototype(), "add", cx, scope);
        Callable add = addCall.getCallable();

        // Finally, run through all the iterated values and add them!
        try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, ito)) {
            for (Object val : it) {
                final Object finalVal = val == Scriptable.NOT_FOUND ? Undefined.instance : val;
                add.call(cx, scope, set, new Object[] {finalVal});
            }
        }
    }

    private static NativeSet realThis(Object thisObj, String name) {
        NativeSet ns = LambdaConstructor.convertThisObject(thisObj, NativeSet.class);
        if (!ns.instanceOfSet) {
            // If we get here, then this object doesn't have the "Set internal data slot."
            throw ScriptRuntime.typeErrorById("msg.incompat.call", name);
        }
        return ns;
    }

    // Set methods (ES2025, 24.2.4)

    /** Set Record (24.2.1.2). */
    private static final class SetRecord {
        final Scriptable set;
        final double size;
        final Callable has;
        final Callable keys;

        SetRecord(Scriptable set, double size, Callable has, Callable keys) {
            this.set = set;
            this.size = size;
            this.has = has;
            this.keys = keys;
        }
    }

    /** GetSetRecord (24.2.1.3). */
    private static SetRecord getSetRecord(Object obj) {
        if (!(obj instanceof Scriptable) || !ScriptRuntime.isObject(obj)) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(obj));
        }
        Scriptable o = (Scriptable) obj;
        Object rawSize = getValue(o, "size");
        if (Undefined.isUndefined(rawSize)) {
            throw ScriptRuntime.typeError("Set-like object must have a 'size' property");
        }
        double numSize = ScriptRuntime.toNumber(rawSize);
        if (Double.isNaN(numSize)) {
            throw ScriptRuntime.typeError("size is not a number");
        }
        double intSize = ScriptRuntime.toIntegerOrInfinity(numSize);
        if (intSize < 0) {
            throw ScriptRuntime.rangeError("size is negative");
        }
        Object has = getValue(o, "has");
        if (!(has instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(has));
        }
        Object keys = getValue(o, "keys");
        if (!(keys instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keys));
        }
        return new SetRecord(o, intSize, (Callable) has, (Callable) keys);
    }

    private static Object getValue(Scriptable o, String name) {
        Object v = ScriptableObject.getProperty(o, name);
        return v == NOT_FOUND ? Undefined.instance : v;
    }

    /** The iterator of GetIteratorFromMethod(set, keys), with IteratorStepValue (7.4). */
    private static final class KeysIterator {
        private final Context cx;
        private final VarScope scope;
        private final Scriptable iterator;
        private final Callable next;
        Object value;

        KeysIterator(Context cx, VarScope scope, SetRecord rec) {
            this.cx = cx;
            this.scope = scope;
            Object it = rec.keys.call(cx, scope, rec.set, ScriptRuntime.emptyArgs);
            if (!ScriptRuntime.isObject(it)) {
                throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(it));
            }
            iterator = (Scriptable) it;
            Object n = getValue(iterator, "next");
            if (!(n instanceof Callable)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.isnt.function", "next", ScriptRuntime.typeof(n));
            }
            next = (Callable) n;
        }

        /** IteratorStepValue; -0 becomes +0 (the set methods all do so). */
        boolean step() {
            Object result = next.call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.arg.not.object", ScriptRuntime.typeof(result));
            }
            Scriptable r = (Scriptable) result;
            if (ScriptRuntime.toBoolean(getValue(r, "done"))) {
                return false;
            }
            value = getValue(r, "value");
            if (value instanceof Number && ((Number) value).doubleValue() == 0) {
                value = ScriptRuntime.zeroObj;
            }
            return true;
        }

        /** IteratorClose with a normal completion (7.4.11). */
        void close() {
            Object ret = getValue(iterator, "return");
            if (ret == null || Undefined.isUndefined(ret)) {
                return;
            }
            if (!(ret instanceof Callable)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.isnt.function", "return", ScriptRuntime.typeof(ret));
            }
            Object result = ((Callable) ret).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.arg.not.object", ScriptRuntime.typeof(result));
            }
        }
    }

    private NativeSet newSet(Context cx, VarScope scope) {
        NativeSet result = (NativeSet) cx.newObject(scope, CLASS_NAME);
        result.instanceOfSet = true;
        return result;
    }

    private NativeSet copySet(Context cx, VarScope scope) {
        NativeSet result = newSet(cx, scope);
        for (Hashtable.Entry entry : entries) {
            result.js_add(entry.key);
        }
        return result;
    }

    private boolean contains(Object key) {
        return js_has(key) == Boolean.TRUE;
    }

    private static boolean callHas(Context cx, VarScope scope, SetRecord rec, Object key) {
        return ScriptRuntime.toBoolean(rec.has.call(cx, scope, rec.set, new Object[] {key}));
    }

    private static Object js_union(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "union").legacyUnion(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "union");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        KeysIterator keys = new KeysIterator(cx, s, rec);
        NativeSet result = o.copySet(cx, s);
        while (keys.step()) {
            result.js_add(keys.value);
        }
        return result;
    }

    private static Object js_intersection(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "intersection").legacyIntersection(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "intersection");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        NativeSet result = o.newSet(cx, s);
        if (o.entries.size() <= rec.size) {
            for (Hashtable.Entry entry : o.entries) {
                Object e = entry.key;
                if (callHas(cx, s, rec, e)) {
                    result.js_add(e);
                }
            }
        } else {
            KeysIterator keys = new KeysIterator(cx, s, rec);
            while (keys.step()) {
                if (o.contains(keys.value)) {
                    result.js_add(keys.value);
                }
            }
        }
        return result;
    }

    private static Object js_difference(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "difference").legacyDifference(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "difference");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        NativeSet result = o.copySet(cx, s);
        if (o.entries.size() <= rec.size) {
            for (Hashtable.Entry entry : result.entries) {
                Object e = entry.key;
                if (callHas(cx, s, rec, e)) {
                    result.js_delete(e);
                }
            }
        } else {
            KeysIterator keys = new KeysIterator(cx, s, rec);
            while (keys.step()) {
                result.js_delete(keys.value);
            }
        }
        return result;
    }

    private static Object js_symmetricDifference(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "symmetricDifference").legacySymmetricDifference(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "symmetricDifference");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        KeysIterator keys = new KeysIterator(cx, s, rec);
        NativeSet result = o.copySet(cx, s);
        while (keys.step()) {
            Object next = keys.value;
            boolean inResult = result.contains(next);
            if (o.contains(next)) {
                if (inResult) {
                    result.js_delete(next);
                }
            } else if (!inResult) {
                result.js_add(next);
            }
        }
        return result;
    }

    private static Object js_isSubsetOf(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "isSubsetOf").legacyIsSubsetOf(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "isSubsetOf");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        if (o.entries.size() > rec.size) {
            return Boolean.FALSE;
        }
        for (Hashtable.Entry entry : o.entries) {
            if (!callHas(cx, s, rec, entry.key)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    private static Object js_isSupersetOf(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "isSupersetOf").legacyIsSupersetOf(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "isSupersetOf");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        if (o.entries.size() < rec.size) {
            return Boolean.FALSE;
        }
        KeysIterator keys = new KeysIterator(cx, s, rec);
        while (keys.step()) {
            if (!o.contains(keys.value)) {
                keys.close();
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    private static Object js_isDisjointFrom(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!cx.isLatestEcmaScript()) {
            return realThis(thisObj, "isDisjointFrom").legacyIsDisjointFrom(cx, s, args);
        }
        NativeSet o = realThis(thisObj, "isDisjointFrom");
        SetRecord rec = getSetRecord(args.length > 0 ? args[0] : Undefined.instance);
        if (o.entries.size() <= rec.size) {
            for (Hashtable.Entry entry : o.entries) {
                if (callHas(cx, s, rec, entry.key)) {
                    return Boolean.FALSE;
                }
            }
        } else {
            KeysIterator keys = new KeysIterator(cx, s, rec);
            while (keys.step()) {
                if (o.contains(keys.value)) {
                    keys.close();
                    return Boolean.FALSE;
                }
            }
        }
        return Boolean.TRUE;
    }

    // The Set methods of stock Rhino, before VERSION_ECMASCRIPT

    private Object legacyIntersection(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        NativeSet result = (NativeSet) cx.newObject(scope, CLASS_NAME);
        result.instanceOfSet = true;

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        return legacyIntersectionSetLike(cx, scope, otherObj, result, sizeVal, hasVal, keysVal);
    }

    private Object legacyIntersectionSetLike(
            Context cx,
            VarScope scope,
            Object otherObj,
            NativeSet result,
            Object sizeVal,
            Object hasVal,
            Object keysVal) {
        // Validate has and keys are callable
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        Callable hasMethod = (Callable) hasVal;
        Callable keysMethod = (Callable) keysVal;

        // ES2025: Compare sizes to determine iteration strategy
        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }
        int otherSize =
                Double.isInfinite(otherSizeDouble)
                        ? Integer.MAX_VALUE
                        : (int) Math.floor(otherSizeDouble);
        int thisSize = entries.size();

        if (thisSize <= otherSize) {
            // When this.size <= other.size: iterate through this, call other.has()
            for (Hashtable.Entry entry : entries) {
                Object key = entry.key;
                Object inOther = legacyCallHas(cx, scope, otherObj, hasMethod, key);
                if (ScriptRuntime.toBoolean(inOther)) {
                    result.js_add(key);
                }
            }
        } else {
            // When this.size > other.size: iterate through other.keys(), call this.has()
            Object iterator =
                    ScriptRuntime.callIterator(
                            keysMethod.call(
                                    cx,
                                    scope,
                                    ScriptableObject.ensureScriptable(otherObj),
                                    ScriptRuntime.emptyArgs),
                            cx,
                            scope);
            try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
                for (Object key : it) {
                    if (js_has(key) == Boolean.TRUE) {
                        result.js_add(key);
                    }
                }
            }
        }

        return result;
    }

    private Object legacyUnion(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        NativeSet result = (NativeSet) cx.newObject(scope, CLASS_NAME);
        result.instanceOfSet = true;

        // Add all elements from this set
        for (Hashtable.Entry entry : entries) {
            result.js_add(entry.key);
        }

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        // Validate size is a number (required by GetSetRecord)
        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }

        // Validate has and keys are callable (GetSetRecord requires both even if union only uses
        // keys)
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        // Set-like object - use keys method
        Callable keysMethod = (Callable) keysVal;
        Object iterator =
                ScriptRuntime.callIterator(
                        keysMethod.call(cx, scope, scriptable, ScriptRuntime.emptyArgs), cx, scope);
        try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
            for (Object key : it) {
                result.js_add(key);
            }
        }

        return result;
    }

    private Object legacyDifference(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        NativeSet result = (NativeSet) cx.newObject(scope, CLASS_NAME);
        result.instanceOfSet = true;

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        return legacyDifferenceSetLike(cx, scope, otherObj, result, sizeVal, hasVal, keysVal);
    }

    private Object legacyDifferenceSetLike(
            Context cx,
            VarScope scope,
            Object otherObj,
            NativeSet result,
            Object sizeVal,
            Object hasVal,
            Object keysVal) {
        // Validate has and keys are callable
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        Callable hasMethod = (Callable) hasVal;
        Callable keysMethod = (Callable) keysVal;

        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }
        int otherSize =
                Double.isInfinite(otherSizeDouble)
                        ? Integer.MAX_VALUE
                        : (int) Math.floor(otherSizeDouble);
        int thisSize = entries.size();

        // According to the spec and test converts-negative-zero.js:
        // When this.size > other.size, we should iterate through other.keys()
        // and remove matching elements, NOT call other.has()
        if (thisSize > otherSize) {

            // First, add all elements from this set
            for (Hashtable.Entry entry : entries) {
                result.js_add(entry.key);
            }

            // Then iterate through other and remove matching elements
            Object iterator =
                    ScriptRuntime.callIterator(
                            keysMethod.call(
                                    cx,
                                    scope,
                                    ScriptableObject.ensureScriptable(otherObj),
                                    ScriptRuntime.emptyArgs),
                            cx,
                            scope);
            try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
                for (Object key : it) {
                    // Convert -0 to +0 as the spec requires
                    if (key instanceof Number
                            && ((Number) key).doubleValue() == ScriptRuntime.negativeZero) {
                        result.js_delete(ScriptRuntime.zeroObj);
                    } else {
                        result.js_delete(key);
                    }
                }
            }
        } else {
            // When this.size <= other.size, iterate through this and check with has()

            for (Hashtable.Entry entry : entries) {
                Object key = entry.key;
                Object inOther = legacyCallHas(cx, scope, otherObj, hasMethod, key);
                if (!ScriptRuntime.toBoolean(inOther)) {
                    result.js_add(key);
                }
            }
        }

        return result;
    }

    private Object legacySymmetricDifference(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        NativeSet result = (NativeSet) cx.newObject(scope, CLASS_NAME);
        result.instanceOfSet = true;

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        // Validate size is a number (required by GetSetRecord)
        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }

        // Validate has and keys are callable
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        // Set-like object path
        Callable hasMethod = (Callable) hasVal;
        Callable keysMethod = (Callable) keysVal;

        // Add elements from this that are not in other
        for (Hashtable.Entry entry : entries) {
            Object key = entry.key;
            Object inOther = legacyCallHas(cx, scope, otherObj, hasMethod, key);
            if (!ScriptRuntime.toBoolean(inOther)) {
                result.js_add(key);
            }
        }

        // Add elements from other that are not in this
        Object iterator =
                ScriptRuntime.callIterator(
                        keysMethod.call(cx, scope, scriptable, ScriptRuntime.emptyArgs), cx, scope);
        try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
            for (Object key : it) {
                if (js_has(key) != Boolean.TRUE) {
                    result.js_add(key);
                }
            }
        }

        return result;
    }

    private Object legacyIsSubsetOf(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        // Validate has and keys are callable (even though isSubsetOf only uses has)
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        // Set-like object - use has method for efficiency
        Callable hasMethod = (Callable) hasVal;

        // Check size optimization
        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }
        int otherSize =
                Double.isInfinite(otherSizeDouble)
                        ? Integer.MAX_VALUE
                        : (int) Math.floor(otherSizeDouble);
        int thisSize = entries.size();

        // If this set is larger than other, it cannot be a subset
        if (thisSize > otherSize) {
            return Boolean.FALSE;
        }

        // Check if all elements of this are in other
        for (Hashtable.Entry entry : entries) {
            Object key = entry.key;
            Object inOther = legacyCallHas(cx, scope, otherObj, hasMethod, key);
            if (!ScriptRuntime.toBoolean(inOther)) {
                return Boolean.FALSE;
            }
        }

        return Boolean.TRUE;
    }

    private Object legacyIsSupersetOf(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        // Validate size is a number (required by GetSetRecord)
        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }

        // Validate has and keys are callable (GetSetRecord requires both even if isSupersetOf only
        // uses keys)
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        // Iterate through other.keys() and check if all elements are in this
        Callable keysMethod = (Callable) keysVal;
        Object iterator =
                ScriptRuntime.callIterator(
                        keysMethod.call(cx, scope, scriptable, ScriptRuntime.emptyArgs), cx, scope);
        try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
            for (Object value : it) {
                if (js_has(value) != Boolean.TRUE) {
                    return Boolean.FALSE;
                }
            }
        }
        return Boolean.TRUE;
    }

    private Object legacyIsDisjointFrom(Context cx, VarScope scope, Object[] args) {
        Object otherObj = args.length > 0 ? args[0] : Undefined.instance;

        // ES2025: GetSetRecord requires size, has, and keys properties
        Scriptable scriptable = ScriptableObject.ensureScriptable(otherObj);
        Object sizeVal = ScriptableObject.getProperty(scriptable, "size");
        Object hasVal = ScriptableObject.getProperty(scriptable, "has");
        Object keysVal = ScriptableObject.getProperty(scriptable, "keys");

        // Validate all required properties exist
        legacyValidateSetLike(sizeVal, hasVal, keysVal);

        // Validate has and keys are callable
        if (!(hasVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "has", ScriptRuntime.typeof(hasVal));
        }
        if (!(keysVal instanceof Callable)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.isnt.function", "keys", ScriptRuntime.typeof(keysVal));
        }

        // Set-like object with size optimization
        Callable hasMethod = (Callable) hasVal;
        Callable keysMethod = (Callable) keysVal;

        double otherSizeDouble = ScriptRuntime.toNumber(sizeVal);
        if (Double.isNaN(otherSizeDouble)) {
            throw ScriptRuntime.typeError("size is not a number");
        }
        int otherSize =
                Double.isInfinite(otherSizeDouble)
                        ? Integer.MAX_VALUE
                        : (int) Math.floor(otherSizeDouble);
        int thisSize = entries.size();

        if (thisSize <= otherSize) {
            // Iterate through this set
            for (Hashtable.Entry entry : entries) {
                Object key = entry.key;
                Object inOther = legacyCallHas(cx, scope, otherObj, hasMethod, key);
                if (ScriptRuntime.toBoolean(inOther)) {
                    return Boolean.FALSE;
                }
            }
        } else {
            // Iterate through other
            Object iterator =
                    ScriptRuntime.callIterator(
                            keysMethod.call(cx, scope, scriptable, ScriptRuntime.emptyArgs),
                            cx,
                            scope);
            try (IteratorLikeIterable it = new IteratorLikeIterable(cx, scope, iterator)) {
                for (Object key : it) {
                    if (js_has(key) == Boolean.TRUE) {
                        return Boolean.FALSE;
                    }
                }
            }
        }

        return Boolean.TRUE;
    }

    // Helper methods for Set operations

    private static Object legacyCallHas(
            Context cx, VarScope scope, Object obj, Object hasMethod, Object key) {
        return ((Callable) hasMethod)
                .call(cx, scope, ScriptableObject.ensureScriptable(obj), new Object[] {key});
    }

    private static void legacyValidateSetLike(Object sizeVal, Object hasVal, Object keysVal) {
        if (sizeVal == Scriptable.NOT_FOUND) {
            throw ScriptRuntime.typeError("Set-like object must have a 'size' property");
        }
        if (hasVal == Scriptable.NOT_FOUND) {
            throw ScriptRuntime.typeError("Set-like object must have a 'has' method");
        }
        if (keysVal == Scriptable.NOT_FOUND) {
            throw ScriptRuntime.typeError("Set-like object must have a 'keys' method");
        }
    }
}
