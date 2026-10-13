/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.io.Serial;

/**
 * This class implements the "arguments" object.
 *
 * <p>See ECMA 10.1.8
 *
 * @see org.monflabs.mozilla.javascript.NativeCall
 * @author Norris Boyd
 */
class Arguments extends ScriptableObject {
    @Serial private static final long serialVersionUID = 4275508002492040609L;

    private static final String CLASS_NAME = "Arguments";

    // Fields to hold caller, callee and length properties,
    // where NOT_FOUND value tags deleted properties.
    // In addition, the 'caller' arguments is only available in JS <= 1.3 scripts
    private Object calleeObj;
    private Object lengthObj;

    private NativeCall activation;

    /**
     * The parameter map of a mapped arguments object (ECMA-262 10.4.4): {@code mapped[i]} is true
     * while {@code arguments[i]} is an alias of parameter i. Only parameters can be mapped, so it
     * is no longer than the parameter list.
     */
    private final boolean[] mapped;

    /** Before VERSION_ECMASCRIPT: the stock Rhino arguments object (see the legacy methods). */
    private final boolean legacy;

    // The values of the indexed properties that are not slots yet: an index i with args[i] !=
    // NOT_FOUND is an ordinary writable, enumerable and configurable data property. It becomes a
    // slot (and NOT_FOUND here) when it is redefined or deleted, so that a call with many arguments
    // does not create one slot per argument. Initially activation.originalArgs, copied on write.
    private Object[] args;

    public Arguments(NativeCall activation, Context cx) {
        this.activation = activation;

        VarScope parent = activation.getParentScope();
        setParentScope(parent);
        setPrototype(ScriptableObject.getObjectPrototype(parent));

        args = activation.originalArgs;
        lengthObj = Integer.valueOf(args.length);
        legacy = !cx.isLatestEcmaScript();
        boolean[] map = null;
        int mappable = legacy ? 0 : Math.min(args.length, activation.function.getParamCount());
        for (int i = 0; i < mappable; i++) {
            if (isMappedParameter(activation.function, i)) {
                if (map == null) {
                    map = new boolean[mappable];
                }
                map[i] = true;
            }
        }
        mapped = map;

        JSFunction f = activation.function;
        calleeObj = f;

        var iter =
                TopLevel.getBuiltinPrototype(
                        ScriptableObject.getTopLevelScope(parent), TopLevel.Builtins.Array);

        defineProperty(SymbolKey.ITERATOR, iter.get("values", iter), ScriptableObject.DONTENUM);
        defineProperty("length", lengthObj, ScriptableObject.DONTENUM);

        if (f.isStrict()) {
            // ECMAScript2015
            // 9.4.4.6 CreateUnmappedArgumentsObject(argumentsList)
            //   8. Perform DefinePropertyOrThrow(obj, "caller", PropertyDescriptor {[[Get]]:
            // %ThrowTypeError%,
            //      [[Set]]: %ThrowTypeError%, [[Enumerable]]: false, [[Configurable]]: false}).
            //   9. Perform DefinePropertyOrThrow(obj, "callee", PropertyDescriptor {[[Get]]:
            // %ThrowTypeError%,
            //      [[Set]]: %ThrowTypeError%, [[Enumerable]]: false, [[Configurable]]: false}).
            BaseFunction typeErrorThrower = ScriptRuntime.typeErrorThrower(cx);
            int version = cx.getLanguageVersion();
            if (version <= Context.VERSION_1_8) {
                setGetterOrSetter("caller", 0, typeErrorThrower, true);
                setGetterOrSetter("caller", 0, typeErrorThrower, false);
                setGetterOrSetter("callee", 0, typeErrorThrower, true);
                setGetterOrSetter("callee", 0, typeErrorThrower, false);
                setAttributes("caller", DONTENUM | PERMANENT);
                setAttributes("callee", DONTENUM | PERMANENT);
            } else {
                setGetterOrSetter("callee", 0, typeErrorThrower, true);
                setGetterOrSetter("callee", 0, typeErrorThrower, false);
                setAttributes("callee", DONTENUM | PERMANENT);
            }
            calleeObj = null;
        } else {
            defineProperty("callee", calleeObj, ScriptableObject.DONTENUM);
        }
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    /**
     * Whether arguments[index] starts as an alias of a parameter: in a non-strict function with a
     * simple parameter list, for a parameter not hidden by a later one of the same name.
     */
    private static boolean isMappedParameter(JSFunction f, int index) {
        if (f == null
                || f.isStrict()
                || f.hasDefaultParameters()
                || f.getDescriptor().hasRestArg()) {
            return false;
        }
        int definedCount = f.getParamCount();
        if (index >= definedCount) {
            return false;
        }
        String argName = f.getParamOrVarName(index);
        for (int i = index + 1; i < definedCount; i++) {
            if (argName.equals(f.getParamOrVarName(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMapped(int index) {
        return mapped != null && index >= 0 && index < mapped.length && mapped[index];
    }

    private void unmap(int index) {
        if (isMapped(index)) {
            mapped[index] = false;
        }
    }

    private void putIntoActivation(int index, Object value) {
        String argName = activation.function.getParamOrVarName(index);
        activation.put(argName, activation, value);
    }

    private Object getFromActivation(int index) {
        String argName = activation.function.getParamOrVarName(index);
        return activation.get(argName, activation);
    }

    private Object arg(int index) {
        if (index < 0 || args.length <= index) return NOT_FOUND;
        return args[index];
    }

    private synchronized void setArg(int index, Object value) {
        if (args == activation.originalArgs) {
            args = args.clone();
        }
        args[index] = value;
    }

    /**
     * Turns a lazily held indexed property into a slot with the same value (directly in the slot
     * map: the property already exists, even if the object is no longer extensible).
     */
    private void materialize(int index) {
        Object value = arg(index);
        if (value != NOT_FOUND) {
            Slot<Scriptable> slot = getMap().modify(this, null, index, 0);
            slot.value = isMapped(index) ? getFromActivation(index) : value;
            setArg(index, NOT_FOUND);
        }
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return arg(index) != NOT_FOUND || super.has(index, start);
    }

    @Override
    public Object get(int index, Scriptable start) {
        if (legacy) {
            return legacyGet(index, start);
        }
        if (isMapped(index)) {
            return getFromActivation(index);
        }
        Object value = arg(index);
        return value != NOT_FOUND ? value : super.get(index, start);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        if (legacy) {
            legacyPut(index, start, value);
            return;
        }
        if (start == this && isMapped(index)) {
            putIntoActivation(index, value);
        }
        if (start == this && arg(index) != NOT_FOUND) {
            setArg(index, value);
        } else {
            super.put(index, start, value);
        }
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        super.put(name, start, value);
    }

    @Override
    public void put(Symbol key, Scriptable start, Object value) {
        super.put(key, start, value);
    }

    @Override
    public void delete(int index) {
        if (legacy) {
            legacyDelete(index);
            return;
        }
        if (arg(index) != NOT_FOUND) {
            setArg(index, NOT_FOUND);
            unmap(index);
            return;
        }
        super.delete(index);
        if (!super.has(index, this)) {
            unmap(index);
        }
    }

    @Override
    protected Object[] getIds(
            CompoundOperationMap<Scriptable> map, boolean getNonEnumerable, boolean getSymbols) {
        if (legacy) {
            return legacyGetIds(map, getNonEnumerable, getSymbols);
        }
        Object[] ids = super.getIds(map, getNonEnumerable, getSymbols);
        int lazy = 0;
        for (Object arg : args) {
            if (arg != NOT_FOUND) lazy++;
        }
        if (lazy == 0) {
            return ids;
        }
        // integer keys in ascending order, then the other keys in their order
        java.util.TreeSet<Integer> indexes = new java.util.TreeSet<>();
        java.util.List<Object> others = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i] != NOT_FOUND) indexes.add(i);
        }
        for (Object id : ids) {
            if (id instanceof Integer) indexes.add((Integer) id);
            else others.add(id);
        }
        Object[] result = new Object[indexes.size() + others.size()];
        int n = 0;
        for (Integer index : indexes) result[n++] = index;
        for (Object other : others) result[n++] = other;
        return result;
    }

    @Override
    protected DescriptorInfo getOwnPropertyDescriptor(Context cx, Object id) {
        if (legacy) {
            return legacyGetOwnPropertyDescriptor(cx, id);
        }
        int index = arrayIndex(id);
        Object value = arg(index);
        if (value != NOT_FOUND) {
            return buildDataDescriptor(isMapped(index) ? getFromActivation(index) : value, EMPTY);
        }
        DescriptorInfo desc = super.getOwnPropertyDescriptor(cx, id);
        if (desc != null && isMapped(index)) {
            desc.value = getFromActivation(index);
        }
        return desc;
    }

    @Override
    protected boolean defineOwnProperty(
            Context cx, Object id, DescriptorInfo desc, boolean checkValid) {
        if (legacy) {
            return legacyDefineOwnProperty(cx, id, desc, checkValid);
        }
        int index = arrayIndex(id);
        materialize(index);
        if (!isMapped(index)) {
            return super.defineOwnProperty(cx, id, desc, checkValid);
        }
        // ECMA-262 10.4.4.2 [[DefineOwnProperty]] of a mapped arguments object
        super.put(index, this, getFromActivation(index));
        DescriptorInfo newDesc = desc;
        if (!desc.isAccessorDescriptor()
                && desc.value == NOT_FOUND
                && Boolean.FALSE.equals(desc.writable)) {
            newDesc =
                    new DescriptorInfo(
                            desc.enumerable,
                            desc.writable,
                            desc.configurable,
                            NOT_FOUND,
                            NOT_FOUND,
                            getFromActivation(index));
        }
        boolean allowed = super.defineOwnProperty(cx, id, newDesc, checkValid);
        if (!allowed) {
            return false;
        }
        if (desc.isAccessorDescriptor()) {
            unmap(index);
        } else {
            if (desc.value != NOT_FOUND) {
                putIntoActivation(index, desc.value);
            }
            if (Boolean.FALSE.equals(desc.writable)) {
                unmap(index);
            }
        }
        return true;
    }

    /** The index named by a property key, or -1. */
    private static int arrayIndex(Object id) {
        if (ScriptRuntime.isSymbol(id) || id instanceof Scriptable) {
            return -1;
        }
        double d = ScriptRuntime.toNumber(id);
        int index = (int) d;
        return d == index ? index : -1;
    }

    // The arguments object of stock Rhino, before VERSION_ECMASCRIPT: an index is shared with its
    // parameter while it holds its initial value slot (not after a delete or a redefinition as an
    // accessor or non-writable), and redefinitions apply the descriptor as given.

    private boolean sharedWithActivation(int index) {
        JSFunction f = activation.function;
        if (f.isStrict() || f.hasDefaultParameters()) {
            return false;
        }
        int definedCount = f.getParamCount();
        if (index < definedCount) {
            // an argument hidden by a later one of the same name is not shared
            String argName = f.getParamOrVarName(index);
            for (int i = index + 1; i < definedCount; i++) {
                if (argName.equals(f.getParamOrVarName(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void replaceArg(int index, Object value) {
        if (sharedWithActivation(index)) {
            putIntoActivation(index, value);
        }
        setArg(index, value);
    }

    private void removeArg(int index) {
        if (args[index] != NOT_FOUND) {
            setArg(index, NOT_FOUND);
        }
    }

    private Object legacyGet(int index, Scriptable start) {
        Object value = arg(index);
        if (value == NOT_FOUND) {
            return super.get(index, start);
        }
        return sharedWithActivation(index) ? getFromActivation(index) : value;
    }

    private void legacyPut(int index, Scriptable start, Object value) {
        if (arg(index) == NOT_FOUND) {
            super.put(index, start, value);
        } else {
            replaceArg(index, value);
        }
    }

    private void legacyDelete(int index) {
        if (0 <= index && index < args.length) {
            removeArg(index);
        }
        super.delete(index);
    }

    private Object[] legacyGetIds(
            CompoundOperationMap<Scriptable> map, boolean getNonEnumerable, boolean getSymbols) {
        Object[] ids = super.getIds(map, getNonEnumerable, getSymbols);
        if (args.length != 0) {
            boolean[] present = new boolean[args.length];
            int extraCount = args.length;
            for (Object id : ids) {
                if (id instanceof Integer) {
                    int index = (Integer) id;
                    if (0 <= index && index < args.length && !present[index]) {
                        present[index] = true;
                        extraCount--;
                    }
                }
            }
            if (!getNonEnumerable) { // avoid adding args which were redefined to non-enumerable
                for (int i = 0; i < present.length; i++) {
                    if (!present[i] && super.has(i, this)) {
                        present[i] = true;
                        extraCount--;
                    }
                }
            }
            if (extraCount != 0) {
                Object[] tmp = new Object[extraCount + ids.length];
                System.arraycopy(ids, 0, tmp, extraCount, ids.length);
                ids = tmp;
                int offset = 0;
                for (int i = 0; i != args.length; ++i) {
                    if (!present[i]) {
                        ids[offset++] = Integer.valueOf(i);
                    }
                }
            }
        }
        return ids;
    }

    private DescriptorInfo legacyGetOwnPropertyDescriptor(Context cx, Object id) {
        int index = arrayIndex(id);
        Object value = arg(index);
        if (value == NOT_FOUND) {
            return super.getOwnPropertyDescriptor(cx, id);
        }
        if (sharedWithActivation(index)) {
            value = getFromActivation(index);
        }
        if (super.has(index, this)) { // the descriptor has been redefined
            DescriptorInfo desc = super.getOwnPropertyDescriptor(cx, id);
            desc.value = value;
            return desc;
        }
        return buildDataDescriptor(value, EMPTY);
    }

    private boolean legacyDefineOwnProperty(
            Context cx, Object id, DescriptorInfo desc, boolean checkValid) {
        super.defineOwnProperty(cx, id, desc, checkValid);
        int index = arrayIndex(id);
        Object value = arg(index);
        if (value == NOT_FOUND) {
            return true;
        }
        if (desc.isAccessorDescriptor()) {
            removeArg(index);
            return true;
        }
        Object newValue = desc.value;
        if (newValue == NOT_FOUND) {
            return true;
        }
        replaceArg(index, newValue);
        if (isFalse(desc.writable)) {
            removeArg(index);
        }
        return true;
    }

    static final class ReadonlyArguments extends Arguments {
        private boolean initialized;

        public ReadonlyArguments(Arguments arguments, Context cx) {
            super(arguments.activation, cx);
            initialized = true;
        }

        @Override
        public void put(int index, Scriptable start, Object value) {
            if (initialized) {
                return;
            }
            super.put(index, start, value);
        }

        @Override
        public void put(String name, Scriptable start, Object value) {
            if (initialized) {
                return;
            }
            super.put(name, start, value);
        }

        @Override
        public void put(Symbol key, Scriptable start, Object value) {
            if (initialized) {
                return;
            }
            super.put(key, start, value);
        }

        @Override
        public void delete(int index) {
            if (initialized) {
                return;
            }
            super.delete(index);
        }

        @Override
        public void delete(String name) {
            if (initialized) {
                return;
            }
            super.delete(name);
        }

        @Override
        public void delete(Symbol key) {
            if (initialized) {
                return;
            }
            super.delete(key);
        }
    }
}
