/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2024;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EventLoop;
import org.monflabs.mozilla.javascript.HostJobScheduler;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;
import org.monflabs.mozilla.javascript.typedarrays.NativeSharedArrayBuffer;

/** Atomics.waitAsync and the host job scheduler (plan 10.3). */
class AtomicsWaitAsyncTest {

    private static final String SETUP =
            "var ta = new Int32Array(new SharedArrayBuffer(16)); var log = [];\n";

    /** Evaluates the script with an EventLoop, runs the loop, and returns log.join(). */
    private static void assertWithEventLoop(String expected, String script) {
        for (Context.EvaluationMethod mode : Context.EvaluationMethod.values()) {
            Utils.runWithMode(
                    cx -> {
                        cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                        EventLoop loop = new EventLoop();
                        cx.setHostJobScheduler(loop);
                        TopLevel scope = cx.initStandardObjects();
                        cx.evaluateString(scope, SETUP + script, "test.js", 1, null);
                        assertTrue(loop.run(cx, 10_000));
                        assertEquals(
                                expected,
                                cx.evaluateString(scope, "log.join()", "log.js", 1, null));
                        return null;
                    },
                    mode);
        }
    }

    @Test
    void withoutSchedulerOnlyTheSynchronousResults() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "false,not-equal,false,timed-out,TypeError,1",
                SETUP
                        + "var a = Atomics.waitAsync(ta, 0, 1); var b = Atomics.waitAsync(ta, 0, 0, 0);\n"
                        + "log.push(a.async, a.value, b.async, b.value);\n"
                        + "try { Atomics.waitAsync(ta, 0, 0); } catch (e) { log.push(e.name); }\n"
                        + "log.push(Object.keys(a).indexOf('value'));\n"
                        + "log.join()");
    }

    @Test
    void notifiedBySameAgent() {
        assertWithEventLoop(
                "true,true,1,0,ok",
                "var r = Atomics.waitAsync(ta, 0, 0, 60000);\n"
                        + "log.push(r.async, r.value instanceof Promise);\n"
                        + "r.value.then(v => log.push(v));\n"
                        + "log.push(Atomics.notify(ta, 0, 1), Atomics.notify(ta, 0));");
    }

    @Test
    void timedOut() {
        assertWithEventLoop(
                "timed-out,0,second",
                "Atomics.waitAsync(ta, 1, 0, 5).value.then(v => {"
                        + " log.push(v, Atomics.notify(ta, 1)); });\n"
                        + "Atomics.waitAsync(ta, 2, 0, 20).value.then(v => log.push('second'));\n");
    }

    @Test
    void timeoutWhilePollingWithMicrotasks() {
        // the harness of test262 polls with promise reactions: the timeout fires between them
        assertWithEventLoop(
                "timed-out",
                "var done = false;\n"
                        + "Atomics.waitAsync(ta, 0, 0, 5).value.then(v => { log.push(v); done = true; });\n"
                        + "(function poll() { if (!done) Promise.resolve().then(poll); })();");
    }

    @Test
    void notifiedByAnotherThread() throws Exception {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            EventLoop loop = new EventLoop();
            cx.setHostJobScheduler(loop);
            TopLevel scope = cx.initStandardObjects();
            cx.evaluateString(
                    scope,
                    SETUP + "Atomics.waitAsync(ta, 0, 0).value.then(v => log.push(v));",
                    "test.js",
                    1,
                    null);
            NativeSharedArrayBuffer sab =
                    (NativeSharedArrayBuffer)
                            cx.evaluateString(scope, "ta.buffer", "buffer.js", 1, null);
            Thread notifier =
                    new Thread(
                            () -> {
                                try (Context other = Context.enter()) {
                                    other.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                                    TopLevel realm = other.initStandardObjects();
                                    ScriptableObject.putProperty(
                                            realm,
                                            "sab",
                                            NativeSharedArrayBuffer.share(realm, sab));
                                    other.evaluateString(
                                            realm,
                                            "Atomics.notify(new Int32Array(sab), 0)",
                                            "notifier.js",
                                            1,
                                            null);
                                }
                            });
            notifier.start();
            assertTrue(loop.run(cx, 10_000));
            notifier.join();
            assertEquals("ok", cx.evaluateString(scope, "log.join()", "log.js", 1, null));
        }
    }

    @Test
    void customScheduler() {
        // a host that runs the jobs itself, later
        List<HostJobScheduler.Job> jobs = new ArrayList<>();
        HostJobScheduler scheduler =
                new HostJobScheduler() {
                    @Override
                    public Pending expect() {
                        return new Pending() {
                            @Override
                            public void post(Job job) {
                                jobs.add(job);
                            }

                            @Override
                            public void cancel() {}
                        };
                    }

                    @Override
                    public Cancelable schedule(Job job, long delayMillis) {
                        return () -> {};
                    }
                };
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setHostJobScheduler(scheduler);
            TopLevel scope = cx.initStandardObjects();
            cx.evaluateString(
                    scope,
                    SETUP
                            + "Atomics.waitAsync(ta, 0, 0, 1000).value.then(v => log.push(v));\n"
                            + "Atomics.notify(ta, 0);",
                    "test.js",
                    1,
                    null);
            assertEquals("", cx.evaluateString(scope, "log.join()", "log.js", 1, null));
            assertEquals(1, jobs.size());
            jobs.get(0).run(cx);
            assertEquals("ok", cx.evaluateString(scope, "log.join()", "log.js", 1, null));
        }
    }
}
