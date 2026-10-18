package org.monflabs.mozilla.javascript;

import java.lang.ref.WeakReference;

/**
 * The property that a named property access found on the prototype chain of its receiver, cached
 * for one access site: a cached access looks the name up in the receiver only, instead of in every
 * object of the chain.
 *
 * <p>A receiver of the cached class that has the property itself is looked up directly. Otherwise
 * the cache is used only when the lookup would find the same slot: its prototype is the cached one,
 * the slot is still in its map ({@link Slot#stale}), and no object of the chain has changed since.
 * The objects of a cached chain are marked ({@link SlotMapOwner#cachedPrototype}), and adding or
 * removing a property of a marked object, replacing its map or changing its prototype invalidates
 * every cache ({@link #invalidate}). Only objects that look their properties up in a slot map that
 * reports these changes are cached. The prototype and the slot are held weakly, so that code which
 * outlives them does not keep them alive.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class PropertyCache {

    /** Incremented by every change to an object of a cached chain. */
    private static int epoch;

    /** Invalidates every cache: an object of a cached chain changed. */
    static void invalidate() {
        epoch++;
    }

    /** Objects that look their named properties up in their slot map and their prototype. */
    private static final ClassValue<Boolean> CACHEABLE =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    return ScriptableObject.class.isAssignableFrom(type)
                            && !VarScope.class.isAssignableFrom(type)
                            && declaredBy(type, "get", String.class, Scriptable.class)
                            && declaredBy(type, "getPrototype");
                }
            };

    private static boolean declaredBy(Class<?> type, String name, Class<?>... params) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params).getDeclaringClass()
                        == ScriptableObject.class;
            } catch (NoSuchMethodException e) {
                // look in the superclass
            }
        }
        return false;
    }

    /**
     * After this many attempts to fill the cache, the site is no longer cached (several receivers
     * or chains, or properties of the receivers themselves).
     */
    private static final int MAX_FILLS = 64;

    /**
     * Whether obj is an object that the cache can serve and that inherits the property: the sites
     * that get own properties are not worth a cache.
     */
    public static boolean isInherited(Object obj, String name) {
        return obj != null
                && CACHEABLE.get(obj.getClass())
                && ((ScriptableObject) obj).getMap().query(name, 0) == null
                && ScriptableObject.hasProperty((ScriptableObject) obj, name);
    }

    /** What a cache holds, replaced as a whole (code can run in several threads). */
    private static final class Entry {
        final Class<?> receiverClass;

        /** The prototype of the receivers and the slot found from it (null: an own property). */
        final WeakReference<Scriptable> prototype;

        final WeakReference<Slot> slot;
        final int epoch;

        Entry(Class<?> receiverClass, Scriptable prototype, Slot slot, int epoch) {
            this.receiverClass = receiverClass;
            this.prototype = prototype == null ? null : new WeakReference<>(prototype);
            this.slot = slot == null ? null : new WeakReference<>(slot);
            this.epoch = epoch;
        }
    }

    private Entry entry;
    private int fills;

    /**
     * The value of the property for this receiver, or {@link Scriptable#NOT_FOUND} if the cache
     * cannot tell.
     */
    private Object cached(Object obj, String name) {
        Entry e = entry;
        if (e == null || obj == null || obj.getClass() != e.receiverClass) {
            return Scriptable.NOT_FOUND;
        }
        ScriptableObject receiver = (ScriptableObject) obj;
        Slot own = receiver.getMap().query(name, 0);
        if (own != null) {
            return own.getValue(receiver);
        }
        return e.prototype == null ? Scriptable.NOT_FOUND : inherited(e, receiver);
    }

    /** {@link #cached} for a receiver without the property. */
    private static Object inherited(Entry e, ScriptableObject receiver) {
        Scriptable proto = e.prototype.get();
        if (e.epoch != epoch || proto == null || receiver.getPrototype() != proto) {
            return Scriptable.NOT_FOUND;
        }
        Slot s = e.slot.get();
        if (s == null || s.stale) {
            return Scriptable.NOT_FOUND;
        }
        return s.getValue(receiver);
    }

    /** Caches the slot that the property has on the prototype chain of obj, after a lookup. */
    private void update(Object obj, String name) {
        if (fills >= MAX_FILLS || obj == null) {
            return;
        }
        fills++;
        if (!CACHEABLE.get(obj.getClass())) {
            return;
        }
        ScriptableObject receiver = (ScriptableObject) obj;
        if (receiver.getMap().query(name, 0) != null) {
            // an own property: the cache looks it up directly
            Entry e = entry;
            if (e == null || e.receiverClass != receiver.getClass()) {
                entry = new Entry(receiver.getClass(), null, null, 0);
            }
            return;
        }
        Scriptable first = receiver.getPrototype();
        for (Scriptable p = first; p != null; ) {
            if (!CACHEABLE.get(p.getClass()) || !((ScriptableObject) p).hasPlainMap()) {
                return;
            }
            ScriptableObject proto = (ScriptableObject) p;
            proto.cachedPrototype = true;
            Slot s = proto.getMap().query(name, 0);
            if (s != null) {
                entry = new Entry(receiver.getClass(), first, s, epoch);
                return;
            }
            p = proto.getPrototype();
        }
    }

    /** {@link ScriptRuntime#getObjectProp(Object, String, Context, VarScope)} */
    public Object getObjectProp(Object obj, String name, Context cx, VarScope scope) {
        Object value = cached(obj, name);
        if (value != Scriptable.NOT_FOUND) {
            return value;
        }
        Object result = ScriptRuntime.getObjectProp(obj, name, cx, scope);
        update(obj, name);
        return result;
    }

    /** {@link ScriptRuntime#getObjectPropNoWarn(Object, String, Context, VarScope)} */
    public Object getObjectPropNoWarn(Object obj, String name, Context cx, VarScope scope) {
        Object value = cached(obj, name);
        if (value != Scriptable.NOT_FOUND) {
            return value;
        }
        Object result = ScriptRuntime.getObjectPropNoWarn(obj, name, cx, scope);
        update(obj, name);
        return result;
    }

    /** {@link ScriptRuntime#getPropAndThis(Object, String, Context, VarScope)} */
    public ScriptRuntime.LookupResult getPropAndThis(
            Object obj, String name, Context cx, VarScope scope) {
        Object value = cached(obj, name);
        if (value != Scriptable.NOT_FOUND) {
            return new ScriptRuntime.LookupResult(value, (Scriptable) obj, name);
        }
        ScriptRuntime.LookupResult result = ScriptRuntime.getPropAndThis(obj, name, cx, scope);
        update(obj, name);
        return result;
    }
}
