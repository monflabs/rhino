/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.io.IOException;

/**
 * A debugger frontend that the {@code --inspect} option starts: something that exposes a {@link
 * Debugger} to the outside, such as the Chrome DevTools Protocol server of the {@code
 * rhino-debugger} artifact. Found through {@link java.util.ServiceLoader}.
 */
public interface DebuggerFrontend {

    /**
     * The frontend's name, for messages.
     *
     * @return the name
     */
    String name();

    /**
     * Starts the frontend. With {@link InspectOptions#waitForDebugger()} set, returns only once a
     * client has attached and asked execution to proceed.
     *
     * @param debugger the debugger to expose
     * @param options where to listen
     * @return a handle that stops the frontend
     * @throws IOException if the frontend cannot start
     */
    AutoCloseable start(Debugger debugger, InspectOptions options) throws IOException;
}
