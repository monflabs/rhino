/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * Runs the jobs that host operations enqueue to finish later, from another thread or after a delay
 * (ECMA-262 9.5.4 HostEnqueueGenericJob, 9.5.6 HostEnqueueTimeoutJob): the settlement of the
 * promise of {@code Atomics.waitAsync}. Without a scheduler on the {@link Context}, such an
 * operation throws a TypeError when it would have to wait.
 *
 * <p>A server can implement it over its own executor or event loop; {@link EventLoop} is a simple
 * implementation, run by the thread of the Context. Whatever the implementation, the jobs of a
 * Context run one at a time, on a thread where that Context is current, and never while a script of
 * that Context is running.
 *
 * @see Context#setHostJobScheduler(HostJobScheduler)
 */
public interface HostJobScheduler {

    /** A job: running it settles what the host operation started, then runs the microtasks. */
    interface Job {
        void run(Context cx);
    }

    /** What a job that has not run yet can be cancelled through. */
    interface Cancelable {
        /** The job will not run; nothing happens if it has run or was handed over already. */
        void cancel();
    }

    /** A job announced before it is known, so that the scheduler can wait for it. */
    interface Pending extends Cancelable {
        /** Hands over the job, from any thread; at most once, and not after {@link #cancel()}. */
        void post(Job job);
    }

    /**
     * Announces a job that will be posted later, possibly from another thread (as by an {@code
     * Atomics.notify} of another agent). Called on the thread of the Context.
     */
    Pending expect();

    /**
     * Runs a job once the delay has passed, unless it is cancelled before. Called on the thread of
     * the Context.
     */
    Cancelable schedule(Job job, long delayMillis);
}
