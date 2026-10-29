/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.time.Duration;

/**
 * The limits of one request, to be enforced by a {@link ResourceScope}. An instance is immutable
 * and can be shared by all requests; every limit is off until set.
 *
 * <pre>{@code
 * ResourceLimits limits = ResourceLimits.builder()
 *         .timeout(Duration.ofSeconds(5))
 *         .maxMemoryBytes(64L << 20)
 *         .build();
 * try (ResourceScope scope = limits.newScope()) {
 *     scope.call(factory, cx -> cx.evaluateString(global, source, "request", 1, null));
 * } catch (ResourceLimitError e) {
 *     // timed out, over budget or cancelled
 * }
 * }</pre>
 *
 * <p>What each limit guarantees:
 *
 * <ul>
 *   <li>The <b>timeout</b> is checked every {@link #checkInterval()} counted instructions, by both
 *       backends, and inside the built-ins that can loop for long on their own. The delay between
 *       the deadline and the termination is that of the interval plus the longest built-in call
 *       that is not polled. Blocking Java code is only stopped if {@link #interruptBlockingCalls()}
 *       is set and the code reacts to {@link Thread#interrupt()}.
 *   <li>The <b>memory</b> limit counts the bytes that the instrumented built-ins reserve before
 *       they allocate large blocks (arrays, strings, buffers, BigInts) and that host code charges
 *       itself. Charges are never given back by the garbage collector, so it is a conservative
 *       budget that can reject early. It does not see the small objects and scope chains of the
 *       script.
 *   <li>The <b>allocation</b> limit bounds the bytes allocated by the thread since the scope was
 *       bound. As nothing retained can exceed what was allocated, it bounds the retained memory,
 *       loosely: garbage counts. HotSpot's counter does not work on virtual threads.
 * </ul>
 *
 * None of this is a hard heap quota: the JVM has none.
 */
public final class ResourceLimits {

    static final int DEFAULT_CHECK_INTERVAL = 10_000;

    private final long timeoutNanos;
    private final long deadlineNanoTime;
    private final boolean hasDeadline;
    private final long maxMemoryBytes;
    private final long maxAllocatedBytes;
    private final int checkInterval;
    private final int maxBigIntBits;
    private final int maxInterpreterStackDepth;
    private final boolean interruptBlockingCalls;

    private ResourceLimits(Builder b) {
        timeoutNanos = b.timeoutNanos;
        deadlineNanoTime = b.deadlineNanoTime;
        hasDeadline = b.hasDeadline;
        maxMemoryBytes = b.maxMemoryBytes;
        maxAllocatedBytes = b.maxAllocatedBytes;
        checkInterval = b.checkInterval;
        maxBigIntBits = b.maxBigIntBits;
        maxInterpreterStackDepth = b.maxInterpreterStackDepth;
        interruptBlockingCalls = b.interruptBlockingCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Opens a scope for one request. The timeout starts now. */
    public ResourceScope newScope() {
        return new ResourceScope(this);
    }

    /** The time allowed from the creation of the scope, in nanoseconds; 0 if unlimited. */
    public long timeoutNanos() {
        return timeoutNanos;
    }

    /** Whether an absolute deadline ({@link System#nanoTime()} clock) was set. */
    public boolean hasDeadline() {
        return hasDeadline;
    }

    public long deadlineNanoTime() {
        return deadlineNanoTime;
    }

    /** The memory budget in bytes; 0 if unlimited. */
    public long maxMemoryBytes() {
        return maxMemoryBytes;
    }

    /** The bytes that the thread may allocate while bound; 0 if unlimited. */
    public long maxAllocatedBytes() {
        return maxAllocatedBytes;
    }

    /** The number of counted instructions between two checks. */
    public int checkInterval() {
        return checkInterval;
    }

    /** The largest BigInt result in bits; 0 if unlimited. */
    public int maxBigIntBits() {
        return maxBigIntBits;
    }

    /** The interpreter call depth, see {@link Context#setMaximumInterpreterStackDepth(int)}. */
    public int maxInterpreterStackDepth() {
        return maxInterpreterStackDepth;
    }

    /** Whether the thread is interrupted when the scope is terminated. */
    public boolean interruptBlockingCalls() {
        return interruptBlockingCalls;
    }

    public static final class Builder {
        private long timeoutNanos;
        private long deadlineNanoTime;
        private boolean hasDeadline;
        private long maxMemoryBytes;
        private long maxAllocatedBytes;
        private int checkInterval = DEFAULT_CHECK_INTERVAL;
        private int maxBigIntBits;
        private int maxInterpreterStackDepth;
        private boolean interruptBlockingCalls;

        private Builder() {}

        /** The time allowed, counted from the creation of the scope. */
        public Builder timeout(Duration timeout) {
            if (timeout.isNegative()) throw new IllegalArgumentException("negative timeout");
            timeoutNanos = timeout.toNanos();
            return this;
        }

        /**
         * An absolute deadline on the {@link System#nanoTime()} clock, for a request whose clock
         * started before the script does. The earlier of this and the timeout applies.
         */
        public Builder deadlineNanoTime(long deadline) {
            deadlineNanoTime = deadline;
            hasDeadline = true;
            return this;
        }

        public Builder maxMemoryBytes(long bytes) {
            if (bytes < 0) throw new IllegalArgumentException();
            maxMemoryBytes = bytes;
            return this;
        }

        /** Not supported on virtual threads: binding such a scope on one throws. */
        public Builder maxAllocatedBytes(long bytes) {
            if (bytes < 0) throw new IllegalArgumentException();
            maxAllocatedBytes = bytes;
            return this;
        }

        /**
         * The number of counted instructions between two checks (default {@value
         * #DEFAULT_CHECK_INTERVAL}). A lower value ends the script sooner after the deadline and
         * costs more.
         */
        public Builder checkInterval(int instructions) {
            if (instructions < 1) throw new IllegalArgumentException();
            checkInterval = instructions;
            return this;
        }

        public Builder maxBigIntBits(int bits) {
            if (bits < 0) throw new IllegalArgumentException();
            maxBigIntBits = bits;
            return this;
        }

        public Builder maxInterpreterStackDepth(int depth) {
            if (depth < 0) throw new IllegalArgumentException();
            maxInterpreterStackDepth = depth;
            return this;
        }

        /**
         * Interrupts the threads bound to the scope when it is terminated (by the deadline or by
         * {@link ResourceScope#cancel(String)}), so that {@code Thread.sleep}, {@code
         * Atomics.wait}, locks and interruptible I/O return early. Costs a shared timer thread
         * while a deadline is pending.
         */
        public Builder interruptBlockingCalls(boolean interrupt) {
            interruptBlockingCalls = interrupt;
            return this;
        }

        public ResourceLimits build() {
            return new ResourceLimits(this);
        }
    }
}
