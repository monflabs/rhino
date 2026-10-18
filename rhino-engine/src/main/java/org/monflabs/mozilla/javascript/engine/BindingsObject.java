/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.engine;

import javax.script.Bindings;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;
import org.monflabs.mozilla.javascript.Wrapper;

/**
 * This class makes the Bindings object into a Scriptable. That way, we can query and modify the
 * contents of the Bindings on demand.
 */
public class BindingsObject extends ScriptableObject {
    private final Bindings bindings;
    private final VarScope parentScope;

    BindingsObject(VarScope parentScope, Bindings bindings) {
        if (bindings == null) {
            throw new IllegalArgumentException("Bindings must not be null");
        }
        this.bindings = bindings;
        this.parentScope = parentScope;
    }

    @Override
    public String getClassName() {
        return "BindingsObject";
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (!bindings.containsKey(name)) {
            return Scriptable.NOT_FOUND;
        }
        return Context.javaToJS(bindings.get(name), parentScope);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        bindings.put(name, value instanceof Wrapper ? ((Wrapper) value).unwrap() : value);
    }

    @Override
    public void delete(String name) {
        bindings.remove(name);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return bindings.containsKey(name);
    }

    /**
     * A data property defined on the global object, such as a global function declaration, is a
     * binding.
     */
    @Override
    protected boolean defineOwnProperty(
            Context cx, Object id, DescriptorInfo desc, boolean checkValid) {
        if (id instanceof String && desc.getter == NOT_FOUND && desc.setter == NOT_FOUND) {
            String name = (String) id;
            if (desc.value != NOT_FOUND) {
                put(name, this, desc.value);
            } else if (!bindings.containsKey(name)) {
                put(name, this, Undefined.instance);
            }
            return true;
        }
        return super.defineOwnProperty(cx, id, desc, checkValid);
    }

    @Override
    protected DescriptorInfo getOwnPropertyDescriptor(Context cx, Object id) {
        if (id instanceof String && bindings.containsKey(id)) {
            return new DescriptorInfo(true, true, true, get((String) id, this));
        }
        return super.getOwnPropertyDescriptor(cx, id);
    }

    @Override
    public Object[] getIds() {
        return bindings.keySet().toArray();
    }
}
