/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Nested functions read and call the function declarations of an enclosing function at their
 * position in its activation: hoisting, reassignment, shadowing, Annex B block functions,
 * redeclarations and errors behave as with a lookup by name.
 */
class FunctionDeclarationSlotsTest {

    @Test
    void functionDeclarationsSeenByNestedFunctions() {
        String expected =
                "2 | 11:120:true:function | 1000:120:true:function | function | 7 | 200:6"
                        + " | TypeError notFn is not a function, it is undefined."
                        + " | TypeError notFn is not a function, it is number. | false | 300"
                        + " || none blk || 2";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "var r = [];"
                        + "function outer() {"
                        + "  var early = inner(1);"
                        + "  function inner(x) { return x + 1; }"
                        + "  function fact(n) { return n <= 1 ? 1 : n * fact(n - 1); }"
                        + "  function even(n) { return n == 0 ? true : odd(n - 1); }"
                        + "  function odd(n) { return n == 0 ? false : even(n - 1); }"
                        + "  var g = function () { return inner(10) + ':' + fact(5) + ':' + even(10) + ':' + typeof odd; };"
                        + "  var a = g();"
                        + "  inner = function (x) { return x * 100; };"
                        + "  var b = g();"
                        + "  var h = function self() { return typeof self; };"
                        + "  var shadow = function () { var inner = 7; return inner; };"
                        + "  var opt = function () { return inner?.(2) + ':' + (fact?.(3)); };"
                        + "  var c = (function () { try { notFn(); } catch (e) { return e.name + ' ' + e.message; } })();"
                        + "  var notFn = 5;"
                        + "  var c2 = (function () { try { notFn(); } catch (e) { return e.name + ' ' + e.message; } })();"
                        + "  var d = (function () { return delete inner; })();"
                        + "  return [early, a, b, h(), shadow(), opt(), c, c2, d, inner(3)].join(' | ');"
                        + "}"
                        + "r.push(outer());"
                        + "function blocks() {"
                        + "  var f = function () { return typeof blk === 'function' ? blk() : 'none'; };"
                        + "  var before = f();"
                        + "  { function blk() { return 'blk'; } }"
                        + "  return before + ' ' + f();"
                        + "}"
                        + "r.push(blocks());"
                        + "function redecl() {"
                        + "  var get = function () { return dup(); };"
                        + "  function dup() { return 1; }"
                        + "  function dup() { return 2; }"
                        + "  return get();"
                        + "}"
                        + "r.push(redecl());"
                        + "r.join(' || ')");
    }
}
