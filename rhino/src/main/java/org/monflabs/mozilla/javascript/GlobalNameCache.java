package org.monflabs.mozilla.javascript;

import java.lang.ref.WeakReference;

/**
 * The binding that a name of top-level code resolves to, cached for one access site: an own
 * property of the global object, or a global let/const. A cached access reaches the slot directly
 * instead of looking the name up in the top scope and then in the global object.
 *
 * <p>The cache is used only when the lookup would find the same slot: the scope is the cached top
 * scope, the top scope has not gained bindings since (it only ever gains them, so its size tells),
 * and the slot is still in its map ({@link Slot#stale}). The scope and the slot are held weakly, so
 * that code which outlives a scope does not keep it alive.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GlobalNameCache {

    private static final class Entry {
        final WeakReference<TopLevel> top;
        final WeakReference<Slot> slot;
        final int topSize;

        /** A global let/const, in the top scope, rather than a property of the global object. */
        final boolean lexical;

        Entry(TopLevel top, Slot slot, int topSize, boolean lexical) {
            this.top = new WeakReference<>(top);
            this.slot = new WeakReference<>(slot);
            this.topSize = topSize;
            this.lexical = lexical;
        }
    }

    /** Top scopes and global objects whose name lookups the cache can replace. */
    private static final ClassValue<Boolean> CACHEABLE =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    if (TopLevel.class.isAssignableFrom(type)) {
                        return declaredBy(type, TopLevel.class, "get", String.class, VarScope.class)
                                && declaredBy(
                                        type, TopLevel.class, "has", String.class, VarScope.class)
                                && declaredBy(
                                        type,
                                        TopLevel.class,
                                        "put",
                                        String.class,
                                        VarScope.class,
                                        Object.class);
                    }
                    // the global object of JavaImporter looks up its own properties first
                    boolean ownFirst = type == ImporterTopLevel.ImporterGlobalThis.class;
                    return (ownFirst
                                    || declaredBy(
                                            type,
                                            ScriptableObject.class,
                                            "get",
                                            String.class,
                                            Scriptable.class))
                            && (ownFirst
                                    || declaredBy(
                                            type,
                                            ScriptableObject.class,
                                            "has",
                                            String.class,
                                            Scriptable.class))
                            && declaredBy(
                                    type,
                                    ScriptableObject.class,
                                    "put",
                                    String.class,
                                    Scriptable.class,
                                    Object.class)
                            && declaredBy(
                                    type,
                                    ScriptableObject.class,
                                    "putOwnProperty",
                                    String.class,
                                    Scriptable.class,
                                    Object.class,
                                    boolean.class)
                            && declaredBy(type, ScriptableObject.class, "isExtensible");
                }
            };

    private static boolean declaredBy(
            Class<?> type, Class<?> declarer, String name, Class<?>... params) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params).getDeclaringClass() == declarer;
            } catch (NoSuchMethodException e) {
                // look in the superclass
            }
        }
        return false;
    }

    private final String name;
    private Entry entry;

    /** Set when the top scope or the global object cannot be cached, so as not to try again. */
    private boolean disabled;

    public GlobalNameCache(String name) {
        this.name = name;
    }

    /** The cached slot, if it is the one a lookup of the name from this scope would find. */
    private Slot slot(Entry e, Context cx, VarScope scope) {
        if (e == null || scope == null || e.top.get() != scope || cx.useDynamicScope) {
            return null;
        }
        Slot slot = e.slot.get();
        if (slot == null || slot.stale || ((TopLevel) scope).getMap().size() != e.topSize) {
            return null;
        }
        return slot;
    }

    /** Returned by {@link #value} where the lookup must decide (TDZ, not found). */
    private static final Object MISS = new Object();

    /** The value of the cached slot, or {@link #MISS}. */
    private Object value(Entry e, Slot slot, VarScope scope) {
        TopLevel top = (TopLevel) scope;
        Object value = e.lexical ? slot.getValue(top) : slot.getValue(top.getGlobalThis());
        return value == UniqueTag.UNINITIALIZED || value == Scriptable.NOT_FOUND ? MISS : value;
    }

    /** Caches the binding the name has from this scope, after a lookup. */
    private void update(Context cx, VarScope scope) {
        if (disabled || cx.useDynamicScope || !(scope instanceof TopLevel)) {
            return;
        }
        TopLevel top = (TopLevel) scope;
        SlotMap topMap = top.getMap();
        if (!CACHEABLE.get(top.getClass()) || !isPlainMap(topMap)) {
            disabled = true;
            return;
        }
        Slot slot = topMap.query(name, 0);
        if (slot != null) {
            entry = new Entry(top, slot, topMap.size(), true);
            return;
        }
        ScriptableObject global = top.getGlobalThis();
        SlotMap map = global.getMap();
        if (!CACHEABLE.get(global.getClass()) || !isPlainMap(map)) {
            disabled = true;
            return;
        }
        slot = map.query(name, 0);
        if (slot != null) {
            entry = new Entry(top, slot, topMap.size(), false);
        }
    }

    /** A map that marks the slots it drops as stale, and is not shared between threads. */
    private static boolean isPlainMap(SlotMap map) {
        return map.getClass() == EmbeddedSlotMap.class || map.getClass() == HashSlotMap.class;
    }

    /** {@link ScriptRuntime#name(Context, VarScope, String)} */
    public Object name(Context cx, VarScope scope) {
        Entry e = entry;
        Slot slot = slot(e, cx, scope);
        if (slot != null) {
            Object value = value(e, slot, scope);
            if (value != MISS) {
                return value;
            }
        }
        Object result = ScriptRuntime.name(cx, scope, name);
        update(cx, scope);
        return result;
    }

    /** {@link ScriptRuntime#bind(Context, VarScope, String)} */
    public VarScope bind(Context cx, VarScope scope) {
        if (slot(entry, cx, scope) != null) {
            return scope;
        }
        VarScope result = ScriptRuntime.bind(cx, scope, name);
        update(cx, scope);
        return result;
    }

    /**
     * Whether the cached slot is an initialized global let in a scope that accepts writes: a const,
     * the temporal dead zone and any other kind of slot take the lookup, which throws as needed.
     */
    private static boolean isWritableLet(Slot slot, VarScope top) {
        return slot.getClass() == StandardSlot.class
                && slot.getAttributes() == ScriptableObject.PERMANENT
                && slot.value != UniqueTag.UNINITIALIZED
                && !((TopLevel) top).isSealed();
    }

    // the let paths are methods of their own, which keeps the methods that global vars take small
    // enough to inline into compiled scripts

    private Object setLet(Slot slot, VarScope bound, Object value, Context cx, VarScope scope) {
        if (isWritableLet(slot, bound)) {
            slot.value = value;
            return value;
        }
        Object result = ScriptRuntime.setName(bound, value, cx, scope, name);
        update(cx, bound);
        return result;
    }

    private Object incrDecrLet(Slot slot, VarScope scope, Context cx, int incrDecrMask) {
        if (isWritableLet(slot, scope) && slot.value instanceof Number) {
            Number number = (Number) slot.value;
            Number result = ScriptRuntime.incrDecrNumber(number, incrDecrMask);
            slot.value = result;
            return (incrDecrMask & Node.POST_FLAG) != 0 ? number : result;
        }
        Object result = ScriptRuntime.nameIncrDecr(scope, name, cx, incrDecrMask);
        update(cx, scope);
        return result;
    }

    /** {@link ScriptRuntime#setName(VarScope, Object, Context, VarScope, String)} */
    public Object setName(VarScope bound, Object value, Context cx, VarScope scope) {
        Entry e = entry;
        Slot slot = slot(e, cx, bound);
        if (slot != null && e.lexical) {
            return setLet(slot, bound, value, cx, scope);
        }
        if (slot != null) {
            ScriptableObject global = ((TopLevel) bound).getGlobalThis();
            if (global.isExtensible() && !global.isSealed()) {
                slot.setValue(value, global, global, cx.isStrictMode());
                return value;
            }
        }
        Object result = ScriptRuntime.setName(bound, value, cx, scope, name);
        if (bound != null) {
            update(cx, bound);
        }
        return result;
    }

    /** {@link ScriptRuntime#nameIncrDecr(VarScope, String, Context, int)} */
    public Object nameIncrDecr(VarScope scope, Context cx, int incrDecrMask) {
        Entry e = entry;
        Slot slot = slot(e, cx, scope);
        // a data property holding a number: reading and converting it has no side effect
        if (slot != null && e.lexical) {
            return incrDecrLet(slot, scope, cx, incrDecrMask);
        }
        if (slot != null && slot.getClass() == StandardSlot.class) {
            ScriptableObject global = ((TopLevel) scope).getGlobalThis();
            if (slot.value instanceof Number && global.isExtensible() && !global.isSealed()) {
                Number number = (Number) slot.value;
                Number result = ScriptRuntime.incrDecrNumber(number, incrDecrMask);
                slot.setValue(result, global, global, cx.isStrictMode());
                return (incrDecrMask & Node.POST_FLAG) != 0 ? number : result;
            }
        }
        Object result = ScriptRuntime.nameIncrDecr(scope, name, cx, incrDecrMask);
        update(cx, scope);
        return result;
    }

    /** {@link ScriptRuntime#getNameAndThis(String, Context, VarScope)} */
    public ScriptRuntime.LookupResult getNameAndThis(Context cx, VarScope scope) {
        Entry e = entry;
        Slot slot = slot(e, cx, scope);
        if (slot != null) {
            Object value = value(e, slot, scope);
            if (value != MISS) {
                return new ScriptRuntime.LookupResult(value, Undefined.SCRIPTABLE_UNDEFINED, name);
            }
        }
        ScriptRuntime.LookupResult result = ScriptRuntime.getNameAndThis(name, cx, scope);
        update(cx, scope);
        return result;
    }
}
