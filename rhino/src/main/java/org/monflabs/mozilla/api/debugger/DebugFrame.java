/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.util.List;

/** A call frame of a paused thread. Valid until the thread resumes. */
public interface DebugFrame {

    /**
     * The id, unique within the pause: the frame's index, innermost first.
     *
     * @return the id
     */
    String id();

    /**
     * The function's name; empty for an anonymous function, {@code <program>} for a script body.
     *
     * @return the name
     */
    String functionName();

    /**
     * Where the frame is: the statement it executes.
     *
     * @return the location
     */
    Location location();

    /**
     * Where the function begins, or null if unknown.
     *
     * @return the location
     */
    Location functionLocation();

    /**
     * The scopes visible from the frame, innermost first, ending with the global scope.
     *
     * @return the scopes
     */
    List<DebugScope> scopes();

    /**
     * The receiver, an engine object.
     *
     * @return the {@code this} value
     */
    Object thisValue();

    /**
     * Evaluates an expression as if written at the frame's location. Runs on the paused thread.
     *
     * @param expression the expression
     * @return the value, an engine object
     * @throws DebugException if the expression throws or does not parse
     */
    Object evaluate(String expression) throws DebugException;
}
