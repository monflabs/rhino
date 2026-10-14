/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.typedarrays;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.PROTO;

import java.io.Serial;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.monflabs.mozilla.javascript.AbstractEcmaObjectOperations;
import org.monflabs.mozilla.javascript.ClassDescriptor;
import org.monflabs.mozilla.javascript.Constructable;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.JSFunction;
import org.monflabs.mozilla.javascript.LambdaConstructor;
import org.monflabs.mozilla.javascript.NativeObject;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.ScriptRuntimeES6;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.SymbolKey;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;

/**
 * SharedArrayBuffer (ECMA-262 25.2). Its storage is a {@link Storage}: the bytes, allocated up to
 * the maximum length of a growable buffer since memory that other agents may see cannot be moved,
 * and the current length. The views of a SharedArrayBuffer read and write its bytes like those of
 * an ArrayBuffer; {@link NativeAtomics} serializes its atomic operations on the storage.
 */
public final class NativeSharedArrayBuffer extends NativeArrayBuffer {
    @Serial private static final long serialVersionUID = 1L;

    public static final String CLASS_NAME = "SharedArrayBuffer";

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(CLASS_NAME, 1, NativeSharedArrayBuffer::js_constructor)
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .withMethod(PROTO, "grow", 1, NativeSharedArrayBuffer::js_grow)
                        .withMethod(PROTO, "slice", 2, NativeSharedArrayBuffer::js_slice)
                        .withProp(
                                PROTO,
                                "byteLength",
                                NativeSharedArrayBuffer::js_byteLength,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                "growable",
                                NativeSharedArrayBuffer::js_growable,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                "maxByteLength",
                                NativeSharedArrayBuffer::js_maxByteLength,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value(CLASS_NAME, DONTENUM | READONLY))
                        .build();
    }

    /**
     * The shared data block: what the agents that share a buffer have in common. Its monitor is the
     * lock of the atomic operations on it, and of its waiter lists (WaiterList, 25.4.1.2).
     */
    static final class Storage {
        final ByteBuffer bytes;
        final int maxByteLength; // -1 if not growable
        volatile int byteLength;
        private final Map<Integer, Deque<Waiter>> waiters = new HashMap<>();

        /** The waiters on a byte index, oldest first; to be used holding the monitor. */
        Deque<Waiter> waiters(int byteIndex) {
            return waiters.computeIfAbsent(byteIndex, k -> new ArrayDeque<>());
        }

        void removeWaiter(int byteIndex, Waiter waiter) {
            Deque<Waiter> list = waiters.get(byteIndex);
            if (list != null) {
                list.remove(waiter);
                if (list.isEmpty()) {
                    waiters.remove(byteIndex);
                }
            }
        }

        /**
         * Wakes up to count waiters on a byte index, adding the callbacks of the async ones to
         * wakeUps; to be used holding the monitor.
         */
        int notifyWaiters(int byteIndex, double count, List<Runnable> wakeUps) {
            Deque<Waiter> list = waiters.get(byteIndex);
            int woken = 0;
            while (list != null && woken < count && !list.isEmpty()) {
                Waiter waiter = list.removeFirst();
                waiter.notified = true;
                if (waiter.onNotify != null) {
                    wakeUps.add(waiter.onNotify);
                }
                woken++;
            }
            if (list != null && list.isEmpty()) {
                waiters.remove(byteIndex);
            }
            if (woken > 0) {
                notifyAll();
            }
            return woken;
        }

        Storage(ByteBuffer bytes, int byteLength, int maxByteLength) {
            this.bytes = bytes;
            this.byteLength = byteLength;
            this.maxByteLength = maxByteLength;
        }
    }

    /** An agent suspended in Atomics.wait, or a promise of Atomics.waitAsync. */
    static final class Waiter {
        boolean notified;
        // for Atomics.waitAsync, run outside the monitor once notified
        Runnable onNotify;
    }

    private final Storage storage;

    private NativeSharedArrayBuffer(Storage storage) {
        super(storage.bytes);
        this.storage = storage;
    }

    /**
     * A SharedArrayBuffer of the realm of scope over the same memory as buffer: how memory is
     * shared between agents (threads with contexts and realms of their own).
     */
    public static NativeSharedArrayBuffer share(VarScope scope, NativeSharedArrayBuffer buffer) {
        NativeSharedArrayBuffer shared = new NativeSharedArrayBuffer(buffer.storage);
        ScriptRuntime.setBuiltinProtoAndParent(shared, scope, TopLevel.Builtins.SharedArrayBuffer);
        return shared;
    }

    public static Object init(Context cx, VarScope scope, boolean sealed) {
        return DESCRIPTOR.buildConstructor(cx, scope, new NativeObject(), sealed);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    Storage getStorage() {
        return storage;
    }

    @Override
    public int getLength() {
        return storage.byteLength;
    }

    @Override
    public boolean isResizable() {
        return storage.maxByteLength >= 0;
    }

    @Override
    public void detach() {
        // a SharedArrayBuffer cannot be detached
    }

    private static NativeSharedArrayBuffer getSelf(Object thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, NativeSharedArrayBuffer.class);
    }

    /** SharedArrayBuffer ( length [ , options ] ) (25.2.3.1). */
    private static NativeSharedArrayBuffer js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        int byteLength = ScriptRuntime.toIndex(args.length > 0 ? args[0] : Undefined.instance);
        int maxByteLength = maxByteLengthOption(args.length > 1 ? args[1] : Undefined.instance);
        // AllocateSharedArrayBuffer: the lengths are checked before the prototype is read
        if (maxByteLength >= 0 && byteLength > maxByteLength) {
            throw ScriptRuntime.rangeErrorById("msg.arraybuf.range.mismatch");
        }
        Scriptable proto = ScriptRuntime.findPrototype(f, nt, TopLevel.Builtins.SharedArrayBuffer);
        int capacity = maxByteLength >= 0 ? maxByteLength : byteLength;
        ByteBuffer bytes;
        try {
            bytes = ByteBuffer.allocate(capacity);
        } catch (OutOfMemoryError e) {
            throw ScriptRuntime.rangeErrorById("msg.arraybuf.oom");
        }
        bytes.order(defaultByteOrder(cx));
        NativeSharedArrayBuffer buffer =
                new NativeSharedArrayBuffer(new Storage(bytes, byteLength, maxByteLength));
        buffer.setPrototype(proto);
        buffer.setParentScope(s);
        return buffer;
    }

    /** GetArrayBufferMaxByteLengthOption (25.1.3.7): -1 for none. */
    private static int maxByteLengthOption(Object options) {
        if (!ScriptRuntime.isObject(options)) {
            return -1;
        }
        Object max = ScriptableObject.getProperty((Scriptable) options, "maxByteLength");
        if (max == Scriptable.NOT_FOUND || Undefined.isUndefined(max)) {
            return -1;
        }
        int maxByteLength = ScriptRuntime.toIndex(max);
        if (maxByteLength > Runtime.getRuntime().maxMemory()) {
            throw ScriptRuntime.rangeErrorById("msg.arraybuf.range.toobig");
        }
        return maxByteLength;
    }

    private static Object js_byteLength(Scriptable thisObj) {
        return getSelf(thisObj).getLength();
    }

    private static Object js_growable(Scriptable thisObj) {
        return getSelf(thisObj).isResizable();
    }

    private static Object js_maxByteLength(Scriptable thisObj) {
        NativeSharedArrayBuffer self = getSelf(thisObj);
        return self.isResizable() ? self.storage.maxByteLength : self.getLength();
    }

    /** SharedArrayBuffer.prototype.grow ( newLength ) (25.2.5.3). */
    private static Object js_grow(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSharedArrayBuffer self = getSelf(thisObj);
        if (!self.isResizable()) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notresizeable");
        }
        int newByteLength = ScriptRuntime.toIndex(args.length > 0 ? args[0] : Undefined.instance);
        Storage storage = self.storage;
        synchronized (storage) {
            if (newByteLength > storage.maxByteLength) {
                throw ScriptRuntime.rangeErrorById(
                        "msg.arraybuf.range.exceedsmax", storage.maxByteLength);
            }
            if (newByteLength < storage.byteLength) {
                throw ScriptRuntime.rangeErrorById("msg.sharedarraybuf.shrink");
            }
            // the bytes beyond the length are zero: they were never in reach
            storage.byteLength = newByteLength;
        }
        return Undefined.instance;
    }

    /** SharedArrayBuffer.prototype.slice ( start, end ) (25.2.5.7). */
    private static Object js_slice(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeSharedArrayBuffer self = getSelf(thisObj);
        int len = self.getLength();
        double relativeStart =
                ScriptRuntime.toIntegerOrInfinity(args.length > 0 ? args[0] : Undefined.instance);
        int first =
                (int)
                        (relativeStart < 0
                                ? Math.max(len + relativeStart, 0)
                                : Math.min(relativeStart, len));
        Object endArg = args.length > 1 ? args[1] : Undefined.instance;
        double relativeEnd =
                Undefined.isUndefined(endArg) ? len : ScriptRuntime.toIntegerOrInfinity(endArg);
        int fin =
                (int)
                        (relativeEnd < 0
                                ? Math.max(len + relativeEnd, 0)
                                : Math.min(relativeEnd, len));
        int newLen = Math.max(fin - first, 0);

        Constructable ctor =
                AbstractEcmaObjectOperations.speciesConstructor(
                        cx,
                        self,
                        TopLevel.getBuiltinCtor(
                                cx,
                                ScriptableObject.getTopLevelScope(s),
                                TopLevel.Builtins.SharedArrayBuffer));
        Scriptable created = ctor.construct(cx, s, new Object[] {newLen});
        if (!(created instanceof NativeSharedArrayBuffer)) {
            throw ScriptRuntime.typeErrorById("msg.species.invalid.ctor");
        }
        NativeSharedArrayBuffer target = (NativeSharedArrayBuffer) created;
        if (target.storage == self.storage) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.same");
        }
        if (target.getLength() < newLen) {
            throw ScriptRuntime.typeErrorById(
                    "msg.arraybuf.smaller.len", newLen, target.getLength());
        }
        ByteBuffer from = self.storage.bytes.duplicate();
        from.limit(first + newLen);
        from.position(first);
        ByteBuffer to = target.storage.bytes.duplicate();
        to.put(from);
        return target;
    }
}
