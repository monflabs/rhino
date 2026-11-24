/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * Receives what the debugger sees. Every method has an empty default, so a listener implements only
 * what it wants. {@link #paused} runs on the thread that paused, before it blocks; everything else
 * runs on the thread that caused the event.
 */
public interface DebugListener {

    /**
     * A global object was created.
     *
     * @param context the new context
     */
    default void executionContextCreated(final ExecutionContext context) {}

    /**
     * A script was compiled and can be executed and broken in.
     *
     * @param script the script
     */
    default void scriptParsed(final DebugScript script) {}

    /**
     * Every execution context and its parsed scripts were discarded - see {@link
     * Debugger#clearScripts()}. Breakpoints are unaffected and re-resolve as scripts parse again.
     */
    default void executionContextsCleared() {}

    /**
     * A pending breakpoint found its script.
     *
     * @param breakpoint the breakpoint
     * @param location where it landed
     */
    default void breakpointResolved(final Breakpoint breakpoint, final Location location) {}

    /**
     * A thread paused. Called on that thread, which blocks after the call until the event is
     * resumed.
     *
     * @param event the pause
     */
    default void paused(final PausedEvent event) {}

    /**
     * A paused thread resumed.
     *
     * @param event the pause that ended
     */
    default void resumed(final PausedEvent event) {}

    /**
     * An exception escaped the outermost script frame of a thread.
     *
     * @param event the exception
     */
    default void exceptionThrown(final ExceptionEvent event) {}

    /**
     * A script called a {@code console} function.
     *
     * @param event the call
     */
    default void consoleCalled(final ConsoleEvent event) {}

    /**
     * The outermost script execution on a thread finished - it ran to completion, an exception
     * escaped it, or it was stopped - and the event loop it drove is idle. Called on that thread,
     * from its own {@code finally}, once per top-level evaluation. A frontend can use it to end the
     * session the way a real inspector closes its connection when the process exits.
     */
    default void executionFinished() {}
}
