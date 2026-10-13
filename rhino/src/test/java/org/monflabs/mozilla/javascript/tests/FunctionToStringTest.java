/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Function.prototype.toString source text (plan 1.2d), under VERSION_ECMASCRIPT; VERSION_ES6 keeps
 * the stock text.
 */
class FunctionToStringTest {

    @Test
    void methodsStartWithTheirKey() {
        Utils.assertGatedBehavior(
                "f() {}|f(a) {}|() {}|() {}|() {}",
                "get /* a */ f() {}|set f(a) {}|* g() {}|[ 'h' ]() {}|m() {}",
                "var o = { get /* a */ f() {}, set f(a) {}, * g() {}, [ 'h' ]() {}, m() {} };"
                        + " var d = Object.getOwnPropertyDescriptor(o, 'f');"
                        + " [d.get, d.set, o.g, o.h, o.m].map(String).join('|')");
    }

    @Test
    void functionConstructorSource() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "function anonymous(a,b //\n) {\nreturn a; //\n}",
                "try { String(Function('a', 'b //', 'return a; //')) } catch (e) { e.name }");
        Utils.assertGatedBehavior(
                "function * anonymous() {\n}",
                "function* anonymous(\n) {\n\n}",
                "String(Object.getPrototypeOf(function* () {}).constructor())");
    }

    @Test
    void callableProxiesAndBuiltins() {
        Utils.assertGatedBehavior(
                "TypeError",
                "function () {\n\t[native code]\n}\n",
                "try { Function.prototype.toString.call(new Proxy(function f() {}, {})) }"
                        + " catch (e) { e.name }");
        Utils.assertGatedBehavior(
                "function get $*() {\n\t[native code]\n}\n",
                "function get() {\n\t[native code]\n}\n",
                "String(Object.getOwnPropertyDescriptor(RegExp, '$*').get)");
        Utils.assertGatedBehavior(1, 0, "Object.getOwnPropertySymbols(Map.prototype).length - 2");
    }
}
