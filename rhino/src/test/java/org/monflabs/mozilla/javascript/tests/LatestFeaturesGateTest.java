/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The features stock Rhino did not have are only available under VERSION_ECMASCRIPT (plan G1):
 * under VERSION_ES6, their syntax is an error and their globals do not exist.
 */
class LatestFeaturesGateTest {

    private static void assertSyntax(String source) {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "ok",
                "try { eval(" + quote(source) + "); 'ok' } catch (e) { e.name }");
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Test
    void syntax() {
        assertSyntax("(class A { #x = 1; static {} })");
        assertSyntax("(async function () { await 1; })");
        assertSyntax("(async () => 1)");
        assertSyntax("({ async m() {} })");
        assertSyntax("(async function* () { for await (var x of []); })");
        assertSyntax("Math.max(...[1, 2])");
        assertSyntax("/a/d");
        assertSyntax("var f = () => import('x')");
        // await is a reserved word before VERSION_ECMASCRIPT
        assertSyntax("var await = 1");
    }

    @Test
    void globals() {
        Utils.assertGatedBehavior(
                "undefined,undefined,undefined,false",
                "function,object,symbol,true",
                "[typeof SharedArrayBuffer, typeof Atomics, typeof Symbol.asyncIterator,"
                        + " 'hasIndices' in RegExp.prototype].join()");
        Utils.assertGatedBehavior(
                "SyntaxError", "ok", "try { new RegExp('a', 'd'); 'ok' } catch (e) { e.name }");
    }

    @Test
    void modules() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    var scope = cx.initStandardObjects();
                    assertThrows(
                            EvaluatorException.class,
                            () -> cx.evaluateModule(scope, "export var x = 1;", "m"));
                    return null;
                });
    }
}
