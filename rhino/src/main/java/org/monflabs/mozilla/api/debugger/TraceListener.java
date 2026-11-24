/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * A passive observer of execution: statements as they are reached, and the completion value of each
 * program-level expression statement. Nothing pauses; the stream coexists with breakpoints,
 * stepping and an attached protocol client.
 *
 * <p>Both callbacks run on the script's own thread, with its realm bound and further hooks
 * suppressed for their duration. Do not call back into script directly; read values through {@link
 * Debugger#values()}, whose {@code callFunction} and {@code evaluateWith} are safe here.
 */
public interface TraceListener {

    /**
     * A statement is about to run.
     *
     * @param script the script it belongs to
     * @param line the statement's line, zero based
     * @param column the statement's column, zero based
     * @param depth the number of script frames on the stack: 1 for a top-level statement of a
     *     program or module, more inside calls
     */
    default void statementReached(
            final DebugScript script, final int line, final int column, final int depth) {}

    /**
     * A program-level expression statement just produced its completion value - what {@code eval}
     * would return if the program ended here.
     *
     * @param script the script
     * @param line the statement's line, zero based
     * @param value the value, in the engine's own representation
     */
    default void completionValue(final DebugScript script, final int line, final Object value) {}
}
