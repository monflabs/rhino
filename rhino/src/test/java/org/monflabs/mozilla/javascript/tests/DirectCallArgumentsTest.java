/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A call to a top-level function compiles to a direct call, with a regular call when the name no
 * longer holds that function: the arguments are evaluated once, in order, on both paths.
 */
class DirectCallArgumentsTest {

    private static final String SCRIPT =
            "var log = [];"
                    + "function side(v) { log.push(v); return v; }"
                    + "function f(a, b, c) { return a + ',' + b + ',' + c; }"
                    + "function t(n) { return f(side(n), n * 2, side('s' + n)); }"
                    + "function c(n) { var o = new F(side(n), n + 1); return o.a + o.b; }"
                    + "function F(a, b) { this.a = a; this.b = b; }"
                    + "var r = [t(1), c(1)];"
                    + "globalThis.f = function (a, b, c) { return a + '|' + b + '|' + c; };"
                    + "globalThis.F = function (a, b) { this.a = -a; this.b = -b; };"
                    + "r.push(t(2), c(2), log.join());"
                    + "r.join(' ')";

    @Test
    void directAndRegularCalls() {
        String expected = "1,2,s1 3 2|4|s2 -5 1,s1,1,2,s2,2";
        Utils.assertGatedBehavior(expected, expected, SCRIPT);
    }

    @Test
    void numberArgumentsThenObjectArgumentsInALoop() {
        String expected = "12,a0,a2,a4";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "function c(a, b) { return a + b; }"
                        + "function m(t, n) { return t + n; }"
                        + "function f(k) {"
                        + "  var r = [c(4 * k, 8 * k)];"
                        + "  for (let i = 0; i < 3; ++i) { r.push(m('a', 2 * i)); }"
                        + "  return r.join(); }"
                        + "f(1)");
    }
}
