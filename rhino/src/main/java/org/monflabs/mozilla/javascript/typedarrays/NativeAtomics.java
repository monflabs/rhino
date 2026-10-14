/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.typedarrays;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;

import java.io.Serial;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.monflabs.mozilla.javascript.ClassDescriptor;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.HostJobScheduler;
import org.monflabs.mozilla.javascript.JSFunction;
import org.monflabs.mozilla.javascript.NativeObject;
import org.monflabs.mozilla.javascript.NativePromise;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.SymbolKey;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;

/**
 * The Atomics object (ECMA-262 25.4). An atomic operation holds the lock of the storage of its
 * buffer: the operations on a location are totally ordered, and plain accesses of the same bytes
 * race with them, as the memory model allows.
 */
public final class NativeAtomics extends ScriptableObject {
    @Serial private static final long serialVersionUID = 1L;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder("Atomics")
                        .withMethod(CTOR, "add", 3, (cx, f, nt, s, t, a) -> rmw(cx, a, Op.ADD))
                        .withMethod(CTOR, "and", 3, (cx, f, nt, s, t, a) -> rmw(cx, a, Op.AND))
                        .withMethod(CTOR, "compareExchange", 4, NativeAtomics::compareExchange)
                        .withMethod(
                                CTOR,
                                "exchange",
                                3,
                                (cx, f, nt, s, t, a) -> rmw(cx, a, Op.EXCHANGE))
                        .withMethod(CTOR, "isLockFree", 1, NativeAtomics::isLockFree)
                        .withMethod(CTOR, "load", 2, NativeAtomics::load)
                        .withMethod(CTOR, "notify", 3, NativeAtomics::notify)
                        .withMethod(CTOR, "or", 3, (cx, f, nt, s, t, a) -> rmw(cx, a, Op.OR))
                        .withMethod(CTOR, "pause", 0, NativeAtomics::pause)
                        .withMethod(CTOR, "store", 3, NativeAtomics::store)
                        .withMethod(CTOR, "wait", 4, NativeAtomics::doWait)
                        .withMethod(CTOR, "waitAsync", 4, NativeAtomics::waitAsync)
                        .withMethod(CTOR, "sub", 3, (cx, f, nt, s, t, a) -> rmw(cx, a, Op.SUB))
                        .withMethod(CTOR, "xor", 3, (cx, f, nt, s, t, a) -> rmw(cx, a, Op.XOR))
                        .withProp(
                                CTOR,
                                SymbolKey.TO_STRING_TAG,
                                value("Atomics", DONTENUM | READONLY))
                        .build();
    }

    public static Object init(Context cx, VarScope scope, boolean sealed) {
        return DESCRIPTOR.populateGlobal(cx, scope, new NativeObject(), sealed);
    }

    private NativeAtomics() {}

    @Override
    public String getClassName() {
        return "Atomics";
    }

    private enum Op {
        ADD,
        AND,
        EXCHANGE,
        OR,
        SUB,
        XOR;

        long apply(long was, long operand) {
            switch (this) {
                case ADD:
                    return was + operand;
                case AND:
                    return was & operand;
                case EXCHANGE:
                    return operand;
                case OR:
                    return was | operand;
                case SUB:
                    return was - operand;
                default:
                    return was ^ operand;
            }
        }
    }

    /** An element of an integer typed array, validated for an atomic access. */
    private static final class Access {
        final NativeTypedArrayView<?> view;
        final int width;
        final boolean signed;
        final boolean bigint;
        final int byteIndex;

        Access(NativeTypedArrayView<?> view, int width, boolean signed, boolean bigint, int index) {
            this.view = view;
            this.width = width;
            this.signed = signed;
            this.bigint = bigint;
            this.byteIndex = view.offset + index * width;
        }

        /** The lock of the storage: shared by every buffer over the same bytes. */
        Object lock() {
            NativeArrayBuffer buffer = view.arrayBuffer;
            return buffer instanceof NativeSharedArrayBuffer
                    ? ((NativeSharedArrayBuffer) buffer).getStorage()
                    : buffer;
        }

        /**
         * RevalidateAtomicAccess (25.4.3.4): converting the arguments may have detached or shrunk
         * the buffer.
         */
        ByteBuffer revalidate() {
            if (view.isTypedArrayOutOfBounds()) {
                throw ScriptRuntime.typeErrorById("msg.typed.array.out.of.bounds");
            }
            if (byteIndex >= view.offset + (long) view.getLength() * width) {
                throw ScriptRuntime.rangeErrorById("msg.atomics.index", byteIndex);
            }
            return view.arrayBuffer.buffer;
        }

        /** The raw bits of the element, sign- or zero-extended. */
        long read(ByteBuffer bytes) {
            switch (width) {
                case 1:
                    return signed ? bytes.get(byteIndex) : bytes.get(byteIndex) & 0xFFL;
                case 2:
                    return signed ? bytes.getShort(byteIndex) : bytes.getShort(byteIndex) & 0xFFFFL;
                case 4:
                    return signed ? bytes.getInt(byteIndex) : bytes.getInt(byteIndex) & 0xFFFFFFFFL;
                default:
                    return bytes.getLong(byteIndex);
            }
        }

        void write(ByteBuffer bytes, long bits) {
            switch (width) {
                case 1:
                    bytes.put(byteIndex, (byte) bits);
                    break;
                case 2:
                    bytes.putShort(byteIndex, (short) bits);
                    break;
                case 4:
                    bytes.putInt(byteIndex, (int) bits);
                    break;
                default:
                    bytes.putLong(byteIndex, bits);
            }
        }

        /** The value of the element type that the bits stand for. */
        long narrow(long bits) {
            switch (width) {
                case 1:
                    return signed ? (byte) bits : bits & 0xFFL;
                case 2:
                    return signed ? (short) bits : bits & 0xFFFFL;
                case 4:
                    return signed ? (int) bits : bits & 0xFFFFFFFFL;
                default:
                    return bits;
            }
        }

        /** The operand: ToBigInt, or ToIntegerOrInfinity then the bits of the element type. */
        long operand(Object value) {
            if (bigint) {
                return ScriptRuntime.toBigIntStrict(value).longValue();
            }
            return ScriptRuntime.toInt32(ScriptRuntime.toIntegerOrInfinity(value));
        }

        Object toValue(long bits) {
            if (!bigint) {
                return width == 4 && !signed ? (Object) (double) bits : (Object) (int) bits;
            }
            return signed ? BigInteger.valueOf(bits) : Conversions.longBitsToBigUint(bits);
        }
    }

    private static Object arg(Object[] args, int i) {
        return args.length > i ? args[i] : Undefined.instance;
    }

    /**
     * ValidateIntegerTypedArray (25.4.3.1) and ValidateAtomicAccess (25.4.3.2): the typed array is
     * validated before the index is converted.
     */
    private static Access validate(Object array, Object index, boolean waitable) {
        return validate(array, index, waitable, false);
    }

    private static Access validate(
            Object array, Object index, boolean waitable, boolean mustBeShared) {
        int width;
        boolean signed = true;
        boolean bigint = false;
        if (array instanceof NativeInt32Array) {
            width = 4;
        } else if (array instanceof NativeBigInt64Array) {
            width = 8;
            bigint = true;
        } else if (waitable) {
            throw ScriptRuntime.typeErrorById("msg.atomics.not.waitable");
        } else if (array instanceof NativeInt8Array) {
            width = 1;
        } else if (array instanceof NativeUint8Array) {
            width = 1;
            signed = false;
        } else if (array instanceof NativeInt16Array) {
            width = 2;
        } else if (array instanceof NativeUint16Array) {
            width = 2;
            signed = false;
        } else if (array instanceof NativeUint32Array) {
            width = 4;
            signed = false;
        } else if (array instanceof NativeBigUint64Array) {
            width = 8;
            signed = false;
            bigint = true;
        } else {
            throw ScriptRuntime.typeErrorById("msg.atomics.not.integer");
        }
        NativeTypedArrayView<?> view = (NativeTypedArrayView<?>) array;
        if (view.isTypedArrayOutOfBounds()) {
            throw ScriptRuntime.typeErrorById("msg.typed.array.out.of.bounds");
        }
        if (mustBeShared && !(view.arrayBuffer instanceof NativeSharedArrayBuffer)) {
            throw ScriptRuntime.typeErrorById("msg.atomics.not.shared");
        }
        int length = view.getLength();
        int accessIndex = ScriptRuntime.toIndex(index);
        if (accessIndex >= length) {
            throw ScriptRuntime.rangeErrorById("msg.atomics.index", accessIndex);
        }
        return new Access(view, width, signed, bigint, accessIndex);
    }

    /** AtomicReadModifyWrite (25.4.3.17). */
    private static Object rmw(Context cx, Object[] args, Op op) {
        Access at = validate(arg(args, 0), arg(args, 1), false);
        long operand = at.operand(arg(args, 2));
        synchronized (at.lock()) {
            ByteBuffer bytes = at.revalidate();
            long was = at.read(bytes);
            at.write(bytes, op.apply(was, operand));
            return at.toValue(was);
        }
    }

    /** Atomics.compareExchange ( typedArray, index, expectedValue, replacementValue ) (25.4.5). */
    private static Object compareExchange(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Access at = validate(arg(args, 0), arg(args, 1), false);
        long expected = at.narrow(at.operand(arg(args, 2)));
        long replacement = at.operand(arg(args, 3));
        synchronized (at.lock()) {
            ByteBuffer bytes = at.revalidate();
            long was = at.read(bytes);
            if (was == expected) {
                at.write(bytes, replacement);
            }
            return at.toValue(was);
        }
    }

    /** Atomics.isLockFree ( size ) (25.4.7). */
    private static Object isLockFree(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double size = ScriptRuntime.toIntegerOrInfinity(arg(args, 0));
        return size == 1 || size == 2 || size == 4;
    }

    /** Atomics.load ( typedArray, index ) (25.4.8). */
    private static Object load(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Access at = validate(arg(args, 0), arg(args, 1), false);
        synchronized (at.lock()) {
            return at.toValue(at.read(at.revalidate()));
        }
    }

    /** Atomics.store ( typedArray, index, value ) (25.4.11): returns the converted value. */
    private static Object store(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Access at = validate(arg(args, 0), arg(args, 1), false);
        Object value = arg(args, 2);
        Object result;
        long bits;
        if (at.bigint) {
            BigInteger v = ScriptRuntime.toBigIntStrict(value);
            result = v;
            bits = v.longValue();
        } else {
            double v = ScriptRuntime.toIntegerOrInfinity(value) + 0.0;
            result = v;
            bits = ScriptRuntime.toInt32(v);
        }
        synchronized (at.lock()) {
            at.write(at.revalidate(), bits);
        }
        return result;
    }

    /**
     * Atomics.wait ( typedArray, index, value, timeout ) (25.4.13, DoWait): every agent can
     * suspend, the main thread included, as on a server.
     */
    private static Object doWait(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Access at = validate(arg(args, 0), arg(args, 1), true, true);
        long expected =
                at.bigint
                        ? ScriptRuntime.toBigIntStrict(arg(args, 2)).longValue()
                        : ScriptRuntime.toInt32(arg(args, 2));
        double q = ScriptRuntime.toNumber(arg(args, 3));
        double millis = Double.isNaN(q) ? Double.POSITIVE_INFINITY : Math.max(q, 0);
        NativeSharedArrayBuffer.Storage storage =
                ((NativeSharedArrayBuffer) at.view.arrayBuffer).getStorage();
        synchronized (storage) {
            if (at.read(storage.bytes) != expected) {
                return "not-equal";
            }
            NativeSharedArrayBuffer.Waiter waiter = new NativeSharedArrayBuffer.Waiter();
            storage.waiters(at.byteIndex).addLast(waiter);
            long deadline =
                    millis == Double.POSITIVE_INFINITY
                            ? Long.MAX_VALUE
                            : System.nanoTime() + (long) Math.min(millis * 1e6, Long.MAX_VALUE / 2);
            try {
                while (!waiter.notified) {
                    if (deadline == Long.MAX_VALUE) {
                        storage.wait();
                    } else {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            break;
                        }
                        storage.wait(remaining / 1_000_000, (int) (remaining % 1_000_000));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (!waiter.notified) {
                    storage.removeWaiter(at.byteIndex, waiter);
                }
            }
            return waiter.notified ? "ok" : "timed-out";
        }
    }

    /**
     * Atomics.waitAsync ( typedArray, index, value, timeout ) (25.4.14, DoWait in async mode): the
     * promise settles in a job of the host job scheduler of the Context, posted by the notify (from
     * any agent) or scheduled for the timeout.
     */
    private static Object waitAsync(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Access at = validate(arg(args, 0), arg(args, 1), true, true);
        long expected =
                at.bigint
                        ? ScriptRuntime.toBigIntStrict(arg(args, 2)).longValue()
                        : ScriptRuntime.toInt32(arg(args, 2));
        double q = ScriptRuntime.toNumber(arg(args, 3));
        double millis = Double.isNaN(q) ? Double.POSITIVE_INFINITY : Math.max(q, 0);
        NativeSharedArrayBuffer.Storage storage =
                ((NativeSharedArrayBuffer) at.view.arrayBuffer).getStorage();
        NativeSharedArrayBuffer.Waiter waiter = new NativeSharedArrayBuffer.Waiter();
        HostJobScheduler.Cancelable[] timer = new HostJobScheduler.Cancelable[1];
        NativePromise.HostPromise promise;
        HostJobScheduler.Pending pending;
        synchronized (storage) {
            if (at.read(storage.bytes) != expected) {
                return waitResult(cx, s, false, "not-equal");
            }
            if (millis == 0) {
                return waitResult(cx, s, false, "timed-out");
            }
            HostJobScheduler scheduler = cx.getHostJobScheduler();
            if (scheduler == null) {
                throw ScriptRuntime.typeErrorById("msg.atomics.no.scheduler");
            }
            promise = NativePromise.newHostPromise(cx, s);
            pending = scheduler.expect();
            HostJobScheduler.Job ok =
                    jcx -> {
                        if (timer[0] != null) {
                            timer[0].cancel();
                        }
                        promise.resolve(jcx, "ok");
                        jcx.processMicrotasks();
                    };
            waiter.onNotify = () -> pending.post(ok);
            storage.waiters(at.byteIndex).addLast(waiter);
        }
        if (millis != Double.POSITIVE_INFINITY) {
            timer[0] =
                    cx.getHostJobScheduler()
                            .schedule(
                                    jcx -> {
                                        synchronized (storage) {
                                            if (waiter.notified) {
                                                // the notify job settles it
                                                return;
                                            }
                                            storage.removeWaiter(at.byteIndex, waiter);
                                        }
                                        pending.cancel();
                                        promise.resolve(jcx, "timed-out");
                                        jcx.processMicrotasks();
                                    },
                                    (long) Math.ceil(Math.min(millis, Long.MAX_VALUE)));
        }
        return waitResult(cx, s, true, promise.getPromise());
    }

    private static Scriptable waitResult(Context cx, VarScope s, boolean async, Object value) {
        Scriptable result = cx.newObject(s);
        ScriptableObject.putProperty(result, "async", async);
        ScriptableObject.putProperty(result, "value", value);
        return result;
    }

    /** Atomics.notify ( typedArray, index, count ) (25.4.15). */
    private static Object notify(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Access at = validate(arg(args, 0), arg(args, 1), true);
        Object count = arg(args, 2);
        double c =
                Undefined.isUndefined(count)
                        ? Double.POSITIVE_INFINITY
                        : Math.max(ScriptRuntime.toIntegerOrInfinity(count), 0);
        if (!(at.view.arrayBuffer instanceof NativeSharedArrayBuffer)) {
            return 0;
        }
        NativeSharedArrayBuffer.Storage storage =
                ((NativeSharedArrayBuffer) at.view.arrayBuffer).getStorage();
        List<Runnable> wakeUps = new ArrayList<>();
        int woken;
        synchronized (storage) {
            woken = storage.notifyWaiters(at.byteIndex, c, wakeUps);
        }
        for (Runnable wakeUp : wakeUps) {
            wakeUp.run();
        }
        return woken;
    }

    /** Atomics.pause ( [ N ] ) (25.4.10): a hint for a spin-wait loop. */
    private static Object pause(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Object n = arg(args, 0);
        if (!Undefined.isUndefined(n)) {
            if (!(n instanceof Number) || n instanceof BigInteger) {
                throw ScriptRuntime.typeErrorById("msg.atomics.pause");
            }
            double d = ((Number) n).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.floor(d)) {
                throw ScriptRuntime.typeErrorById("msg.atomics.pause");
            }
        }
        Thread.onSpinWait();
        return Undefined.instance;
    }
}
