/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** A call does not compute the this of a function that cannot read it. */
class IgnoredThisTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void functionsReadingThis() {
        assertAllVersions(
                "true object true true true true 2 object",
                "var sloppy = function () { return this === globalThis; };"
                        + "var prim = function () { return typeof this; };"
                        + "var arrow = function () { return (() => (() => this)())() === globalThis; };"
                        + "var viaEval = function () { return eval('this') === globalThis; };"
                        + "var o = { m: function () { return this === o; } };"
                        + "var sup = { m() { return super.toString === Object.prototype.toString; } };"
                        + "var none = function (x) { return x + 1; };"
                        + "var gen = function* () { yield typeof this; };"
                        + "[sloppy(), prim.call('s'), arrow(), viaEval(), o.m(),"
                        + "  sup.m(), [1].map(none)[0], gen.call(1).next().value].join(' ')");
    }

    @Test
    void functionsIgnoringThis() {
        assertAllVersions(
                "3 2,3,4 5 x 1",
                "var add = function (a, b) { return a + b; };"
                        + "var inc = function (x) { return x + 1; };"
                        + "var inner = function () { function f() { return 5; } return f(); };"
                        + "var str = function () { return 'x'; };"
                        + "var nested = function () { return (function () { return 1; }).call(undefined); };"
                        + "[add(1, 2), [1, 2, 3].map(inc), inner.call(null), str.call('s'),"
                        + "  nested.apply(7)].join(' ')");
    }
}
