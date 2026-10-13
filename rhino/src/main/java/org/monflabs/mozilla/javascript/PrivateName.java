/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Private Name (ECMA-262 6.2.12), created for each private identifier declared by a class each
 * time the class is evaluated. It is the value of the binding {@code #x} in the scope of the class
 * body; {@code obj.#x} is an element access with it as the key, and the static helpers implement
 * the private element operations (7.3.26 to 7.3.32).
 */
public final class PrivateName implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String description;

    PrivateName(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }

    /** A private method or accessor (PrivateElement of kind method or accessor). */
    static final class Method implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final PrivateName name;
        final Object value;
        Object getter;
        Object setter;

        Method(PrivateName name, Object value, Object getter, Object setter) {
            this.name = name;
            this.value = value;
            this.getter = getter;
            this.setter = setter;
        }

        boolean isAccessor() {
            return value == null;
        }
    }

    private static final Map<Scriptable, Map<PrivateName, Object>> OTHERS = new WeakHashMap<>();

    /** The private elements of obj (its [[PrivateElements]]); field values are stored as is. */
    private static Map<PrivateName, Object> elements(Scriptable obj, boolean create) {
        if (obj instanceof ScriptableObject) {
            ScriptableObject so = (ScriptableObject) obj;
            if (so.privateElements == null && create) {
                so.privateElements = new IdentityHashMap<>();
            }
            return so.privateElements;
        }
        synchronized (OTHERS) {
            Map<PrivateName, Object> map = OTHERS.get(obj);
            if (map == null && create) {
                map = new IdentityHashMap<>();
                OTHERS.put(obj, map);
            }
            return map;
        }
    }

    private static final Object NOT_FOUND = new Object();

    private static Object find(Object obj, PrivateName name) {
        if (!(obj instanceof Scriptable)) {
            return NOT_FOUND;
        }
        Map<PrivateName, Object> map = elements((Scriptable) obj, false);
        if (map == null) {
            return NOT_FOUND;
        }
        Object element = map.get(name);
        return element == null && !map.containsKey(name) ? NOT_FOUND : element;
    }

    /** PrivateFieldAdd (7.3.28). */
    static void addField(Scriptable obj, PrivateName name, Object value) {
        Map<PrivateName, Object> map = elements(obj, true);
        if (map.containsKey(name)) {
            throw ScriptRuntime.typeErrorById("msg.private.add.twice", name.description);
        }
        map.put(name, value);
    }

    /** PrivateMethodOrAccessorAdd (7.3.29). */
    static void addMethod(Scriptable obj, Method method) {
        Map<PrivateName, Object> map = elements(obj, true);
        if (map.containsKey(method.name)) {
            throw ScriptRuntime.typeErrorById("msg.private.add.twice", method.name.description);
        }
        map.put(method.name, method);
    }

    /** PrivateGet (7.3.31). */
    static Object get(Object obj, PrivateName name, Context cx, VarScope scope) {
        Object element = find(obj, name);
        if (element == NOT_FOUND) {
            throw ScriptRuntime.typeErrorInRealm(
                    cx, scope, "msg.private.missing", name.description);
        }
        if (!(element instanceof Method)) {
            return element;
        }
        Method method = (Method) element;
        if (!method.isAccessor()) {
            return method.value;
        }
        if (!(method.getter instanceof Callable)) {
            throw ScriptRuntime.typeErrorInRealm(
                    cx, scope, "msg.private.no.getter", name.description);
        }
        return ((Callable) method.getter)
                .call(cx, scope, (Scriptable) obj, ScriptRuntime.emptyArgs);
    }

    /** PrivateSet (7.3.32). */
    static void set(Object obj, PrivateName name, Object value, Context cx, VarScope scope) {
        Object element = find(obj, name);
        if (element == NOT_FOUND) {
            throw ScriptRuntime.typeErrorInRealm(
                    cx, scope, "msg.private.missing", name.description);
        }
        if (!(element instanceof Method)) {
            elements((Scriptable) obj, false).put(name, value);
            return;
        }
        Method method = (Method) element;
        if (!method.isAccessor()) {
            throw ScriptRuntime.typeErrorInRealm(
                    cx, scope, "msg.private.method.write", name.description);
        }
        if (!(method.setter instanceof Callable)) {
            throw ScriptRuntime.typeErrorInRealm(
                    cx, scope, "msg.private.no.setter", name.description);
        }
        ((Callable) method.setter).call(cx, scope, (Scriptable) obj, new Object[] {value});
    }

    /** {@code #x in obj} (13.10.1). */
    static boolean has(Object obj, PrivateName name) {
        if (!(obj instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.in.not.object");
        }
        return find(obj, name) != NOT_FOUND;
    }
}
