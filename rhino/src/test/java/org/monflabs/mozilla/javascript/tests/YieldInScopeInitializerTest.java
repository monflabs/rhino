/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Compiled generators and async functions: a destructuring pattern whose value yields or awaits
 * keeps the scope of its temporaries across the suspension, and a returned null stays null.
 */
class YieldInScopeInitializerTest {

    @Test
    void generators() {
        String expected = "7,7,7,7,3";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "function run(g) { var it = g(); it.next(); return it.next([3, 4]).value; }"
                        + "[run(function* () { var [a, b] = yield 1; return a + b; }),"
                        + " run(function* () { let [a, b] = yield 1; return a + b; }),"
                        + " run(function* () { var a, b; [a, b] = yield 1; return a + b; }),"
                        + " run(function* () { var { 0: a, 1: b } = yield 1; return a + b; }),"
                        + " run(function* () { const [a] = [yield 1]; return a[0]; })].join()");
    }

    @Test
    void generatorReturnsNull() {
        String expected = "null true";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "function* g() { return null; } function* h() { yield 1; return null; }"
                        + "var it = h(); it.next();"
                        + "String(g().next().value) + ' ' + (it.next().value === null)");
    }

    @Test
    void asyncFunctions() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(
                            scope,
                            "var log = [];"
                                    + "async function f() {"
                                    + "  const [a, b] = await Promise.all([1, 2]); return a + b; }"
                                    + "f().then(v => log.push(v), e => log.push(String(e)));"
                                    + "async function n() { try { await 0; throw 0; }"
                                    + "  catch (e) { return null; } }"
                                    + "n().then(v => log.push(v === null));",
                            "test",
                            1,
                            null);
                    assertEquals(
                            "3,true",
                            cx.evaluateString(scope, "log.sort().join()", "log", 1, null));
                    return null;
                });
    }
}
