/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * An assignment to a let/const binding in its temporal dead zone throws once its right-hand side is
 * evaluated (PutValue, SetMutableBinding); a compound assignment reads the binding first.
 */
class TemporalDeadZoneWritesTest {

    @Test
    void writesFromClosures() {
        Utils.assertGatedBehavior(
                "rhs,rhs+=,rhs-destr,rhs,rhs+=,2,rhs-const",
                "rhs,ReferenceError,ReferenceError,rhs-destr,ReferenceError,rhs,rhs+=,2,rhs-const,"
                        + "TypeError",
                "var log = [];"
                        + "function outer() {"
                        + "  function w() { c = (log.push('rhs'), 1); }"
                        + "  function u() { c += (log.push('rhs+='), 1); }"
                        + "  function v() { [c] = (log.push('rhs-destr'), [1]); }"
                        + "  try { w(); } catch (e) { log.push(e.name); }"
                        + "  try { u(); } catch (e) { log.push(e.name); }"
                        + "  try { v(); } catch (e) { log.push(e.name); }"
                        + "  let c = 0;"
                        + "  w(); u(); log.push(c);"
                        + "  const k = 1;"
                        + "  function wk() { k = (log.push('rhs-const'), 2); }"
                        + "  try { wk(); } catch (e) { log.push(e.name); }"
                        + "}"
                        + "outer(); log.join()");
    }

    @Test
    void writesInTheSameFunction() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "rhs,ReferenceError",
                "var log = [];"
                        + "(function () {"
                        + "  try { d = (log.push('rhs'), 1); } catch (e) { log.push(e.name); }"
                        + "  let d;"
                        + "})();"
                        + "log.join()");
    }

    private static void assertEvalWrite(String function) {
        Utils.assertGatedBehavior(
                "no error",
                "ReferenceError",
                "var r = 'no error'; try { " + function + "() } catch (e) { r = e.name } r");
    }

    @Test
    void writesFromEvalCode() {
        assertEvalWrite("(function () { eval('x = 1'); let x; })");
        assertEvalWrite("(function () { 'use strict'; eval('x = 1'); let x; })");
        assertEvalWrite("(function () { eval('[x] = [1]'); let x; })");
        assertEvalWrite("(function () { eval('for (x of [1]);'); let x; })");
        assertEvalWrite("(function () { eval('for (x in {a: 1});'); let x; })");
        assertEvalWrite("(function () { eval('(function () { x = 1 })()'); let x; })");
        assertEvalWrite("(function () { eval('eval(\"x = 1\")'); let x; })");
        assertEvalWrite("(function () { { eval('x = 1'); let x; } })");
        assertEvalWrite("(function () { (() => eval('x = 1'))(); let x; })");
        assertEvalWrite("(function () { let x = eval('x = 1'); })");
        assertEvalWrite("(function () { with ({}) { eval('x = 1'); } let x; })");
        assertEvalWrite("(function () { switch (1) { case 0: let x; case 1: eval('x = 1'); } })");
    }

    @Test
    void writesFromEvalCodeToConstAndClass() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError,ReferenceError,TypeError",
                "function name(f) { try { f() } catch (e) { return e.name } }"
                        + "[name(function () { eval('x = 1'); const x = 2; }),"
                        + " name(function () { eval('x = 1'); class x {} }),"
                        + " name(function () { const x = 2; eval('x = 1'); })].join()");
    }

    @Test
    void writesFromEvalCodeToGlobalLexicals() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError,ReferenceError,2",
                "var log = [];"
                        + "try { eval('g = 1'); } catch (e) { log.push(e.name); }"
                        + "try { (0, eval)('g = 1'); } catch (e) { log.push(e.name); }"
                        + "let g; eval('g = 2'); log.push(g); log.join()");
    }

    @Test
    void evalCodeEvaluatesTheValueBeforeTheCheck() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "rhs,ReferenceError",
                "var log = [];"
                        + "(function () {"
                        + "  try { eval('x = log.push(\"rhs\")'); } catch (e) { log.push(e.name); }"
                        + "  let x;"
                        + "})();"
                        + "log.join()");
    }

    @Test
    void otherWritesFromEvalCode() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2,3,4,5",
                "var log = [];"
                        + "eval('created = 1'); log.push(created);"
                        + "(function (a, {b}) { eval('a = 2; b = 3'); log.push(a, b); })(0, {});"
                        + "(function () { let x; eval('x = 4'); log.push(x); })();"
                        + "(function () { eval('let x; x = 5; log.push(x)'); })();"
                        + "log.join()");
    }

    @Test
    void evalCodeDoesNotReadThePropertyItAssigns() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2",
                "var log = [];"
                        + "var o = {get x() { throw 'get' }, set x(v) { log.push(v) }};"
                        + "(function () { with (o) { eval('x = 1'); } })();"
                        + "Object.defineProperty(globalThis, 'p',"
                        + "    {get() { throw 'get' }, set(v) { log.push(v) }});"
                        + "eval('p = 2'); log.join()");
    }
}
