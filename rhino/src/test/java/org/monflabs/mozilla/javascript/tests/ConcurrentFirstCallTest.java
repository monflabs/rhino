/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Function;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.TopLevel;

/**
 * The functions of a compiled script called for the first time by several threads at once: the
 * first call links the function (MHJSFunctionCode), which another thread may see half done.
 */
class ConcurrentFirstCallTest {

    private static final int FUNCTIONS = 200;
    private static final int THREADS = 8;

    @Test
    void functionsCalledFirstByManyThreads() throws Exception {
        StringBuilder source = new StringBuilder("var fs = [];\n");
        for (int i = 0; i < FUNCTIONS; i++) {
            source.append("fs.push(function () { return ").append(i).append("; });\n");
        }
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < 300; round++) {
                TopLevel scope;
                try (Context cx = Context.enter()) {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    Script script = cx.compileString(source.toString(), "calls.js", 1, null);
                    scope = cx.initStandardObjects();
                    script.exec(cx, scope, scope.getGlobalThis());
                }
                Scriptable global = scope.getGlobalThis();
                Scriptable fs = (Scriptable) global.get("fs", global);
                CyclicBarrier start = new CyclicBarrier(THREADS);
                List<Future<Double>> sums = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    sums.add(
                            pool.submit(
                                    () -> {
                                        try (Context cx = Context.enter()) {
                                            start.await();
                                            double sum = 0;
                                            for (int i = 0; i < FUNCTIONS; i++) {
                                                Function f = (Function) fs.get(i, fs);
                                                sum +=
                                                        Context.toNumber(
                                                                f.call(
                                                                        cx,
                                                                        scope,
                                                                        global,
                                                                        new Object[0]));
                                            }
                                            return sum;
                                        }
                                    }));
                }
                for (Future<Double> sum : sums) {
                    assertEquals(FUNCTIONS * (FUNCTIONS - 1) / 2.0, sum.get());
                }
            }
        } finally {
            pool.shutdown();
        }
    }
}
