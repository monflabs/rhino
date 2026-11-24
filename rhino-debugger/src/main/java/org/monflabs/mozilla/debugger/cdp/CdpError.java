/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.cdp;

/** A protocol error: the code and message the response carries. */
final class CdpError extends Exception {
    private static final long serialVersionUID = 1L;

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int SERVER_ERROR = -32000;

    private final int code;

    CdpError(final int code, final String message) {
        super(message);
        this.code = code;
    }

    int code() {
        return code;
    }

    static CdpError notPaused() {
        return new CdpError(SERVER_ERROR, "Can only perform operation while paused.");
    }

    static CdpError invalidParams(final String what) {
        return new CdpError(INVALID_PARAMS, "Invalid parameters: " + what);
    }
}
