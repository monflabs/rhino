/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A let/const loop head gets a binding per iteration only where a closure or a direct eval can
 * capture it: wherever one is created in the loop, each sees its own iteration's value.
 */
class PerIterationBindingsTest {

    private static final String LOOPS =
            "var r = [];"
                    + "var f1 = []; for (let i = 0; i < 3; i++) f1.push(() => i);"
                    + "r.push(f1.map(f => f()).join(''));"
                    + "var f2 = []; for (let i = 0; i < 3; i++, f2.push(() => i));"
                    + "r.push(f2.map(f => f()).join(''));"
                    + "var f3 = []; for (let i = 0; f3.push(() => i), i < 2; i++);"
                    + "r.push(f3.map(f => f()).join(''));"
                    + "var f4; for (let i = 0, g = () => i; i < 3; i++) f4 = g;"
                    + "r.push(f4());"
                    + "var f5 = []; for (let i = 0; i < 3; i++) {"
                    + "  class C { m() { return i; } } f5.push(new C()); }"
                    + "r.push(f5.map(c => c.m()).join(''));"
                    + "var f6 = []; for (let i = 0; i < 3; i++)"
                    + "  f6.push(eval('(function () { return i; })'));"
                    + "r.push(f6.map(f => f()).join(''));"
                    + "var f7 = []; for (let k in {a: 1, b: 2}) f7.push(() => k);"
                    + "r.push(f7.map(f => f()).join(''));"
                    + "var f8 = []; for (const v of [7, 8]) f8.push(() => v);"
                    + "r.push(f8.map(f => f()).join(''));"
                    + "var f9 = []; for (let {x = () => 1} of [{}, {}]) f9.push(x);"
                    + "r.push(f9[0] === f9[1]);"
                    + "var s = 0; for (let i = 0; i < 5; i++) s += i; r.push(s);"
                    + "var t = ''; for (const k in {p: 1, q: 2}) t += k; r.push(t);";

    private static final String EXPECTED = "012 123 012 0 012 012 ab 78 false 10 pq";

    @Test
    void topLevelLoops() {
        Utils.assertWithAllModes_ECMASCRIPT(EXPECTED, LOOPS + "r.join(' ')");
    }

    @Test
    void loopsInAFunctionWithAClosure() {
        Utils.assertWithAllModes_ECMASCRIPT(
                EXPECTED, "function f() { var cb = () => 0;" + LOOPS + "return r.join(' '); } f()");
    }
}
