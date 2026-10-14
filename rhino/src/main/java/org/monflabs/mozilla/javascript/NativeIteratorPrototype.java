/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.PROTO;
import static org.monflabs.mozilla.javascript.Symbol.Kind.REGULAR;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import org.monflabs.mozilla.javascript.NativeAsyncFromSyncIterator.IteratorRecord;

/**
 * The Iterator constructor and %Iterator.prototype% with the iterator helpers (ECMA-262 27.1.3,
 * 27.1.4), and %WrapForValidIteratorPrototype% (27.1.3.2.1). They replace the Iterator of
 * JavaScript 1.7 (NativeIterator) under VERSION_ECMASCRIPT.
 */
final class NativeIteratorPrototype {

    static final SymbolKey ITERATOR_PROTOTYPE_TAG = new SymbolKey("IteratorPrototype", REGULAR);
    private static final SymbolKey WRAP_TAG =
            new SymbolKey("WrapForValidIteratorPrototype", REGULAR);

    private static final ClassDescriptor DESCRIPTOR =
            new ClassDescriptor.Builder(
                            "Iterator",
                            0,
                            ClassDescriptor.typeError(),
                            NativeIteratorPrototype::js_constructor)
                    .withPrimitiveThis()
                    .withMethod(CTOR, "from", 1, NativeIteratorPrototype::js_from)
                    .withMethod(CTOR, "concat", 0, NativeIteratorPrototype::js_concat)
                    .withMethod(PROTO, "map", 1, NativeIteratorPrototype::js_map)
                    .withMethod(PROTO, "filter", 1, NativeIteratorPrototype::js_filter)
                    .withMethod(PROTO, "take", 1, NativeIteratorPrototype::js_take)
                    .withMethod(PROTO, "drop", 1, NativeIteratorPrototype::js_drop)
                    .withMethod(PROTO, "flatMap", 1, NativeIteratorPrototype::js_flatMap)
                    .withMethod(PROTO, "reduce", 1, NativeIteratorPrototype::js_reduce)
                    .withMethod(PROTO, "toArray", 0, NativeIteratorPrototype::js_toArray)
                    .withMethod(PROTO, "forEach", 1, NativeIteratorPrototype::js_forEach)
                    .withMethod(PROTO, "some", 1, NativeIteratorPrototype::js_some)
                    .withMethod(PROTO, "every", 1, NativeIteratorPrototype::js_every)
                    .withMethod(PROTO, "find", 1, NativeIteratorPrototype::js_find)
                    .withMethod(PROTO, SymbolKey.ITERATOR, 0, NativeIteratorPrototype::js_iterator)
                    .build();

    private static final ClassDescriptor WRAP_DESCRIPTOR =
            new ClassDescriptor.Builder(WRAP_TAG)
                    .withPrimitiveThis()
                    .withMethod(CTOR, "next", 0, NativeIteratorPrototype::wrap_next)
                    .withMethod(CTOR, "return", 0, NativeIteratorPrototype::wrap_return)
                    .build();

    private NativeIteratorPrototype() {}

    static void init(Context cx, TopLevel scope, boolean sealed) {
        NativeObject proto = new NativeObject();
        DESCRIPTOR.buildConstructor(
                cx,
                scope,
                proto,
                sealed,
                (c, ctor) -> {
                    proto.delete("constructor");
                    defineIgnoringAccessor(c, scope, proto, "constructor", ctor);
                    defineIgnoringAccessor(c, scope, proto, SymbolKey.TO_STRING_TAG, "Iterator");
                });
        scope.associateValue(ITERATOR_PROTOTYPE_TAG, proto);

        NativeObject wrapPrototype = new NativeObject();
        WRAP_DESCRIPTOR.populate(cx, scope, wrapPrototype, sealed);
        wrapPrototype.setPrototype(proto);
        scope.associateValue(WRAP_TAG, wrapPrototype);

        NativeIteratorHelper.init(cx, scope, proto, sealed);
    }

    /** %Iterator.prototype%, or null before VERSION_ECMASCRIPT. */
    static Scriptable iteratorPrototype(VarScope scope) {
        return (Scriptable) ScriptableObject.getTopScopeValue(scope, ITERATOR_PROTOTYPE_TAG);
    }

    /**
     * An accessor whose getter returns value and whose setter is
     * SetterThatIgnoresPrototypeProperties (10.4.7, 27.1.4.1, 27.1.4.14).
     */
    private static void defineIgnoringAccessor(
            Context cx, VarScope scope, ScriptableObject home, Object key, Object value) {
        String name =
                key instanceof SymbolKey ? "[" + ((SymbolKey) key).getName() + "]" : (String) key;
        JSFunction getter =
                ClassDescriptor.builtInFunction(
                        scope, "get " + name, 0, (lcx, f, nt, lscope, thisObj, args) -> value);
        JSFunction setter =
                ClassDescriptor.builtInFunction(
                        scope,
                        "set " + name,
                        1,
                        (lcx, f, nt, lscope, thisObj, args) -> {
                            setIgnoringPrototype(
                                    lcx, thisObj, home, key, args.length > 0 ? args[0] : null);
                            return Undefined.instance;
                        });
        home.defineOwnProperty(
                cx,
                key,
                new ScriptableObject.DescriptorInfo(
                        false,
                        ScriptableObject.NOT_FOUND,
                        true,
                        getter,
                        setter,
                        ScriptableObject.NOT_FOUND),
                false);
    }

    /** SetterThatIgnoresPrototypeProperties ( thisValue, home, p, v ) (10.4.7). */
    private static void setIgnoringPrototype(
            Context cx, Object thisObj, Scriptable home, Object key, Object value) {
        if (value == null) {
            value = Undefined.instance;
        }
        if (!ScriptRuntime.isObject(thisObj)) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(thisObj));
        }
        if (thisObj == home) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", key.toString());
        }
        Scriptable o = (Scriptable) thisObj;
        if (o instanceof ScriptableObject
                && ((ScriptableObject) o).getOwnPropertyDescriptor(cx, key) == null) {
            ((ScriptableObject) o)
                    .defineOwnProperty(
                            cx, key, new ScriptableObject.DescriptorInfo(true, true, true, value));
        } else if (key instanceof SymbolKey) {
            ScriptableObject.putProperty(o, (SymbolKey) key, value);
        } else {
            ScriptableObject.putProperty(o, (String) key, value);
        }
    }

    /** Iterator ( ) (27.1.3.1): abstract, only a subclass constructs. */
    private static Scriptable js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (nt == f || !(nt instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.abstract");
        }
        NativeObject iterator = new NativeObject();
        iterator.setPrototype(ScriptRuntime.findPrototype(f, nt));
        iterator.setParentScope(s);
        return iterator;
    }

    /** Iterator.from ( O ) (27.1.3.2.1). */
    private static Object js_from(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        IteratorRecord record = getIteratorFlattenable(cx, s, arg(args, 0), true);
        Scriptable prototype = iteratorPrototype(s);
        for (Scriptable p = record.iterator.getPrototype(); p != null; p = p.getPrototype()) {
            if (p == prototype) {
                return record.iterator;
            }
        }
        return new WrapForValidIterator(record, s);
    }

    /**
     * Iterator.concat ( ...items ) (27.1.3.2.1): the items are checked now, each iterator is opened
     * when the concatenation reaches it.
     */
    private static Object js_concat(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Callable[] methods = new Callable[args.length];
        for (int i = 0; i < args.length; i++) {
            if (!ScriptRuntime.isObject(args[i])) {
                throw ScriptRuntime.typeErrorById(
                        "msg.arg.not.object", ScriptRuntime.typeof(args[i]));
            }
            methods[i] = ScriptRuntime.getMethod(cx, s, args[i], SymbolKey.ITERATOR);
            if (methods[i] == null) {
                throw ScriptRuntime.typeErrorById(
                        "msg.not.iterable", ScriptRuntime.toString(args[i]));
            }
        }
        return NativeIteratorHelper.concat(s, args.clone(), methods);
    }

    /** A Wrap for Valid Iterator object, which Iterator.from returns (27.1.3.2.1.1). */
    private static final class WrapForValidIterator extends ScriptableObject {
        @Serial private static final long serialVersionUID = 1L;

        private final IteratorRecord iterated;

        WrapForValidIterator(IteratorRecord iterated, VarScope scope) {
            this.iterated = iterated;
            TopLevel top = ScriptableObject.getTopLevelScope(scope);
            setParentScope(top);
            setPrototype((Scriptable) ScriptableObject.getTopScopeValue(top, WRAP_TAG));
        }

        @Override
        public String getClassName() {
            return "Object";
        }
    }

    /** %WrapForValidIteratorPrototype%.next ( ) (27.1.3.2.1.1.1). */
    private static Object wrap_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        IteratorRecord record =
                LambdaConstructor.convertThisObject(thisObj, WrapForValidIterator.class).iterated;
        if (!(record.nextMethod instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(record.nextMethod, "next");
        }
        return ((Callable) record.nextMethod).call(cx, s, record.iterator, ScriptRuntime.emptyArgs);
    }

    /** %WrapForValidIteratorPrototype%.return ( ) (27.1.3.2.1.1.2). */
    private static Object wrap_return(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable iterator =
                LambdaConstructor.convertThisObject(thisObj, WrapForValidIterator.class)
                        .iterated
                        .iterator;
        Callable returnMethod = ScriptRuntime.getMethod(cx, s, iterator, "return");
        if (returnMethod == null) {
            return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
        }
        return returnMethod.call(cx, s, iterator, ScriptRuntime.emptyArgs);
    }

    private static Object js_iterator(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return thisObj;
    }

    private static Object js_map(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return lazy(cx, s, thisObj, args, NativeIteratorHelper.Kind.MAP);
    }

    private static Object js_filter(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return lazy(cx, s, thisObj, args, NativeIteratorHelper.Kind.FILTER);
    }

    private static Object js_flatMap(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return lazy(cx, s, thisObj, args, NativeIteratorHelper.Kind.FLAT_MAP);
    }

    private static Object js_take(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return limited(cx, s, thisObj, args, NativeIteratorHelper.Kind.TAKE);
    }

    private static Object js_drop(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return limited(cx, s, thisObj, args, NativeIteratorHelper.Kind.DROP);
    }

    /** Iterator.prototype.map, filter and flatMap (27.1.4.3, 27.1.4.5, 27.1.4.6). */
    private static Object lazy(
            Context cx, VarScope s, Object thisObj, Object[] args, NativeIteratorHelper.Kind kind) {
        Scriptable o = thisIterator(thisObj);
        Callable callback = callbackOrClose(cx, s, o, arg(args, 0));
        return new NativeIteratorHelper(s, kind, getIteratorDirect(o), callback, 0);
    }

    /** Iterator.prototype.take and drop (27.1.4.2, 27.1.4.11). */
    private static Object limited(
            Context cx, VarScope s, Object thisObj, Object[] args, NativeIteratorHelper.Kind kind) {
        Scriptable o = thisIterator(thisObj);
        double limit;
        try {
            limit = ScriptRuntime.toNumber(arg(args, 0));
        } catch (RhinoException e) {
            closeOnThrow(cx, s, o);
            throw e;
        }
        if (Double.isNaN(limit) || ScriptRuntime.toInteger(limit) < 0) {
            closeOnThrow(cx, s, o);
            throw ScriptRuntime.rangeErrorById(
                    "msg.iterator.bad.limit", ScriptRuntime.toString(limit));
        }
        return new NativeIteratorHelper(
                s, kind, getIteratorDirect(o), null, ScriptRuntime.toInteger(limit));
    }

    /** Iterator.prototype.reduce ( reducer [ , initialValue ] ) (27.1.4.8). */
    private static Object js_reduce(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = thisIterator(thisObj);
        Callable reducer = callbackOrClose(cx, s, o, arg(args, 0));
        IteratorRecord iterated = getIteratorDirect(o);
        Object accumulator;
        long counter;
        if (args.length < 2) {
            Scriptable first = step(cx, s, iterated);
            if (first == null) {
                throw ScriptRuntime.typeErrorById("msg.empty.iterator.reduce");
            }
            accumulator = value(first);
            counter = 1;
        } else {
            accumulator = args[1];
            counter = 0;
        }
        for (Scriptable r; (r = step(cx, s, iterated)) != null; counter++) {
            accumulator = callOrClose(cx, s, iterated, reducer, accumulator, value(r), counter);
        }
        return accumulator;
    }

    /** Iterator.prototype.toArray ( ) (27.1.4.12). */
    private static Object js_toArray(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        IteratorRecord iterated = getIteratorDirect(thisIterator(thisObj));
        List<Object> items = new ArrayList<>();
        for (Scriptable r; (r = step(cx, s, iterated)) != null; ) {
            items.add(value(r));
        }
        return cx.newArray(ScriptableObject.getTopLevelScope(s), items.toArray());
    }

    /** Iterator.prototype.forEach ( procedure ) (27.1.4.5). */
    private static Object js_forEach(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = thisIterator(thisObj);
        Callable procedure = callbackOrClose(cx, s, o, arg(args, 0));
        IteratorRecord iterated = getIteratorDirect(o);
        long counter = 0;
        for (Scriptable r; (r = step(cx, s, iterated)) != null; counter++) {
            callOrClose(cx, s, iterated, procedure, value(r), counter);
        }
        return Undefined.instance;
    }

    private enum Search {
        SOME,
        EVERY,
        FIND
    }

    private static Object js_some(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return search(cx, s, thisObj, args, Search.SOME);
    }

    private static Object js_every(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return search(cx, s, thisObj, args, Search.EVERY);
    }

    private static Object js_find(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return search(cx, s, thisObj, args, Search.FIND);
    }

    /** Iterator.prototype.some, every and find (27.1.4.10, 27.1.4.1, 27.1.4.4). */
    private static Object search(
            Context cx, VarScope s, Object thisObj, Object[] args, Search mode) {
        Scriptable o = thisIterator(thisObj);
        Callable predicate = callbackOrClose(cx, s, o, arg(args, 0));
        IteratorRecord iterated = getIteratorDirect(o);
        long counter = 0;
        for (Scriptable r; (r = step(cx, s, iterated)) != null; counter++) {
            Object value = value(r);
            boolean matched =
                    ScriptRuntime.toBoolean(
                            callOrClose(cx, s, iterated, predicate, value, counter));
            if (matched != (mode == Search.EVERY)) {
                close(cx, s, iterated.iterator);
                return mode == Search.FIND ? value : Boolean.valueOf(mode == Search.SOME);
            }
        }
        return mode == Search.FIND ? Undefined.instance : Boolean.valueOf(mode == Search.EVERY);
    }

    private static Object arg(Object[] args, int index) {
        return index < args.length ? args[index] : Undefined.instance;
    }

    private static Scriptable thisIterator(Object thisObj) {
        if (!ScriptRuntime.isObject(thisObj)) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(thisObj));
        }
        return (Scriptable) thisObj;
    }

    /** The callback, or IteratorClose(iterated, a TypeError) when it is not callable. */
    private static Callable callbackOrClose(Context cx, VarScope s, Scriptable o, Object fn) {
        if (fn instanceof Callable) {
            return (Callable) fn;
        }
        closeOnThrow(cx, s, o);
        throw ScriptRuntime.notFunctionError(fn);
    }

    /** Calls the callback with the arguments and a counter, closing iterated if it throws. */
    static Object callOrClose(
            Context cx,
            VarScope s,
            IteratorRecord iterated,
            Callable callback,
            Object value,
            long counter) {
        return callOrClose(cx, s, iterated, callback, new Object[] {value, (double) counter});
    }

    private static Object callOrClose(
            Context cx,
            VarScope s,
            IteratorRecord iterated,
            Callable callback,
            Object accumulator,
            Object value,
            long counter) {
        return callOrClose(
                cx, s, iterated, callback, new Object[] {accumulator, value, (double) counter});
    }

    private static Object callOrClose(
            Context cx, VarScope s, IteratorRecord iterated, Callable callback, Object[] args) {
        try {
            return callback.call(cx, s, Undefined.SCRIPTABLE_UNDEFINED, args);
        } catch (RhinoException e) {
            closeOnThrow(cx, s, iterated.iterator);
            throw e;
        }
    }

    /** GetIteratorDirect ( obj ) (7.4.2). */
    static IteratorRecord getIteratorDirect(Scriptable o) {
        return new IteratorRecord(o, get(o, ES6Iterator.NEXT_METHOD));
    }

    /** GetIteratorFlattenable ( obj, primitiveHandling ) (7.4.13). */
    static IteratorRecord getIteratorFlattenable(
            Context cx, VarScope s, Object obj, boolean iterateStrings) {
        if (!ScriptRuntime.isObject(obj) && !(iterateStrings && obj instanceof CharSequence)) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(obj));
        }
        Callable method;
        if (obj instanceof CharSequence) {
            // GetMethod ( obj, @@iterator ): a getter receives the string itself
            Object m = ScriptRuntime.getV(cx, s, obj, SymbolKey.ITERATOR);
            if (m == null || Undefined.isUndefined(m)) {
                method = null;
            } else if (m instanceof Callable) {
                method = (Callable) m;
            } else {
                throw ScriptRuntime.notFunctionError(m);
            }
        } else {
            method = ScriptRuntime.getMethod(cx, s, obj, SymbolKey.ITERATOR);
        }
        Object iterator = method == null ? obj : method.call(cx, s, obj, ScriptRuntime.emptyArgs);
        if (!ScriptRuntime.isObject(iterator)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.not.object", "[Symbol.iterator]");
        }
        return getIteratorDirect((Scriptable) iterator);
    }

    /** IteratorStep ( iteratorRecord ) (7.4.8): the result, or null when done. */
    static Scriptable step(Context cx, VarScope s, IteratorRecord record) {
        if (!(record.nextMethod instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(record.nextMethod, ES6Iterator.NEXT_METHOD);
        }
        Object result =
                ((Callable) record.nextMethod)
                        .call(cx, s, record.iterator, ScriptRuntime.emptyArgs);
        if (!ScriptRuntime.isObject(result)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.not.object", ES6Iterator.NEXT_METHOD);
        }
        Scriptable r = (Scriptable) result;
        return ScriptRuntime.toBoolean(get(r, ES6Iterator.DONE_PROPERTY)) ? null : r;
    }

    /** IteratorValue ( iterResult ) (7.4.7). */
    static Object value(Scriptable result) {
        return get(result, ES6Iterator.VALUE_PROPERTY);
    }

    private static Object get(Scriptable o, String name) {
        Object value = ScriptableObject.getProperty(o, name);
        return value == Scriptable.NOT_FOUND ? Undefined.instance : value;
    }

    /** IteratorClose ( iteratorRecord, completion ) (7.4.11) for a normal completion. */
    static void close(Context cx, VarScope s, Scriptable iterator) {
        Callable returnMethod = ScriptRuntime.getMethod(cx, s, iterator, ES6Iterator.RETURN_METHOD);
        if (returnMethod == null) {
            return;
        }
        Object result = returnMethod.call(cx, s, iterator, ScriptRuntime.emptyArgs);
        if (!ScriptRuntime.isObject(result)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.not.object", ES6Iterator.RETURN_METHOD);
        }
    }

    /** IteratorClose for a throw completion: the error being thrown wins over any other. */
    static void closeOnThrow(Context cx, VarScope s, Scriptable iterator) {
        try {
            Object returnMethod = ScriptableObject.getProperty(iterator, ES6Iterator.RETURN_METHOD);
            if (returnMethod instanceof Callable) {
                ((Callable) returnMethod).call(cx, s, iterator, ScriptRuntime.emptyArgs);
            }
        } catch (RhinoException ignored) {
            // the completion that is propagating takes precedence
        }
    }
}
