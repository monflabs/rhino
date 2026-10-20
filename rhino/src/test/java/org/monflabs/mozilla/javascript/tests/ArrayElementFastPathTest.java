/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Element reads and writes of a dense array go to its elements in compiled code; everything else
 * takes the general path, at the same call sites.
 */
class ArrayElementFastPathTest {

    private static final String ACCESS =
            "function get(a, i) { return a[i]; }"
                    + "function set(a, i, v) { a[i] = v; return a[i]; }"
                    + "function sum(a) { var s = 0; for (var i = 0; i < a.length; i++) s += a[i];"
                    + "  return s; }"
                    + "function fill(a, n) { for (var i = 0; i < n; i++) a[i] = i; return a.length; }";

    @Test
    void readsAndWrites() {
        String expected = "6 6 3 2 undefined 9 5 9,2,3,,,5 1,2,3 o undefined";
        Utils.assertGatedBehavior(
                expected,
                expected,
                ACCESS
                        + "var a = [1, 2, 3], o = {0: 'o'};"
                        + "var r = [sum(a), sum([1, 2, 3])];"
                        + "r.push(get(a, 2), get(a, 1.0), get(a, 3), set(a, 0, 9));"
                        + "var b = [1, 2, 3]; r.push(fill(b, 5), String(set(a, 5, 5) && a));"
                        + "r.push(String(b.slice(1, 4)), get(o, 0), get(a, -0.5));"
                        + "r.map(String).join(' ')");
    }

    @Test
    void holesReadThePrototype() {
        String expected = "p,2,x undefined set:7 true";
        Utils.assertGatedBehavior(
                expected,
                expected,
                ACCESS
                        + "var log = '';"
                        + "Object.defineProperty(Array.prototype, 0, {"
                        + "  get() { return 'p'; }, set(v) { log += 'set:' + v; },"
                        + "  configurable: true });"
                        + "Array.prototype[2] = 'x';"
                        + "var a = [, 2, , 4];"
                        + "var r = [get(a, 0), get(a, 1), get(a, 2)].join();"
                        + "var c = []; c.length = 3; set(c, 0, 7);"
                        + "delete Array.prototype[0]; delete Array.prototype[2];"
                        + "[r, get(c, 1), log, !c.hasOwnProperty(0)].map(String).join(' ')");
    }

    @Test
    void accessorsFrozenAndNonExtensibleArrays() {
        String expected = "acc:1 w:5 1 TypeError 3 undefined";
        Utils.assertGatedBehavior(
                expected,
                expected,
                ACCESS
                        + "var log = [];"
                        + "var a = [0, 1, 2];"
                        + "Object.defineProperty(a, 1, {"
                        + "  get() { return 'acc:1'; }, set(v) { log.push('w:' + v); } });"
                        + "var r = [get(a, 1)]; set(a, 1, 5); r.push(log.join());"
                        + "var f = Object.freeze([1, 2]); set(f, 0, 9); r.push(get(f, 0));"
                        + "try { (function () { 'use strict'; f[0] = 9; })(); r.push('no error'); }"
                        + "catch (e) { r.push(e.name); }"
                        + "var n = Object.preventExtensions([1, 2, 3]); set(n, 3, 4);"
                        + "r.push(n.length, get(n, 3));"
                        + "r.map(String).join(' ')");
    }
}
