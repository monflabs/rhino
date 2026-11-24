/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * Where a {@link DebuggerFrontend} listens.
 *
 * @param host the host to bind
 * @param port the port to bind; 0 for one the system picks
 * @param waitForDebugger whether execution waits for a client to attach
 */
public record InspectOptions(String host, int port, boolean waitForDebugger) {

    /** The default host. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** The default port - Node's. */
    public static final int DEFAULT_PORT = 9229;

    /**
     * Parses {@code [host:]port}, {@code host} or the empty string, as the {@code --inspect} option
     * accepts them.
     *
     * @param spec the specification, possibly null or empty
     * @param waitForDebugger whether execution waits for a client
     * @return the options
     * @throws IllegalArgumentException if the port is not a number
     */
    public static InspectOptions parse(final String spec, final boolean waitForDebugger) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (spec != null && !spec.isEmpty() && !"true".equals(spec)) {
            final int colon = spec.lastIndexOf(':');
            final String portText;
            if (colon >= 0) {
                if (colon > 0) {
                    host = spec.substring(0, colon);
                }
                portText = spec.substring(colon + 1);
            } else if (spec.chars().allMatch(Character::isDigit)) {
                portText = spec;
            } else {
                host = spec;
                portText = "";
            }
            if (!portText.isEmpty()) {
                try {
                    port = Integer.parseInt(portText);
                } catch (final NumberFormatException e) {
                    throw new IllegalArgumentException("not a port number: " + portText, e);
                }
            }
        }
        return new InspectOptions(host, port, waitForDebugger);
    }
}
