/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** yield as an AssignmentExpression and as an identifier (plan 1.6a); the same in both versions. */
class GeneratorSyntaxTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void yieldOperandIsAnAssignmentExpression() {
        assertBoth(
                "9,7|1,2|2|1,2",
                "function* a() { var x = [yield 1, 7]; return x.join(); }"
                        + " var it = a(); it.next(); var r = [it.next(9).value];"
                        + " function* b() { yield 1, yield 2; } r.push([...b()].join());"
                        + " function* c() { yield { ...yield, y: 1 }; }"
                        + " it = c(); it.next(); r.push(it.next({x: 2}).value.x);"
                        + " var m = { *g() { yield [...yield]; } }.g;"
                        + " it = m(); it.next(); r.push(it.next([1, 2]).value.join());"
                        + " r.join('|')");
    }

    @Test
    void yieldIsAnIdentifierOutsideGenerators() {
        assertBoth(
                "4|2|43|function",
                "var yield = 3; var r = [yield + 1];"
                        + " function f() { var yield = 2; return yield; } r.push(f());"
                        + " function* g() { return (function (a) { var yield = a + 1; return yield; })(yield); }"
                        + " var it = g(); it.next(); r.push(it.next(42).value);"
                        + " r.push((function () { function* yield() {} return typeof yield; })());"
                        + " r.join('|')");
    }

    @Test
    void yieldIsReservedInGeneratorsAndStrictCode() {
        assertBoth(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                "var r = []; function t(s) { try { eval(s); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t('function* g(yield) {}'); t('function* g() { var yield; }');"
                        + " t(\"'use strict'; var yield;\"); t('function* g() { yield\\n* 1 }');"
                        + " r.join()");
    }

    @Test
    void generatorPrototypeHasNoOwnProperties() {
        assertBoth(0, "function* g() {} Object.getOwnPropertyNames(g.prototype).length");
    }
}
