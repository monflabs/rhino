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
        // before VERSION_ECMASCRIPT, the operand of yield is an Expression
        Utils.assertGatedBehavior(
                "ok,SyntaxError",
                "ok,ok",
                "var r = []; function t(s) { try { eval(s); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t('function* a() { var x = [yield 1, 7]; }');"
                        + " t('function* b() { yield 1, yield 2; }'); r.join()");
        Utils.assertWithAllModes_ECMASCRIPT(
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
        Utils.assertGatedBehavior(
                "SyntaxError", "ok", "try { eval('var yield = 3'); 'ok' } catch (e) { e.name }");
        Utils.assertWithAllModes_ECMASCRIPT(
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
        Utils.assertGatedBehavior(
                "SyntaxError,SyntaxError,SyntaxError,ok",
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                "var r = []; function t(s) { try { eval(s); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t('function* g(yield) {}'); t('function* g() { var yield; }');"
                        + " t(\"'use strict'; var yield;\"); t('function* g() { yield\\n* 1 }');"
                        + " r.join()");
    }

    @Test
    void parametersAreBoundWhenTheGeneratorIsCalled() {
        Utils.assertGatedBehavior(
                "created|5|1,2,NaN|7",
                "TypeError|5|1,2,3|7",
                "var r = []; function* g([a]) {}"
                        + " try { g(null); r.push('created'); } catch (e) { r.push(e.name); }"
                        + " function* h({a}) { return a; } r.push(h({a: 5}).next().value);"
                        + " function* k([a, b] = [1, 2], c = a + b) { return [a, b, c].join(); }"
                        + " r.push(k().next().value);"
                        + " var o = { *m([a]) { return a; } }; r.push(o.m([7]).next().value);"
                        + " r.join('|')");
    }

    @Test
    void generatorDetails() {
        // yield* passes undefined to next, %GeneratorPrototype% has a constructor
        Utils.assertGatedBehavior(
                "0|false",
                "1|true",
                "var r = []; function* h() { return 1; } var n;"
                        + " var it = { [Symbol.iterator]() { return { next() { n = arguments.length;"
                        + "   return { done: true }; } }; } }; function* d() { yield* it; } d().next();"
                        + " r.push(n);"
                        + " r.push(Object.getPrototypeOf(h).prototype.constructor"
                        + "   === Object.getPrototypeOf(h));"
                        + " r.join('|')");
        // a function expression in a generator may be named yield; yield* and its operand
        Utils.assertGatedBehavior(
                "SyntaxError,ok",
                "ok,ok",
                "var r = []; function t(s) { try { eval(s); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t('function* h() { return (function yield() {}); }');"
                        + " t('function* g() { yield *\\n 1; }'); r.join()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "5|1|1|true|TypeError|true",
                "var r = [];"
                        + " var g = function* () { yield *\n g2(); }; var g2 = function* () { yield 5; };"
                        + " r.push(g().next().value);"
                        + " function* h() { return (function yield() { return 1; })(); } r.push(h().next().value);"
                        + " var n; var it = { [Symbol.iterator]() { return { next() { n = arguments.length;"
                        + "   return { done: true }; } }; } }; function* d() { yield* it; } d().next(); r.push(n);"
                        + " r.push(Object.getPrototypeOf(h).prototype.constructor === Object.getPrototypeOf(h));"
                        + " var iter; function* w() { iter.next(); } iter = w();"
                        + " try { iter.next(); } catch (e) { r.push(e.name); } r.push(iter.next().done);"
                        + " r.join('|')");
    }

    @Test
    void generatorPrototypeHasNoOwnProperties() {
        Utils.assertGatedBehavior(
                1, 0, "function* g() {} Object.getOwnPropertyNames(g.prototype).length");
    }
}
