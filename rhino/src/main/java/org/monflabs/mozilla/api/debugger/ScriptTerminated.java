/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * Thrown through a script when {@link PausedEvent#terminate()} ends it. An {@link Error} rather
 * than an exception, so that script code catching what it can does not keep it; and the debugger
 * throws it again at every statement the thread reaches until the script has unwound entirely.
 */
public final class ScriptTerminated extends Error {
    private static final long serialVersionUID = 1L;

    /** Creates the error. */
    public ScriptTerminated() {
        super("script terminated by the debugger", null, false, false);
    }
}
