/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.test;

import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.inprocess.InProcessCdpServer;

/**
 * {@link DebugSessionTest}'s own scenarios - attach, breakpoints, pauses, frames, scopes,
 * evaluation, stepping, the staleness guard, reattach, the console and watches - against {@link
 * InProcessCdpServer} instead of a real socket. The same {@link
 * org.monflabs.mozilla.debugger.ui.model.DebugSession} drives both; only the attach differs.
 *
 * <p>An in-process channel is one-shot: once it closes there is nothing to redial. So rather than
 * opening one server up front and reattaching to it, each attach opens a fresh session on the very
 * same engine and debugger, which is exactly what the playground's Debug button does. The reattach
 * scenarios still prove what they were written to prove - a client-owned breakpoint survives a
 * detach and is re-armed on the next attach - just not against one literal server instance.
 */
@SuppressWarnings({"javadoc", "deprecation"})
public class InProcessDebugSessionTest extends DebugSessionTest {

    @Override
    protected void openServer() {
        // nothing up front: the channel is opened per attach, below
    }

    @Override
    protected void attachSession() {
        final AutoCloseable previous = server;
        final InProcessCdpServer.Handle handle =
                InProcessCdpServer.open(engine.debugger(), InspectOptions.parse("", false));
        server = handle;
        if (previous != null) {
            // only now: closing the old session ends its CDP conversation, and
            // a conversation that still holds a pause resumes it on the way out
            try {
                previous.close();
            } catch (final Exception ignored) {
                // it is going away either way
            }
        }
        session.attach(handle.clientChannel());
    }
}
