/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The resource controller of one request: a deadline, a cancellation state and a memory budget that
 * are shared by every thread and {@link Context} that runs the request's scripts. It is created
 * from {@link ResourceLimits#newScope()}, and closed when the request ends.
 *
 * <p>A script runs under the scope once a Context is {@linkplain #bind(Context) bound} to it. The
 * binding turns on the instruction observer of that Context (and the generation of the counting
 * code, so scripts must be compiled while it is open), restores everything when it is closed, and
 * is the only state that the Context keeps. {@link #call(ContextFactory, ContextAction)} does both
 * in one step.
 *
 * <p>Once a limit is reached, or {@link #cancel(String)} is called, the scope is terminated for
 * good: the running script gets a {@link ResourceLimitError} at its next safepoint, again at every
 * following one if something swallows it, and the scope cannot be bound again. Nested bindings
 * (another scope bound on a Context that already has one) are all checked and all charged, so the
 * tightest limit wins.
 */
public final class ResourceScope implements AutoCloseable {

    private final ResourceLimits limits;
    private final boolean timed;
    private final long deadline;
    private final AtomicReference<ResourceLimitError> terminated = new AtomicReference<>();
    private final AtomicLong charged = new AtomicLong();
    private final Object lock = new Object();
    // guarded by lock
    private final List<Binding> bindings = new ArrayList<>(1);
    private boolean closed;
    private ScheduledFuture<?> timer;

    ResourceScope(ResourceLimits limits) {
        this.limits = limits;
        long now = System.nanoTime();
        boolean hasDeadline = false;
        long d = 0;
        if (limits.timeoutNanos() > 0) {
            d = now + limits.timeoutNanos();
            hasDeadline = true;
        }
        if (limits.hasDeadline() && (!hasDeadline || limits.deadlineNanoTime() - d < 0)) {
            d = limits.deadlineNanoTime();
            hasDeadline = true;
        }
        timed = hasDeadline;
        deadline = d;
        if (timed && limits.interruptBlockingCalls()) {
            timer = Watchdog.schedule(this::expire, d - now);
        }
    }

    public ResourceLimits getLimits() {
        return limits;
    }

    /**
     * Runs the action on a Context of the factory (the current one if the thread has one), bound to
     * this scope. Throws {@link ResourceLimitError} if the scope is terminated. The scope itself
     * stays open.
     */
    public <T> T call(ContextFactory factory, ContextAction<T> action) {
        try (Context cx = factory.enterContext();
                Binding binding = bind(cx)) {
            return action.run(cx);
        }
    }

    /**
     * Binds the Context, which must be the one of the current thread, to this scope until the
     * result is closed, which has to happen on the same thread and in reverse order of the
     * bindings.
     *
     * @throws ResourceLimitError if the scope is already terminated
     * @throws IllegalStateException if the scope is closed or the Context is not the current one
     * @throws UnsupportedOperationException if an allocation limit is set and the thread cannot be
     *     measured
     */
    public Binding bind(Context cx) {
        if (Context.getCurrentContext() != cx) {
            throw new IllegalStateException("The Context is not the one of the current thread");
        }
        Thread thread = Thread.currentThread();
        long allocBaseline = 0;
        if (limits.maxAllocatedBytes() > 0) {
            if (thread.isVirtual() || !AllocationCounter.isSupported()) {
                throw new UnsupportedOperationException(
                        "Allocation limits need a platform thread on a HotSpot-like JVM");
            }
            allocBaseline = AllocationCounter.current();
        }
        check();
        Binding binding = new Binding(this, cx, thread, allocBaseline);
        synchronized (lock) {
            if (closed) {
                binding.close();
                throw new IllegalStateException("The scope is closed");
            }
            bindings.add(binding);
        }
        return binding;
    }

    /**
     * Terminates the scope from any thread: the scripts bound to it end at their next safepoint
     * with a {@link ResourceLimitError} of kind {@link ResourceLimitError.Kind#CANCELLED}. Threads
     * blocked in Java code are interrupted if {@link ResourceLimits#interruptBlockingCalls()} is
     * set. Does nothing if the scope is already terminated.
     */
    public void cancel(String reason) {
        terminate(ResourceLimitError.Kind.CANCELLED, reason == null ? "cancelled" : reason);
        interruptBindings();
    }

    public boolean isTerminated() {
        return terminated.get() != null;
    }

    /** The error that terminated the scope, or null. */
    public ResourceLimitError getTermination() {
        return terminated.get();
    }

    /** The bytes charged so far, see {@link #charge(long)}. */
    public long chargedBytes() {
        return charged.get();
    }

    /**
     * Reserves memory against the budget, before the allocation it stands for. Thread-safe; host
     * objects that hold large data for the script can call it too.
     *
     * @throws ResourceLimitError if the budget is exceeded, or the scope is terminated
     */
    public void charge(long bytes) {
        ResourceLimitError t = terminated.get();
        if (t != null) throw t;
        if (bytes <= 0) return;
        long total = charged.addAndGet(bytes);
        long max = limits.maxMemoryBytes();
        if (max > 0 && total > max) {
            throw terminate(
                    ResourceLimitError.Kind.MEMORY,
                    "memory limit exceeded (" + total + " > " + max + " bytes)");
        }
    }

    /** Gives back bytes that were charged, for data the host knows to be gone. */
    public void release(long bytes) {
        if (bytes > 0) charged.addAndGet(-bytes);
    }

    /** Throws the {@link ResourceLimitError} if the scope is terminated or its deadline passed. */
    public void check() {
        ResourceLimitError t = terminated.get();
        if (t != null) throw t;
        if (timed && System.nanoTime() - deadline >= 0) {
            throw terminate(ResourceLimitError.Kind.TIME, "time limit exceeded");
        }
    }

    /**
     * Ends the request. Cancels the timer; the scope cannot be bound any more. Idempotent. The
     * bindings must have been closed.
     */
    @Override
    public void close() {
        ScheduledFuture<?> t;
        boolean stillBound;
        synchronized (lock) {
            closed = true;
            stillBound = !bindings.isEmpty();
            t = timer;
            timer = null;
        }
        if (t != null) t.cancel(false);
        if (stillBound) {
            throw new IllegalStateException("The scope still has open bindings");
        }
    }

    private ResourceLimitError terminate(ResourceLimitError.Kind kind, String message) {
        ResourceLimitError e = new ResourceLimitError(kind, message);
        return terminated.compareAndSet(null, e) ? e : terminated.get();
    }

    private void expire() {
        terminate(ResourceLimitError.Kind.TIME, "time limit exceeded");
        interruptBindings();
    }

    private void interruptBindings() {
        if (!limits.interruptBlockingCalls()) return;
        synchronized (lock) {
            for (Binding b : bindings) {
                b.interrupt();
            }
        }
    }

    /**
     * Charges the memory of the script running on the current thread, if any, to its scopes: for
     * the built-ins and host functions that are about to allocate a large block (a thread-local
     * lookup, so not for small ones). A no-op without a binding.
     *
     * @throws ResourceLimitError if a budget is exceeded
     */
    public static void chargeCurrent(long bytes) {
        Context cx = Context.getCurrentContext();
        if (cx != null) {
            Binding b = cx.resourceBinding;
            if (b != null) b.charge(bytes);
        }
    }

    /**
     * Counts work done by a built-in or a host function as instructions of the script running on
     * the current thread, so that its deadline is checked: for loops that run long without calling
     * back into the script. Call it every few hundred iterations, not at each one.
     *
     * @throws ResourceLimitError if a scope is terminated
     */
    public static void pollCurrent(int units) {
        Context cx = Context.getCurrentContext();
        if (cx != null && cx.instructionThreshold != 0) {
            ScriptRuntime.addInstructionCount(cx, units);
        }
    }

    /** Called when a string grows beyond the range of an int: ends a scoped script. */
    static void stringOverflow() {
        Context cx = Context.getCurrentContext();
        if (cx != null) {
            Binding b = cx.resourceBinding;
            if (b != null) b.stringOverflow();
        }
    }

    /** Checks the size in bits of a BigInt about to be computed, and charges it. */
    static void checkBigIntBits(long bits) {
        Context cx = Context.getCurrentContext();
        if (cx != null) {
            Binding b = cx.resourceBinding;
            if (b != null) b.checkBigIntBits(bits);
        }
    }

    /** The activation of a scope on one Context. */
    public static final class Binding implements AutoCloseable {
        private final ResourceScope scope;
        private final Context cx;
        private final Thread thread;
        private Binding previous;
        private final long allocBaseline;
        private final int savedThreshold;
        private final int savedCount;
        private final boolean savedGenerate;
        private final int savedStackDepth;
        private boolean closed;
        // guarded by scope.lock
        private boolean interrupted;

        private Binding(ResourceScope scope, Context cx, Thread thread, long allocBaseline) {
            this.scope = scope;
            this.cx = cx;
            this.thread = thread;
            this.allocBaseline = allocBaseline;
            this.previous = cx.resourceBinding;
            savedThreshold = cx.instructionThreshold;
            savedCount = cx.instructionCount;
            savedGenerate = cx.generateObserverCount;
            savedStackDepth = cx.maximumInterpreterStackDepth;

            int interval = scope.limits.checkInterval();
            cx.instructionThreshold =
                    savedThreshold == 0 ? interval : Math.min(savedThreshold, interval);
            cx.instructionCount = 0;
            cx.generateObserverCount = true;
            int depth = scope.limits.maxInterpreterStackDepth();
            if (depth > 0) {
                cx.maximumInterpreterStackDepth = Math.min(savedStackDepth, depth);
            }
            cx.resourceBinding = this;
        }

        public ResourceScope getScope() {
            return scope;
        }

        /** Ends the binding and restores the Context. Idempotent. */
        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != thread) {
                throw new IllegalStateException("A binding is closed on the thread that made it");
            }
            closed = true;
            boolean wasInterrupted;
            synchronized (scope.lock) {
                scope.bindings.remove(this);
                wasInterrupted = interrupted;
            }
            if (cx.resourceBinding == this) {
                cx.resourceBinding = previous;
            } else {
                // closed out of order: unlink this one, keep the newer ones working
                for (Binding b = cx.resourceBinding; b != null; b = b.previous) {
                    if (b.previous == this) {
                        b.previous = previous;
                        break;
                    }
                }
            }
            cx.instructionThreshold = savedThreshold;
            cx.instructionCount = savedCount;
            cx.generateObserverCount = savedGenerate;
            cx.maximumInterpreterStackDepth = savedStackDepth;
            if (wasInterrupted) {
                // the interrupt was meant for the script: it must not reach the next request
                Thread.interrupted();
            }
        }

        // called with scope.lock held
        private void interrupt() {
            if (!closed) {
                interrupted = true;
                thread.interrupt();
            }
        }

        /** Called by the observer of the Context: every binding in the chain is checked. */
        void check() {
            for (Binding b = this; b != null; b = b.previous) {
                b.scope.check();
                long max = b.scope.limits.maxAllocatedBytes();
                if (max > 0 && AllocationCounter.current() - b.allocBaseline > max) {
                    throw b.scope.terminate(
                            ResourceLimitError.Kind.ALLOCATION,
                            "allocation limit exceeded (" + max + " bytes)");
                }
            }
        }

        void charge(long bytes) {
            for (Binding b = this; b != null; b = b.previous) {
                b.scope.charge(bytes);
            }
        }

        void stringOverflow() {
            throw scope.terminate(ResourceLimitError.Kind.MEMORY, "string too long");
        }

        void checkBigIntBits(long bits) {
            for (Binding b = this; b != null; b = b.previous) {
                int max = b.scope.limits.maxBigIntBits();
                if (max > 0 && bits > max) {
                    throw b.scope.terminate(
                            ResourceLimitError.Kind.MEMORY,
                            "BigInt too large (" + bits + " > " + max + " bits)");
                }
            }
            charge(bits >> 3);
        }
    }

    private static final class AllocationCounter {
        private static final com.sun.management.ThreadMXBean BEAN = lookup();

        private static com.sun.management.ThreadMXBean lookup() {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean b
                    && b.isThreadAllocatedMemorySupported()
                    && b.isThreadAllocatedMemoryEnabled()) {
                return b;
            }
            return null;
        }

        static boolean isSupported() {
            try {
                return BEAN != null;
            } catch (LinkageError e) {
                return false;
            }
        }

        static long current() {
            return BEAN.getCurrentThreadAllocatedBytes();
        }
    }

    private static final class Watchdog {
        private static final ScheduledThreadPoolExecutor TIMER = create();

        private static ScheduledThreadPoolExecutor create() {
            ScheduledThreadPoolExecutor t =
                    new ScheduledThreadPoolExecutor(
                            1,
                            r -> {
                                Thread th = new Thread(r, "rhino-resource-watchdog");
                                th.setDaemon(true);
                                return th;
                            });
            t.setRemoveOnCancelPolicy(true);
            return t;
        }

        static ScheduledFuture<?> schedule(Runnable r, long delayNanos) {
            return TIMER.schedule(r, Math.max(delayNanos, 0), TimeUnit.NANOSECONDS);
        }
    }
}
