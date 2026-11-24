/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

/**
 * One line of the debugger console.
 *
 * @param kind what produced it
 * @param text the text
 */
public record ConsoleEntry(Kind kind, String text) {
    /** Where a console line came from. */
    public enum Kind {
        /** A {@code console.log} and its kin, or other standard output. */
        LOG,
        /** An error - {@code console.error}, or an uncaught exception. */
        ERROR,
        /** An expression the user typed into the console prompt. */
        EVAL_INPUT,
        /** The result of an evaluated expression. */
        EVAL_RESULT,
        /** A status note from the debugger itself. */
        STATUS
    }
}
