/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A module namespace exotic object (ECMA-262 10.4.6): a null-prototype, non-extensible object whose
 * string keys are the export names, sorted, each a writable, enumerable, non-configurable data
 * property read live from its module.
 */
final class ModuleNamespace extends ScriptableObject {
    @Serial private static final long serialVersionUID = 1L;

    // sorted by code units (String.compareTo)
    private final TreeMap<String, ModuleRecord.Binding> exports;

    ModuleNamespace(TopLevel realm, Map<String, ModuleRecord.Binding> exports) {
        this.exports = new TreeMap<>(exports);
        setParentScope(realm);
        setPrototype(null);
        defineProperty(SymbolKey.TO_STRING_TAG, "Module", READONLY | DONTENUM | PERMANENT);
        preventExtensions();
    }

    @Override
    public String getClassName() {
        return "Module";
    }

    private Object read(String name) {
        ModuleRecord.Binding b = exports.get(name);
        return b.module().readBinding(Context.getCurrentContext(), b.bindingName());
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (exports.containsKey(name)) {
            return read(name);
        }
        return super.get(name, start);
    }

    @Override
    public Object get(int index, Scriptable start) {
        return get(Integer.toString(index), start);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return exports.containsKey(name) || super.has(name, start);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return has(Integer.toString(index), start);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        // [[Set]] is false
        if (Context.isCurrentContextStrict()) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", name);
        }
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        put(Integer.toString(index), start, value);
    }

    @Override
    public void delete(String name) {
        if (exports.containsKey(name)) {
            // [[Delete]] is false: a TypeError in strict code
            if (Context.getContext().isStrictMode()) {
                throw ScriptRuntime.typeErrorById(
                        "msg.delete.property.with.configurable.false", name);
            }
            return;
        }
        super.delete(name);
    }

    @Override
    public boolean delete(Context cx, Object key) {
        if (!(key instanceof Symbol) && exports.containsKey(ScriptRuntime.toString(key))) {
            return false;
        }
        return super.delete(cx, key);
    }

    @Override
    public boolean set(Context cx, Object key, Object value, Object receiver) {
        return false; // 10.4.6.9 [[Set]]
    }

    @Override
    public void delete(int index) {
        delete(Integer.toString(index));
    }

    @Override
    protected Object[] getIds(
            CompoundOperationMap<Scriptable> map, boolean getNonEnumerable, boolean getSymbols) {
        if (!getNonEnumerable) {
            // the enumerable keys are for EnumerableOwnProperties and for-in, which read each
            // property ([[GetOwnProperty]]): an uninitialized binding throws a ReferenceError
            for (String name : exports.keySet()) {
                read(name);
            }
        }
        List<Object> ids = new ArrayList<>(exports.keySet());
        if (getSymbols) {
            for (Object id : super.getIds(map, getNonEnumerable, true)) {
                if (id instanceof Symbol) {
                    ids.add(id);
                }
            }
        }
        return ids.toArray();
    }

    @Override
    protected DescriptorInfo getOwnPropertyDescriptor(Context cx, Object id) {
        if (!(id instanceof Symbol)) {
            String name = ScriptRuntime.toString(id);
            if (exports.containsKey(name)) {
                return new DescriptorInfo(true, true, false, read(name));
            }
            return null;
        }
        return super.getOwnPropertyDescriptor(cx, id);
    }

    @Override
    protected boolean defineOwnProperty(
            Context cx, Object id, DescriptorInfo desc, boolean checkValid) {
        if (id instanceof Symbol) {
            return super.defineOwnProperty(cx, id, desc, checkValid);
        }
        // 10.4.6.6: only a redefinition that changes nothing succeeds
        DescriptorInfo current = getOwnPropertyDescriptor(cx, id);
        if (current == null
                || Boolean.TRUE.equals(desc.configurable)
                || Boolean.FALSE.equals(desc.enumerable)
                || desc.getter != NOT_FOUND
                || desc.setter != NOT_FOUND
                || Boolean.FALSE.equals(desc.writable)
                || (desc.value != NOT_FOUND && !ScriptRuntime.same(desc.value, current.value))) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", ScriptRuntime.toString(id));
        }
        return true;
    }
}
