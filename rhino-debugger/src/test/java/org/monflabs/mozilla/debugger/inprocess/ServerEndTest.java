/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.inprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.debugger.CdpClientChannel;

class ServerEndTest {

    @Test
    void messagesSentByTwoThreadsAreDeliveredOneAtATime() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        ServerEnd server = new ServerEnd(closed);
        ClientEnd client = new ClientEnd(closed);
        server.setPeer(client);
        client.setPeer(server);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        AtomicBoolean overlap = new AtomicBoolean();
        client.listen(
                new CdpClientChannel.ChannelListener() {
                    @Override
                    public void onText(CharSequence data, boolean last) {
                        if (inside.incrementAndGet() > 1) {
                            overlap.set(true);
                        }
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        delivered.incrementAndGet();
                        inside.decrementAndGet();
                    }

                    @Override
                    public void onClosed(String reason) {}
                });
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            threads.add(
                    Thread.ofPlatform()
                            .start(
                                    () -> {
                                        try {
                                            start.await();
                                            for (int n = 0; n < 20; n++) {
                                                server.send("message");
                                            }
                                        } catch (Exception e) {
                                            throw new IllegalStateException(e);
                                        }
                                    }));
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(40, delivered.get());
        assertFalse(overlap.get());
    }
}
