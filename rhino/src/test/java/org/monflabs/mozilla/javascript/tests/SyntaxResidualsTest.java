/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.RhinoException;
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

    @Test
    void arrowCoverAndCommentsAndEval() {
        assertBoth(
                "1|SyntaxError|2|1|local",
                "var r = [];"
                        + " var af = ({ x = 1 }) => x; r.push(af({}));"
                        + " try { eval('({ x = 1 })'); r.push('no'); } catch (e) { r.push(e.name); }"
                        + " r.push(eval(\"1/*\\n*/\\n2\"));"
                        + " var x = 'global';"
                        + " (function () { var x = 'local'; eval(...['x = 1']); r.push(x); })();"
                        + " r.push((function () { var x = 'local'; eval(...['1']); return x; })());"
                        + " r.join('|')");
    }

    @Test
    void instanceofAndRestWithProxies() {
        assertBoth(
                "true|false|TypeError|a,1",
                "var r = [];"
                        + " var F = { [Symbol.hasInstance](v) { return v === 0; } }; r.push(0 instanceof F);"
                        + " r.push(1 instanceof function () {});"
                        + " try { 1 instanceof Math; } catch (e) { r.push(e.name); }"
                        + " var log = []; var p = new Proxy({ 0: 1, a: 2 }, { getOwnPropertyDescriptor(t, k) {"
                        + " log.push(k); return Reflect.getOwnPropertyDescriptor(t, k); } });"
                        + " var { 0: z, ...rest } = p; r.push(Object.keys(rest).concat(z).join());"
                        + " r.join('|')");
    }

    @Test
    void letAsIdentifier() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "1|2|3",
                "try { eval('var let = 1; var r = [let]; let = 2; r.push(let);"
                        + " var o = { let }; r.push(o.let + 1); r.join(\"|\")'); } catch (e) { e.name }");
    }

    @Test
    void supplementaryIdentifiers() {
        assertBoth(
                "3",
                "var \uD80C\uDC00 = 1; var a\uD80C\uDC00 = 2; String(\uD80C\uDC00 + a\uD80C\uDC00)");
    }

    @Test
    void globalDeclarationInstantiation() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    var scope = cx.initStandardObjects();
                    cx.evaluateString(
                            scope,
                            "Object.defineProperty(this, 'cfg', { configurable: true, value: 0 });",
                            "a",
                            1,
                            null);
                    cx.evaluateString(scope, "var NaN; function cfg() {}", "b", 1, null);
                    String error;
                    try {
                        cx.evaluateString(scope, "var created; let undefined;", "c", 1, null);
                        error = "none";
                    } catch (RhinoException e) {
                        error = "SyntaxError";
                    }
                    assertEquals(
                            "function|NaN|SyntaxError|undefined",
                            cx.evaluateString(
                                    scope,
                                    "[typeof cfg, String(NaN), '"
                                            + error
                                            + "', typeof created].join('|')",
                                    "d",
                                    1,
                                    null));
                    return null;
                });
    }
}
