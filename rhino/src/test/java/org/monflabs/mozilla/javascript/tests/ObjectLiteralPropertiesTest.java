/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The properties of an object literal are own data properties (CreateDataPropertyOrThrow), whether
 * the literal defines accessors or not.
 */
class ObjectLiteralPropertiesTest {

    private static final String DATA =
            "{\"value\":%s,\"writable\":true,\"enumerable\":true,\"configurable\":true}";

    @Test
    void duplicateKeys() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "{\"a\":3,\"b\":2} ab {\"0\":\"z\",\"1\":\"y\"} 2",
                "var o = {a: 1, b: 2, a: 3}, p = {0: 'x', 1: 'y', 0: 'z'}, s = Symbol();"
                        + "var q = {[s]: 1, [s]: 2};"
                        + "[JSON.stringify(o), Object.keys(o).join(''), JSON.stringify(p), q[s]]"
                        + ".join(' ')");
    }

    @Test
    void dataPropertyReplacesAnAccessorOfTheLiteral() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2 " + String.format(DATA, 2) + " " + String.format(DATA, 4) + " 1",
                "var o = {get a() { return 1; }, a: 2}, p = {set q(v) {}, q: 4};"
                        + "var r = {a: 2, get a() { return 1; }};"
                        + "[o.a, JSON.stringify(Object.getOwnPropertyDescriptor(o, 'a')),"
                        + " JSON.stringify(Object.getOwnPropertyDescriptor(p, 'q')), r.a].join(' ')");
    }

    @Test
    void inheritedSetterIsNotCalled() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1 false",
                "var called = false;"
                        + "Object.defineProperty(Object.prototype, 'zz', {"
                        + "  set: function (v) { called = true; }, configurable: true });"
                        + "var o = {zz: 1};"
                        + "delete Object.prototype.zz;"
                        + "[Object.getOwnPropertyDescriptor(o, 'zz').value, called].join(' ')");
    }

    @Test
    void protoKeys() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "__proto__ true true 1",
                "var o = {['__proto__']: 5}, p = {__proto__: null, x: 1};"
                        + "[Object.getOwnPropertyNames(o).join(),"
                        + " Object.getPrototypeOf(o) === Object.prototype,"
                        + " Object.getPrototypeOf(p) === null, p.x].join(' ')");
    }
}
