/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Declarations of eval code (plan 1.2e, ECMA-262 19.2.1.3). */
class EvalCodeTest {

    private static final String TRY =
            "var r = []; function t(f) { try { f(); r.push('ok'); } catch (e) { r.push(e.name); } }";

    @Test
    void varInAParameterListMayNotReuseAParameterOrArguments() {
        Utils.assertGatedBehavior(
                "ok,ok,ok,ok,ok,ok",
                "SyntaxError,SyntaxError,SyntaxError,ok,ok,ok",
                TRY
                        + " t(function () { function f(p = eval('var arguments')) {} f(); });"
                        + " t(function () { function f(a = eval('var a = 1')) {} f(); });"
                        + " t(function () { function f(a = eval('function arguments() {}')) {} f(); });"
                        + " t(function () { function f(a, b = eval('var c')) {} f(); });"
                        + " t(function () { function f(a = eval('let a')) {} f(); });"
                        + " t(function () { function f() { eval('var arguments'); } f(); });"
                        + " r.join()");
    }

    @Test
    void indirectEvalIsNeverStrict() {
        // before VERSION_ECMASCRIPT, an indirect eval took the strictness of the context
        Utils.assertGatedBehavior(
                "undefined,SyntaxError",
                "number,ok",
                "'use strict'; var r = [];"
                        + " (function () { (0, eval)('var ix = 1;'); })(); r.push(typeof ix);"
                        + " try { (0, eval)('with ({}) {} r.push(\"ok\");'); }"
                        + " catch (e) { r.push(e.name); }"
                        + " r.join()");
    }

    @Test
    void varMayNotBeHoistedOverALexicalDeclaration() {
        Utils.assertGatedBehavior(
                "ok,ok,ok,ok,ok",
                "SyntaxError,SyntaxError,ok,ok,ok",
                "let gx = 1; "
                        + TRY
                        + " t(function () { (0, eval)('var gx'); });"
                        + " t(function () { { let x; { eval('var x'); } } });"
                        + " t(function () { try { throw 1; } catch (x) { eval('var x'); } });"
                        + " t(function () { with ({x: 1}) { eval('var x'); } });"
                        + " t(function () { eval('var gx'); });"
                        + " r.join()");
    }

    @Test
    void globalFunctionsAreCheckedBeforeAnyIsCreated() {
        Utils.assertGatedBehavior(
                "TypeError,undefined,false",
                "TypeError,undefined,true",
                "var r = [];"
                        + " try { (0, eval)('function g1() {} function NaN() {}'); } catch (e) { r.push(e.name); }"
                        + " r.push(typeof g1);"
                        + " Object.defineProperty(this, 'f', {value: 1, writable: false, configurable: true});"
                        + " (0, eval)('function f() {}');"
                        + " r.push(Object.getOwnPropertyDescriptor(this, 'f').writable);"
                        + " r.join()");
    }
}
