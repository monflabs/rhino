/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.test;

import org.junit.jupiter.api.Disabled;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.inprocess.InProcessCdpServer;
import org.monflabs.mozilla.debugger.ui.cdp.CdpConnection;

/**
 * {@link CdpConnectionTest}'s own scenarios - request/response, protocol errors, events, close -
 * against {@link InProcessCdpServer} instead of a real socket. The same {@link CdpConnection}
 * drives both; only the channel underneath it differs.
 */
@SuppressWarnings({"javadoc", "deprecation"})
public class InProcessCdpConnectionTest extends CdpConnectionTest {

    @Override
    protected void openServer() {
        server = InProcessCdpServer.open(engine.debugger(), InspectOptions.parse("", false));
    }

    @Override
    protected CdpConnection connect(final Events events) {
        return CdpConnection.open(((InProcessCdpServer.Handle) server).clientChannel(), events);
    }

    /**
     * Not a gap to fill in later: the refusal this asserts needs a discoverable {@code ws://} url a
     * second party can dial while the first is attached. An in-process channel has no url at all -
     * the only way to reach the session is the one handle {@code open()} returned - so the failure
     * mode does not exist for this transport.
     */
    @Override
    @Disabled("no ws url in-process")
    public void aSecondClientIsRefusedAsBusy() {
        // see the javadoc
    }
}
