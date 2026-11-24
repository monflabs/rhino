/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/** An evaluation failed: the expression did not parse, or threw. */
public final class DebugException extends Exception {
    private static final long serialVersionUID = 1L;

    private final transient Object thrown;

    /**
     * Creates the exception.
     *
     * @param message the message
     * @param thrown the value the script threw, an engine object, or null for a parse error
     * @param cause the underlying exception
     */
    public DebugException(final String message, final Object thrown, final Throwable cause) {
        super(message, cause);
        this.thrown = thrown;
    }

    /**
     * The value the script threw, if the expression threw.
     *
     * @return an engine object, or null
     */
    public Object thrown() {
        return thrown;
    }
}
