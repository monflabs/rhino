/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link HostJobScheduler} run by the thread of one Context: {@link #run(Context)} runs the jobs
 * as they are posted or fall due, until none is expected or scheduled any more. The jobs that fall
 * due while microtasks run (a script polling with promise reactions) run between them.
 *
 * <pre>{@code
 * EventLoop loop = new EventLoop();
 * cx.setHostJobScheduler(loop);
 * cx.evaluateString(scope, source, "script", 1, null);
 * loop.run(cx);
 * }</pre>
 */
public final class EventLoop implements HostJobScheduler {

    private static final Job WAKE_UP = cx -> {};

    private final LinkedBlockingQueue<Job> posted = new LinkedBlockingQueue<>();
    private final AtomicInteger expected = new AtomicInteger();
    private final PriorityQueue<Timer> timers = new PriorityQueue<>();
    private long sequence;

    private final class Timer implements Comparable<Timer>, Cancelable {
        final Job job;
        final long due;
        final long order;

        Timer(Job job, long due, long order) {
            this.job = job;
            this.due = due;
            this.order = order;
        }

        @Override
        public int compareTo(Timer other) {
            int byDue = Long.compare(due - other.due, 0);
            return byDue != 0 ? byDue : Long.compare(order, other.order);
        }

        @Override
        public void cancel() {
            synchronized (EventLoop.this) {
                timers.remove(this);
            }
        }
    }

    @Override
    public Pending expect() {
        expected.incrementAndGet();
        AtomicBoolean done = new AtomicBoolean();
        return new Pending() {
            @Override
            public void post(Job job) {
                if (done.compareAndSet(false, true)) {
                    // queued before the count drops, so that run() cannot miss it
                    posted.add(job);
                    expected.decrementAndGet();
                }
            }

            @Override
            public void cancel() {
                if (done.compareAndSet(false, true)) {
                    expected.decrementAndGet();
                    posted.add(WAKE_UP);
                }
            }
        };
    }

    @Override
    public synchronized Cancelable schedule(Job job, long delayMillis) {
        long delay = TimeUnit.MILLISECONDS.toNanos(Math.max(delayMillis, 0));
        Timer timer = new Timer(job, System.nanoTime() + delay, sequence++);
        timers.add(timer);
        return timer;
    }

    /** Runs the jobs until none is posted, expected or scheduled. */
    public void run(Context cx) {
        run(cx, Long.MAX_VALUE);
    }

    /**
     * Runs the jobs until none is posted, expected or scheduled, or the timeout has passed, or the
     * thread is interrupted.
     *
     * @return true when no job is left
     */
    public boolean run(Context cx, long timeoutMillis) {
        long start = System.nanoTime();
        long timeout =
                timeoutMillis == Long.MAX_VALUE
                        ? Long.MAX_VALUE
                        : TimeUnit.MILLISECONDS.toNanos(Math.max(timeoutMillis, 0));
        cx.processMicrotasks();
        while (true) {
            if (runReady(cx)) {
                continue;
            }
            long wait;
            synchronized (this) {
                if (expected.get() == 0 && timers.isEmpty() && posted.isEmpty()) {
                    return true;
                }
                wait = timers.isEmpty() ? Long.MAX_VALUE : timers.peek().due - System.nanoTime();
            }
            if (timeout != Long.MAX_VALUE) {
                long left = timeout - (System.nanoTime() - start);
                if (left <= 0) {
                    return false;
                }
                wait = Math.min(wait, left);
            }
            try {
                Job job = posted.poll(Math.max(wait, 0), TimeUnit.NANOSECONDS);
                if (job != null) {
                    job.run(cx);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** Runs the jobs posted and the ones due; true if any ran. */
    boolean runReady(Context cx) {
        boolean ran = false;
        Job job;
        while ((job = posted.poll()) != null) {
            job.run(cx);
            ran = true;
        }
        while (true) {
            Timer timer;
            synchronized (this) {
                timer = timers.peek();
                if (timer == null || timer.due - System.nanoTime() > 0) {
                    break;
                }
                timers.poll();
            }
            timer.job.run(cx);
            ran = true;
        }
        return ran;
    }
}
