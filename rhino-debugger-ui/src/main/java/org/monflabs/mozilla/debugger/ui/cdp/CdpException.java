/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.cdp;

/**
 * A protocol failure: either a JSON-RPC {@code error} the server returned for a request, or a
 * transport-level condition mapped to a code of its own. The {@link #code()} is the server's for a
 * real protocol error, and one of the negative {@code TRANSPORT_*} constants otherwise, so a client
 * can tell "the server refused this call" from "there is no connection".
 */
public final class CdpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The connection was refused because another client is already attached (HTTP 403). */
    public static final int TRANSPORT_BUSY = -403;

    /** The connection closed, or never opened, for any other reason. */
    public static final int TRANSPORT_CLOSED = -1;

    private final int code;

    /**
     * Creates a failure.
     *
     * @param code the server's error code, or a {@code TRANSPORT_*} constant
     * @param message the message
     */
    public CdpException(final int code, final String message) {
        super(message);
        this.code = code;
    }

    /**
     * The error code.
     *
     * @return the code
     */
    public int code() {
        return code;
    }

    /** Whether this is a transport failure rather than a protocol error. */
    public boolean isTransport() {
        return code == TRANSPORT_BUSY || code == TRANSPORT_CLOSED;
    }
}
