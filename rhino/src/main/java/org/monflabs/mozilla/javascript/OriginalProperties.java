/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * Properties of built-in objects with the contents they had when their realm was created: the value
 * of a data property, the getter of an accessor. While they keep them, a built-in may skip the
 * lookups of an algorithm that only reads them, since none of them can be observed.
 *
 * <p>The slots are cached: a slot stays valid while it is not stale and holds the same contents. A
 * stale slot (deleted, replaced by another kind of property, or moved to a rebuilt map) is looked
 * up again, so that adding properties to a prototype keeps it intact.
 */
public final class OriginalProperties {
    private final ScriptableObject[] owners;
    private final Object[] keys;
    private final Object[] contents;
    private final Cached[] slots;

    private static final int DATA = 0;
    private static final int ACCESSOR = 1;
    private static final int LAMBDA_ACCESSOR = 2;
    private static final int OTHER = 3;

    /** A slot with its kind, replaced together. */
    private static final class Cached {
        final Slot<?> slot;
        final int kind;

        Cached(Slot<?> slot) {
            this.slot = slot;
            if (slot instanceof LambdaAccessorSlot) {
                kind = LAMBDA_ACCESSOR;
            } else if (slot instanceof AccessorSlot) {
                kind = ACCESSOR;
            } else if (slot.getClass() == StandardSlot.class) {
                kind = DATA;
            } else {
                // a property computed by its slot: never recognized
                kind = OTHER;
            }
        }

        Object contents() {
            switch (kind) {
                case DATA:
                    return slot.value;
                case ACCESSOR:
                    return ((AccessorSlot) slot).getter;
                case LAMBDA_ACCESSOR:
                    return ((LambdaAccessorSlot) slot).getterObject();
                default:
                    return null;
            }
        }
    }

    private OriginalProperties(ScriptableObject[] owners, Object[] keys) {
        this.owners = owners;
        this.keys = keys;
        this.contents = new Object[keys.length];
        this.slots = new Cached[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Slot<?> slot = owners[i].getMap().query(keys[i], 0);
            if (slot != null) {
                slots[i] = new Cached(slot);
                contents[i] = slots[i].contents();
            }
        }
    }

    /**
     * The current contents of the properties keys[i] of owners[i]; a missing property must stay
     * missing.
     */
    public static OriginalProperties of(ScriptableObject[] owners, Object[] keys) {
        return new OriginalProperties(owners, keys);
    }

    /** Whether every property still has its original contents. */
    public boolean isIntact() {
        for (int i = 0; i < slots.length; i++) {
            Cached cached = slots[i];
            if (cached == null || cached.slot.stale) {
                Slot<?> slot = owners[i].getMap().query(keys[i], 0);
                if (slot == null) {
                    if (contents[i] == null) {
                        continue;
                    }
                    return false;
                }
                cached = new Cached(slot);
                slots[i] = cached;
            }
            if (contents[i] == null || cached.contents() != contents[i]) {
                return false;
            }
        }
        return true;
    }

    /** The object of the property keys[i]. */
    public ScriptableObject owner(int i) {
        return owners[i];
    }

    /** Whether the property keys[i] was present when the realm was created. */
    public boolean wasPresent(int i) {
        return contents[i] != null;
    }

    /** The number of own properties of an object. */
    public static int ownPropertyCount(ScriptableObject obj) {
        return obj.getMap().size();
    }
}
