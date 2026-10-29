/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.ResourceLimitError;
import org.monflabs.mozilla.javascript.ResourceLimits;
import org.monflabs.mozilla.javascript.ResourceScope;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class ResourceLimitsTest {

    private static ResourceLimits.Builder quick() {
        return ResourceLimits.builder().timeout(Duration.ofMillis(300)).checkInterval(1000);
    }

    private static <T> T onContext(org.monflabs.mozilla.javascript.ContextAction<T> action) {
        try (Context cx = new ContextFactory().enterContext()) {
            return action.run(cx);
        }
    }

    private static ResourceLimitError run(ResourceLimits limits, Context cx, String source) {
        try (ResourceScope scope = limits.newScope();
                ResourceScope.Binding ignored = scope.bind(cx)) {
            TopLevel global = cx.initSafeStandardObjects();
            cx.evaluateString(global, source, "request", 1, null);
            return null;
        } catch (ResourceLimitError e) {
            return e;
        }
    }

    private static void assertTerminates(
            ResourceLimitError.Kind kind, ResourceLimits limits, String source) {
        Utils.runWithAllModes(
                cx -> {
                    ResourceLimitError e = run(limits, cx, source);
                    assertTrue(e != null, "not terminated: " + source);
                    assertEquals(kind, e.getKind(), source);
                    return null;
                });
    }

    @Test
    void infiniteLoopsTimeOut() {
        for (String src :
                new String[] {
                    "while (true) {}",
                    "for (;;) {}",
                    "do {} while (true)",
                    "var i = 0; while (true) { i++; }",
                    "function f() { for(;;); } f()",
                    "[1,2,3].forEach(function () { while (true); })",
                }) {
            assertTerminates(ResourceLimitError.Kind.TIME, quick().build(), src);
        }
    }

    @Test
    void scriptCannotSwallowTheError() {
        assertTerminates(
                ResourceLimitError.Kind.TIME,
                quick().build(),
                "while (true) { try { while (true) {} } catch (e) {} finally { while (true) {} } }");
    }

    @Test
    void runawayRecursionEndsCleanly() {
        ResourceLimits limits = quick().maxInterpreterStackDepth(500).build();
        Utils.runWithAllModes(
                cx -> {
                    // the interpreter throws a catchable error at the depth limit, compiled code
                    // a StackOverflowError that scripts cannot catch: the host sees it
                    try {
                        run(limits, cx, "function f() { return f(); } f()");
                    } catch (StackOverflowError | EvaluatorException expected) {
                        // fine
                    }
                    assertEquals(0, cx.getInstructionObserverThreshold());
                    assertNull(run(limits, cx, "var x = 1"));

                    // swallowing the interpreter's error does not help against the deadline
                    if (cx.isInterpretedMode()) {
                        ResourceLimitError e =
                                run(
                                        limits,
                                        cx,
                                        "function f() { return f(); }"
                                                + "for (;;) { try { f(); } catch (e) {} }");
                        assertEquals(ResourceLimitError.Kind.TIME, e.getKind());
                    }
                    return null;
                });
    }

    @Test
    void nativeLoopsArePolled() {
        for (String src :
                new String[] {
                    "var o = {length: 4294967295}; Array.prototype.indexOf.call(o, 1)",
                    "var a = []; a.length = 4294967295; a.reverse()",
                }) {
            assertTerminates(ResourceLimitError.Kind.TIME, quick().build(), src);
        }
    }

    @Test
    void sortComparatorIsChecked() {
        assertTerminates(
                ResourceLimitError.Kind.TIME,
                quick().build(),
                "var a = []; for (var i = 0; i < 100000; i++) a.push(i);"
                        + "a.sort(function (x, y) { while (true) {} })");
    }

    @Test
    void largeAllocationsAreRefusedBeforeTheyHappen() {
        ResourceLimits limits =
                ResourceLimits.builder()
                        .timeout(Duration.ofSeconds(30))
                        .maxMemoryBytes(8L << 20)
                        .build();
        for (String src :
                new String[] {
                    "'x'.repeat(100000000)",
                    "var s = 'x'; for (;;) s = s + s;",
                    "new Array(100000000).fill(0)",
                    "new ArrayBuffer(100000000)",
                    "'x'.padStart(100000000)",
                    "var a = []; a[50000000] = 1; a.join()",
                }) {
            assertTerminates(ResourceLimitError.Kind.MEMORY, limits, src);
        }
    }

    @Test
    void bigIntsAreBounded() {
        ResourceLimits limits =
                ResourceLimits.builder()
                        .timeout(Duration.ofSeconds(30))
                        .maxBigIntBits(1 << 20)
                        .build();
        for (String src :
                new String[] {
                    "2n ** 100000000n",
                    "1n << 1000000000n",
                    "var x = 3n ** 600000n; x * x * x * x * x * x * x * x * x * x * x * x",
                    "BigInt.asUintN(2000000000, 1n)",
                    "BigInt.asIntN(2000000000, 1n)",
                }) {
            assertTerminates(ResourceLimitError.Kind.MEMORY, limits, src);
        }
    }

    @Test
    void workWithinTheLimitsIsUnaffected() {
        ResourceLimits limits =
                ResourceLimits.builder()
                        .timeout(Duration.ofSeconds(10))
                        .maxMemoryBytes(64L << 20)
                        .maxBigIntBits(1 << 20)
                        .build();
        Utils.runWithAllModes(
                cx -> {
                    try (ResourceScope scope = limits.newScope();
                            ResourceScope.Binding ignored = scope.bind(cx)) {
                        TopLevel global = cx.initSafeStandardObjects();
                        Object r =
                                cx.evaluateString(
                                        global,
                                        "var s = 0; for (var i = 0; i < 300000; i++) s += i;"
                                                + "'ab'.repeat(1000).length + s + Number(2n ** 64n > 0n)",
                                        "request",
                                        1,
                                        null);
                        assertEquals(2000 + 44999850000d + 1, ((Number) r).doubleValue());
                        assertFalse(scope.isTerminated());
                    }
                    return null;
                });
    }

    @Test
    void contextIsRestoredAfterEveryOutcome() {
        Utils.runWithAllModes(
                cx -> {
                    int threshold = cx.getInstructionObserverThreshold();
                    assertTrue(run(quick().build(), cx, "while (true);") != null);
                    assertEquals(threshold, cx.getInstructionObserverThreshold());
                    // the Context is clean for the next request, no leftover termination
                    assertNull(
                            run(
                                    ResourceLimits.builder().build(),
                                    cx,
                                    "var x = 0; for (var i = 0; i < 100000; i++) x++;"));
                    return null;
                });
    }

    @Test
    void releasingTheContextClosesAForgottenBinding() {
        ContextFactory factory = new ContextFactory();
        ResourceScope scope = quick().build().newScope();
        Context cx = factory.enterContext();
        scope.bind(cx);
        Context.exit();
        scope.close();

        Context again = factory.enterContext();
        try {
            assertEquals(0, again.getInstructionObserverThreshold());
        } finally {
            Context.exit();
        }
    }

    @Test
    void terminationIsStickyAndTheScopeCannotBeRebound() {
        ResourceLimits limits = quick().build();
        try (ResourceScope scope = limits.newScope()) {
            ResourceLimitError first =
                    onContext(
                            cx -> {
                                try (ResourceScope.Binding ignored = scope.bind(cx)) {
                                    cx.evaluateString(
                                            cx.initSafeStandardObjects(),
                                            "while (true);",
                                            "r",
                                            1,
                                            null);
                                    return null;
                                } catch (ResourceLimitError e) {
                                    return e;
                                }
                            });
            assertTrue(first != null && scope.isTerminated());
            assertSame(first, scope.getTermination());
            onContext(
                    cx -> {
                        assertThrows(ResourceLimitError.class, () -> scope.bind(cx));
                        return null;
                    });
        }
    }

    @Test
    void cancelFromAnotherThread() throws Exception {
        ResourceScope scope = ResourceLimits.builder().checkInterval(1000).build().newScope();
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResourceLimitError> f =
                    pool.submit(
                            () -> {
                                try (Context cx = new ContextFactory().enterContext();
                                        ResourceScope.Binding ignored = scope.bind(cx)) {
                                    cx.setInterpretedMode(true);
                                    started.countDown();
                                    cx.evaluateString(
                                            cx.initSafeStandardObjects(),
                                            "while (true);",
                                            "r",
                                            1,
                                            null);
                                    return null;
                                } catch (ResourceLimitError e) {
                                    return e;
                                }
                            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            scope.cancel("stop");
            ResourceLimitError e = f.get(5, TimeUnit.SECONDS);
            assertEquals(ResourceLimitError.Kind.CANCELLED, e.getKind());
        } finally {
            pool.shutdownNow();
            scope.close();
        }
    }

    @Test
    void blockingCallsAreInterrupted() throws Exception {
        ResourceLimits limits =
                ResourceLimits.builder()
                        .timeout(Duration.ofMillis(200))
                        .interruptBlockingCalls(true)
                        .build();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> f =
                    pool.submit(
                            () -> {
                                try (ResourceScope scope = limits.newScope();
                                        Context cx = new ContextFactory().enterContext();
                                        ResourceScope.Binding ignored = scope.bind(cx)) {
                                    TopLevel global = cx.initSafeStandardObjects();
                                    global.put(
                                            "sleep",
                                            global,
                                            Context.javaToJS(
                                                    (Runnable)
                                                            () -> {
                                                                try {
                                                                    Thread.sleep(30_000);
                                                                } catch (InterruptedException ex) {
                                                                    Thread.currentThread()
                                                                            .interrupt();
                                                                }
                                                            },
                                                    global));
                                    try {
                                        cx.evaluateString(
                                                global, "sleep.run(); while (true);", "r", 1, null);
                                    } catch (ResourceLimitError e) {
                                        // the interrupt flag must not outlive the binding
                                        return true;
                                    }
                                    return false;
                                }
                            });
            assertTrue(f.get(10, TimeUnit.SECONDS));
            // the worker thread is clean for the next request
            assertFalse(pool.submit(() -> Thread.currentThread().isInterrupted()).get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRequestsDoNotInterfere() throws Exception {
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < n * 4; i++) {
                final boolean runaway = i % 2 == 0;
                results.add(
                        pool.submit(
                                () -> {
                                    ResourceLimits limits =
                                            runaway
                                                    ? quick().build()
                                                    : ResourceLimits.builder()
                                                            .timeout(Duration.ofSeconds(20))
                                                            .build();
                                    try (Context cx = new ContextFactory().enterContext()) {
                                        ResourceLimitError e =
                                                run(
                                                        limits,
                                                        cx,
                                                        runaway
                                                                ? "while (true);"
                                                                : "var s = 0; for (var i = 0; i < 2000000; i++) s += i;");
                                        return runaway == (e != null);
                                    }
                                }));
            }
            for (Future<Boolean> f : results) {
                assertTrue(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nestedScopesTightestWins() {
        ResourceLimits outer =
                ResourceLimits.builder()
                        .timeout(Duration.ofSeconds(30))
                        .checkInterval(1000)
                        .build();
        ResourceLimits inner = quick().build();
        Utils.runWithAllModes(
                cx -> {
                    try (ResourceScope o = outer.newScope();
                            ResourceScope.Binding ob = o.bind(cx)) {
                        ResourceLimitError e = run(inner, cx, "while (true);");
                        assertEquals(ResourceLimitError.Kind.TIME, e.getKind());
                        assertFalse(o.isTerminated());
                        assertNull(run(outer, cx, "var x = 1;"));
                    }
                    return null;
                });
    }

    @Test
    void allocationLimitStopsAllocationLoops() {
        ResourceLimits limits =
                ResourceLimits.builder()
                        .timeout(Duration.ofSeconds(30))
                        .maxAllocatedBytes(32L << 20)
                        .checkInterval(1000)
                        .build();
        assertTerminates(
                ResourceLimitError.Kind.ALLOCATION,
                limits,
                "var a = []; for (;;) a.push({a: 1, b: [1, 2, 3]});");
    }

    @Test
    void allocationLimitIsRefusedOnVirtualThreads() throws Exception {
        ResourceLimits limits = ResourceLimits.builder().maxAllocatedBytes(1 << 20).build();
        Future<Boolean> f;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            f =
                    pool.submit(
                            () -> {
                                try (Context cx = new ContextFactory().enterContext();
                                        ResourceScope scope = limits.newScope()) {
                                    scope.bind(cx);
                                    return false;
                                } catch (UnsupportedOperationException e) {
                                    return true;
                                }
                            });
            assertTrue(f.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void timeLimitWorksOnVirtualThreads() throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ResourceLimitError> f =
                    pool.submit(
                            () -> {
                                try (Context cx = new ContextFactory().enterContext()) {
                                    return run(quick().build(), cx, "while (true);");
                                }
                            });
            assertEquals(ResourceLimitError.Kind.TIME, f.get(10, TimeUnit.SECONDS).getKind());
        }
    }

    @Test
    void interpreterObservesCallBoundaries() {
        Utils.runWithMode(
                cx -> {
                    cx.setInterpretedMode(true);
                    ResourceLimitError e =
                            run(
                                    quick().build(),
                                    cx,
                                    "function f(n) { return n ? f(n - 1) + f(n - 1) : 1; }"
                                            + "for (;;) f(12)");
                    assertTrue(e != null);
                    return null;
                },
                Context.EvaluationMethod.Interpreter);
    }
}
