/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Annex B (plan 1.11). */
class AnnexBTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void stringAndDateMethods() {
        assertBoth(
                "true,trimEnd,1,1900",
                "var d = new Date(2000, 0, 1); d.setYear(-0.5);"
                        + " [String.prototype.trimLeft === String.prototype.trimStart,"
                        + " String.prototype.trimRight.name, 'x'.anchor.length, d.getFullYear()].join()");
    }

    @Test
    void regExpLegacyFeatures() {
        assertBoth(
                "false,TypeError,TypeError",
                "var r = [];"
                        + " r.push(Object.getOwnPropertyDescriptor(RegExp, 'lastMatch').enumerable);"
                        + " try { /a/.compile(/b/, 'g'); r.push('no'); } catch (e) { r.push(e.name); }"
                        + " try { new (class extends RegExp {})('a').compile(); r.push('no'); }"
                        + " catch (e) { r.push(e.name); }"
                        + " r.join()");
    }

    @Test
    void regExpPatternGrammar() {
        Utils.assertGatedBehavior(
                "false,false,false",
                "true,true,true",
                "[/\\xa/.test('xa'), /[\\c0]/.test('\\x10'), /\\0022/.test('\\x022')].join()");
    }

    @Test
    void htmlCloseCommentAfterBlockComment() {
        assertBoth("0", "var n = 0;\n/*\n*/--> n += 1;\n/* c */ --> n += 1;\nString(n)");
    }

    @Test
    void callTargetsForInInitializersAndCatchPatterns() {
        Utils.assertGatedBehavior(
                "ReferenceError,false,ok,function",
                "ReferenceError,true,SyntaxError,undefined",
                "var r = []; var called = false; function f() { called = true; }"
                        + " try { f() = 1; } catch (e) { r.push(e.name); }"
                        + " r.push(called);"
                        + " try { eval('\"use strict\"; for (var a = 0 in {});'); r.push('ok'); }"
                        + " catch (e) { r.push(e.name); }"
                        + " try { throw {}; } catch ({ g }) { { function g() {} } }"
                        + " r.push(typeof g);"
                        + " r.join()");
    }
}
