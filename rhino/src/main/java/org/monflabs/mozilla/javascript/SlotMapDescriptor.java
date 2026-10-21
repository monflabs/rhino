package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.List;

public class SlotMapDescriptor<O extends ScriptableObject> {

    private final List<CompactSlot.Descriptor<?, Scriptable, O>> slots;
    private final int[] attributes;
    private final boolean hashed;

    private SlotMapDescriptor(
            List<CompactSlot.Descriptor<?, Scriptable, O>> slots,
            int[] attributes,
            boolean hashed) {
        this.slots = slots;
        this.attributes = attributes;
        this.hashed = hashed;
    }

    SlotMap<Scriptable> buildMap(O owner) {
        return buildMap(owner, Context.getCurrentContext());
    }

    /** The map of the slots, for objects created in this context. */
    SlotMap<Scriptable> buildMap(O owner, Context cx) {
        boolean threadSafe = cx != null && cx.hasFeature(Context.FEATURE_THREAD_SAFE_OBJECTS);
        int size = slots.size();
        if (hashed || size > 4) {
            // a table that holds the slots without growing (at most 3/4 full)
            int capacity = (4 * size + 2) / 3;
            EmbeddedSlotMap<Scriptable> map =
                    threadSafe
                            ? new ThreadSafeEmbeddedSlotMap<>(capacity)
                            : new EmbeddedSlotMap<>(capacity);
            for (int i = 0; i < size; i++) {
                map.add(null, slots.get(i).createSlot(owner, attributes[i]));
            }
            return map;
        }
        return SlotMapOwner.createSlotMap(
                threadSafe,
                size > 0 ? slots.get(0).createSlot(owner, attributes[0]) : null,
                size > 1 ? slots.get(1).createSlot(owner, attributes[1]) : null,
                size > 2 ? slots.get(2).createSlot(owner, attributes[2]) : null,
                size > 3 ? slots.get(3).createSlot(owner, attributes[3]) : null);
    }

    public void installMap(O owner) {
        installMap(owner, Context.getCurrentContext());
    }

    void installMap(O owner, Context cx) {
        for (var slot : owner.getMap()) {
            slot.stale = true;
        }
        owner.setMap(buildMap(owner, cx));
    }

    public static class Builder<O extends ScriptableObject> {
        List<CompactSlot.Descriptor<?, Scriptable, O>> slots = new ArrayList<>();
        List<Integer> attributes = new ArrayList<>();
        boolean hashed;

        public Builder() {}

        private Builder(SlotMapDescriptor<O> old) {
            slots = new ArrayList<>(old.slots);
            attributes = new ArrayList<>(old.attributes.length * 2);
            for (int i = 0; i < old.attributes.length; i++) {
                attributes.add(old.attributes[i]);
            }
        }

        public static <O extends ScriptableObject> Builder<O> extending(
                SlotMapDescriptor<O> start) {
            return new Builder<>(start);
        }

        public Builder<O> withSlot(
                CompactSlot.Descriptor<?, Scriptable, O> descriptor, int attributes) {
            slots.add(descriptor);
            this.attributes.add(attributes);
            return this;
        }

        /**
         * Builds the map of the slots as a hash table, as the maps of objects that gain properties
         * become: lookups of keys it does not have are cheaper.
         */
        public Builder<O> hashed() {
            hashed = true;
            return this;
        }

        public SlotMapDescriptor<O> build() {
            return new SlotMapDescriptor<>(
                    List.copyOf(slots), attributes.stream().mapToInt(v -> v).toArray(), hashed);
        }
    }
}
