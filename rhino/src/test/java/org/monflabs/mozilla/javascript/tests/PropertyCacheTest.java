/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A named property access caches the property it found on the prototype chain of its receiver: a
 * change to any object of the chain must be seen by the next access.
 */
class PropertyCacheTest {

    private static final String CHAIN =
            "function A() { this.a = 1; }"
                    + "A.prototype.m = function () { return 'A' + this.a; };"
                    + "function B() { A.call(this); }"
                    + "B.prototype = Object.create(A.prototype);"
                    + "function C() { B.call(this); }"
                    + "C.prototype = Object.create(B.prototype);"
                    + "function call(o) { var r; for (var i = 0; i < 20; i++) r = o.m(); return r; }"
                    + "function get(o) { var r; for (var i = 0; i < 20; i++) r = o.m; return r; }"
                    + "var o = new C(), out = [call(o)];";

    private static void assertAllVersions(Object expected, String script) {
        Utils.assertGatedBehavior(expected, expected, CHAIN + script);
    }

    @Test
    void intermediatePrototypeGainsTheProperty() {
        assertAllVersions(
                "A1,B1,A1",
                "B.prototype.m = function () { return 'B' + this.a; };"
                        + "out.push(call(o));"
                        + "delete B.prototype.m;"
                        + "out.push(call(o));"
                        + "out.join()");
    }

    @Test
    void receiverGainsTheProperty() {
        assertAllVersions(
                "A1,own,A1",
                "o.m = function () { return 'own'; };"
                        + "out.push(call(o));"
                        + "delete o.m;"
                        + "out.push(call(o));"
                        + "out.join()");
    }

    @Test
    void holderLosesOrRedefinesTheProperty() {
        assertAllVersions(
                "A1,undefined,get2,A3",
                "delete A.prototype.m;"
                        + "out.push(typeof get(o) === 'function' ? 'f' : String(get(o)));"
                        + "Object.defineProperty(A.prototype, 'm', { configurable: true,"
                        + "  get: function () { var self = this; return function () { return 'get' + (self.a + 1); }; } });"
                        + "out.push(call(o));"
                        + "Object.defineProperty(A.prototype, 'm', { configurable: true, writable: true,"
                        + "  value: function () { return 'A' + (this.a + 2); } });"
                        + "out.push(call(o));"
                        + "out.join()");
    }

    @Test
    void prototypeChanges() {
        assertAllVersions(
                "A1,X1,Y1,A1",
                "var X = { m: function () { return 'X' + this.a; } };"
                        + "Object.setPrototypeOf(o, X);"
                        + "out.push(call(o));"
                        + "Object.setPrototypeOf(o, C.prototype);"
                        + "Object.setPrototypeOf(B.prototype, { m: function () { return 'Y' + this.a; } });"
                        + "out.push(call(o));"
                        + "Object.setPrototypeOf(B.prototype, A.prototype);"
                        + "out.push(call(o));"
                        + "out.join()");
    }

    @Test
    void intermediateMapGrows() {
        // enough properties to promote the slot map of B.prototype, then the shadowing one
        assertAllVersions(
                "A1,A1,B1",
                "for (var i = 0; i < 2000; i++) B.prototype['p' + i] = i;"
                        + "out.push(call(o));"
                        + "B.prototype.m = function () { return 'B' + this.a; };"
                        + "out.push(call(o));"
                        + "out.join()");
    }

    @Test
    void severalReceiversAtOneSite() {
        assertAllVersions(
                "A1,A1,own,3,1,undefined,A1",
                "var p = new Proxy(o, {});"
                        + "var receivers = [new B(), o, { m: function () { return 'own'; } },"
                        + "  [1, 2, 3], 'x', {}, p];"
                        + "function read(r) { return r.m ? r.m() : r.length; }"
                        + "var res;"
                        + "for (var k = 0; k < 10; k++) res = receivers.map(read);"
                        + "out.push.apply(out, res.slice(0, 5));"
                        + "out.push(String(receivers[5].m));"
                        + "out.push(read(p));"
                        + "out.slice(1).join()");
    }

    @Test
    void objectPrototypeMethods() {
        assertAllVersions(
                "[object Object],patched,[object Object]",
                "function str(x) { var r; for (var i = 0; i < 20; i++) r = x.toString(); return r; }"
                        + "var plain = {}, res = [str(plain)];"
                        + "var saved = Object.prototype.toString;"
                        + "Object.prototype.toString = function () { return 'patched'; };"
                        + "res.push(str(plain));"
                        + "Object.prototype.toString = saved;"
                        + "res.push(str(plain));"
                        + "res.join()");
    }

    @Test
    void getterOnThePrototypeSeesTheReceiver() {
        assertAllVersions(
                "1,5",
                "Object.defineProperty(A.prototype, 'twice', { get: function () { return this.a; } });"
                        + "function tw(x) { var r; for (var i = 0; i < 20; i++) r = x.twice; return r; }"
                        + "var q = new C(); q.a = 5;"
                        + "[tw(o), tw(q)].join()");
    }
}
