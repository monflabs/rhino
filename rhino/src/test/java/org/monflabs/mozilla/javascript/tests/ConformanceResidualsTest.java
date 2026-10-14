/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The long tail of test262 (plan M13). */
class ConformanceResidualsTest {

    @Test
    void decodeUriNoncharacters() {
        Utils.assertGatedBehavior(
                "fffd,fffd",
                "fffe,ffff",
                "[decodeURI('%EF%BF%BE'), decodeURIComponent('%EF%BF%BF')]"
                        + ".map(s => s.charCodeAt(0).toString(16)).join()");
    }

    @Test
    void revokeIsNotAConstructor() {
        Utils.assertGatedBehavior(
                "true",
                "false",
                "String(Proxy.revocable({}, {}).revoke.hasOwnProperty('prototype'))");
    }

    @Test
    void log2OfPowersOfTwo() {
        Utils.assertGatedBehavior(
                "2.9999999999999996,-1074",
                "3,-1074",
                "[Math.log2(8), Math.log2(Number.MIN_VALUE)].join()");
    }

    @Test
    void finalSigmaPastSupplementaryCaseIgnorable() {
        Utils.assertGatedBehavior(
                "σ,ς,σ",
                "ς,ς,σ",
                "['a\\ud834\\ude42\\u03a3', 'a\\u03a3', '\\u03a3'].map(s => s.toLowerCase().slice(-1))"
                        + ".join()");
    }

    @Test
    void classEarlyErrors() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok,ok",
                "['class C { * constructor() {} }', 'class C { async * constructor() {} }',"
                        + " 'class C { static * prototype() {} }', 'class C { st\\\\u0061tic m() {} }',"
                        + " 'class C { st\\\\u0061tic() {} }', 'class C { * [\"constructor\"]() {} }']"
                        + ".map(src => { try { eval(src); return 'ok'; } catch (e) { return e.name; } })"
                        + ".join()");
    }

    @Test
    void noArity() {
        Utils.assertGatedBehavior(
                "2|length,name,arity,prototype",
                "undefined|length,name,prototype",
                "var f = function (a, b) {};\n"
                        + "f.arity + '|' + Object.getOwnPropertyNames(f).join()");
    }

    @Test
    void yieldInArrowParameters() {
        Utils.assertGatedBehavior(
                "ok",
                "SyntaxError",
                "try { eval('function *g() { (x = yield) => {}; }'); 'ok' } catch (e) { e.name }");
    }
}
