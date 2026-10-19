/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** An assignment to a property finds the property on the object or its prototypes first. */
class PropertyAssignmentTest {

    @Test
    void assignments() {
        String expected =
                "2 3 set 4 false 1 false 1 2  7  own set 9 1 TypeError TypeError TypeError 1 5 5";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "var r = [];"
                        + "var o = {a: 1}; o.a = 2; o.b = 3; r.push(o.a, o.b);"
                        + "var p = Object.create({set s(v) { r.push('set ' + v); }});"
                        + "p.s = 4; r.push(p.hasOwnProperty('s'));"
                        + "var q = Object.create(Object.freeze({ro: 1}));"
                        + "q.ro = 5; r.push(q.ro, q.hasOwnProperty('ro'));"
                        + "var f = Object.freeze({x: 1}); f.x = 2; r.push(f.x);"
                        + "var se = Object.seal({y: 1}); se.y = 2; se.z = 3; r.push(se.y, se.z);"
                        + "var ne = Object.preventExtensions({w: 1});"
                        + "ne.w = 7; ne.v = 8; r.push(ne.w, ne.v);"
                        + "var acc = {get g() { return 1; }, set g(v) { r.push('own set ' + v); }};"
                        + "acc.g = 9;"
                        + "var ro = {}; Object.defineProperty(ro, 'k', {value: 1, writable: false});"
                        + "ro.k = 2; r.push(ro.k);"
                        + "try { (function () { 'use strict'; ro.k = 3; })(); }"
                        + "  catch (e) { r.push(e.name); }"
                        + "try { (function () { 'use strict'; f.x = 3; })(); }"
                        + "  catch (e) { r.push(e.name); }"
                        + "try { (function () { 'use strict'; ne.u = 3; })(); }"
                        + "  catch (e) { r.push(e.name); }"
                        + "var arr = [1, 2]; arr.length = 1; arr.x = 5; r.push(arr.length, arr.x);"
                        + "function C() { this.a = 1; this.b = 2; }"
                        + "var c = new C(); c.a = 3; r.push(c.a + c.b);"
                        + "r.join(' ')");
    }

    @Test
    void writesInALoopSeeChanges() {
        Utils.assertGatedBehavior(
                "4,setter,5,ro",
                "4,setter,5,ro",
                "var r = [], o = {v: 0};"
                        + "for (var i = 0; i < 5; i++) o.v = i;"
                        + "r.push(o.v);"
                        + "var proto = {};"
                        + "for (var j = 0; j < 3; j++) {"
                        + "  if (j == 2) Object.defineProperty(proto, 'w', {"
                        + "    set: function () { r.push('setter'); }, configurable: true });"
                        + "  var x = Object.create(proto); x.w = j;"
                        + "}"
                        + "var y = {k: 1};"
                        + "for (var m = 0; m < 3; m++) {"
                        + "  if (m == 2) Object.defineProperty(y, 'k', {writable: false});"
                        + "  y.k = 5;"
                        + "}"
                        + "r.push(y.k, Object.getOwnPropertyDescriptor(y, 'k').writable ? 'rw' : 'ro');"
                        + "r.join()");
    }
}
