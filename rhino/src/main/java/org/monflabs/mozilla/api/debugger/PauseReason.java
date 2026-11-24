/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/** Why a thread paused. */
public enum PauseReason {
    /** A breakpoint was hit. */
    BREAKPOINT,
    /** A step completed. */
    STEP,
    /** {@link Debugger#pause()} was called. */
    DEBUG_COMMAND,
    /** An exception was thrown. */
    EXCEPTION,
    /** The first statement of a script, after {@link Debugger#pauseOnStart()}. */
    START,
    /** A {@code debugger} statement was reached. */
    DEBUGGER_STATEMENT,
    /** Something else. */
    OTHER
}
