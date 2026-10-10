/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Language residuals (plan 1.10). */
class SyntaxResidualsTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void completionValues() {
        Utils.assertGatedBehavior(
                "1,2,2,8,3",
                ",,,7,",
                "[eval('1; do { } while (false)'), eval('2; if (true) {}'),"
                        + " eval('1; try { } finally { 2; }'), eval('6; try { 7; } finally { 8; }'),"
                        + " eval('3; switch (0) { case 1: 4; }')].join()");
    }

    @Test
    void logicalAssignmentShortCircuits() {
        assertBoth(
                "0|1|ok|2",
                "var r = [];"
                        + " var o = {}; Object.defineProperty(o, 'p', { value: 0, writable: false });"
                        + " r.push(o.p &&= 1);"
                        + " var q = { get p() { return 1; } }; r.push(q.p ||= 2);"
                        + " var calls = 0; var k = { toString() { calls++; return 'x'; } };"
                        + " var t = {}; t[k] ??= 'ok'; r.push(t.x);"
                        + " var n; n ||= 2; r.push(n);"
                        + " r.join('|')");
    }

    @Test
    void compoundAssignmentConvertsKeyOnce() {
        assertBoth(
                "1|TypeError|1",
                "var r = []; var calls = 0;"
                        + " var k = { toString() { calls++; return 'a'; } };"
                        + " var o = { a: 1 }; o[k] += 1; r.push(calls);"
                        + " try { null[{ toString() { throw 'evaluated'; } }] += 1; } catch (e) { r.push(e.name || e); }"
                        + " calls = 0; o[k]++; r.push(calls);"
                        + " r.join('|')");
    }

    @Test
    void withUnscopablesAndStrictPutValue() {
        assertBoth(
                "outer|ReferenceError|TypeError",
                "var r = []; var v = 'outer';"
                        + " var env = { v: 'inner', [Symbol.unscopables]: { v: true } };"
                        + " with (env) { r.push(v); }"
                        + " Object.defineProperty(this, 'gx', { configurable: true, value: 1 });"
                        + " var self = this;"
                        + " (function () { 'use strict'; try { gx = (delete self.gx, 2); r.push('no'); }"
                        + " catch (e) { r.push(e.name); } })();"
                        + " (function () { 'use strict'; var o = {};"
                        + " Object.defineProperty(o, 'p', { get() { return 1; }, set: undefined });"
                        + " try { o.p = 2; r.push('no'); } catch (e) { r.push(e.name); } })();"
                        + " r.join('|')");
    }
}
