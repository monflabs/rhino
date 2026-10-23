/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A console and listener for the tests: collects the output, waits for the end. */
final class Recorder implements ScriptRunner.Console, ScriptRunner.Listener {
    final StringBuffer out = new StringBuffer();
    final StringBuffer err = new StringBuffer();
    final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile ScriptRunner.Result result;

    @Override
    public void out(String text) {
        out.append(text);
    }

    @Override
    public void err(String text) {
        err.append(text);
    }

    @Override
    public void started() {
        started.countDown();
    }

    @Override
    public void finished(ScriptRunner.Result r) {
        result = r;
        done.countDown();
    }

    /** Waits for the run to finish, naming {@code what} if it does not. */
    ScriptRunner.Result await(long seconds, String what) throws InterruptedException {
        if (!done.await(seconds, TimeUnit.SECONDS)) {
            throw new AssertionError(what + " did not finish within " + seconds + " s");
        }
        return result;
    }
}
