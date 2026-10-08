/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Function.prototype.toString source text (plan 1.2d); the same in both versions. */
class FunctionToStringTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void methodsStartWithTheirKey() {
        assertBoth(
                "get /* a */ f() {}|set f(a) {}|* g() {}|[ 'h' ]() {}|m() {}",
                "var o = { get /* a */ f() {}, set f(a) {}, * g() {}, [ 'h' ]() {}, m() {} };"
                        + " var d = Object.getOwnPropertyDescriptor(o, 'f');"
                        + " [d.get, d.set, o.g, o.h, o.m].map(String).join('|')");
    }

    @Test
    void functionConstructorSource() {
        assertBoth(
                "function anonymous(a,b //\n) {\nreturn a; //\n}",
                "String(Function('a', 'b //', 'return a; //'))");
        assertBoth(
                "function* anonymous(\n) {\n\n}",
                "String(Object.getPrototypeOf(function* () {}).constructor())");
    }

    @Test
    void callableProxiesAndBuiltins() {
        assertBoth(
                "function () {\n\t[native code]\n}\n",
                "Function.prototype.toString.call(new Proxy(function f() {}, {}))");
        assertBoth(
                "function get() {\n\t[native code]\n}\n",
                "String(Object.getOwnPropertyDescriptor(RegExp, '$*').get)");
        assertBoth(0, "Object.getOwnPropertySymbols(Map.prototype).length - 2");
    }
}
