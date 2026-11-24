/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * A thread paused in script. The thread stays paused until one of the resuming methods is called -
 * from any thread - and meanwhile runs whatever {@link #call} is handed, so that script objects are
 * only ever touched by the thread that owns them.
 */
public interface PausedEvent {

    /**
     * The paused thread.
     *
     * @return the thread
     */
    Thread thread();

    /**
     * Why it paused.
     *
     * @return the reason
     */
    PauseReason reason();

    /**
     * The call frames, innermost first.
     *
     * @return the frames
     */
    List<DebugFrame> frames();

    /**
     * The breakpoints that caused the pause, if any.
     *
     * @return the breakpoint ids
     */
    List<String> hitBreakpoints();

    /**
     * The exception being thrown, for a pause on an exception; otherwise null.
     *
     * @return the thrown value, an engine object
     */
    Object exception();

    /**
     * The context the thread is executing in.
     *
     * @return the context
     */
    ExecutionContext context();

    /**
     * Whether the thread has been resumed.
     *
     * @return true once resumed
     */
    boolean isResumed();

    /** Resumes execution. */
    void resume();

    /** Resumes until the next statement, entering calls. */
    void stepInto();

    /** Resumes until the next statement in this frame or a caller's. */
    void stepOver();

    /** Resumes until the next statement in a caller's frame. */
    void stepOut();

    /**
     * Ends the script: the paused thread resumes by throwing {@link ScriptTerminated}, and keeps
     * throwing it at every statement it reaches until nothing of the script is left on its stack,
     * so that a {@code catch} in the script cannot keep it running.
     */
    void terminate();

    /**
     * Runs an operation on the paused thread and returns its result. Called on the paused thread
     * itself, it runs inline.
     *
     * @param operation what to run
     * @param <T> the result type
     * @return the result
     * @throws IllegalStateException if the thread has been resumed
     * @throws Exception whatever the operation threw
     */
    <T> T call(Callable<T> operation) throws Exception;
}
