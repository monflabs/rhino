/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Residuals of the built-ins (plan 1.8c). */
class BuiltinsResidualsTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void stringMethodsIgnoreSymbolMethodsOfPrimitives() {
        assertBoth(
                "1|a-b-c|2",
                "Object.defineProperty(Number.prototype, Symbol.match, { get() { throw new Error('match'); } });"
                        + " Object.defineProperty(Number.prototype, Symbol.replace, { get() { throw new Error('replace'); } });"
                        + " Object.defineProperty(Number.prototype, Symbol.search, { get() { throw new Error('search'); } });"
                        + " ['a1b1c'.match(1).index, 'a1b1c'.replace(1, '-').replace(1, '-'),"
                        + " 'ab1'.search(1)].join('|')");
    }

    @Test
    void ownKeysOrder() {
        assertBoth(
                "1,4294967294,12345678900,b,a,4294967295,Symbol(z)|0,foo,Symbol()",
                "var o = { 12345678900: 1, b: 1, 1: 1, a: 1, [Symbol('z')]: 1, 4294967294: 1, 4294967295: 1 };"
                        + " var keys = Reflect.ownKeys(o).map(String).join();"
                        + " var log = []; var p = new Proxy({ [Symbol()]: 1, foo: 1, 0: 1 }, {"
                        + " getOwnPropertyDescriptor(t, k) { log.push(typeof k === 'symbol' ? 'Symbol()' : k);"
                        + " return Reflect.getOwnPropertyDescriptor(t, k); } });"
                        + " Object.getOwnPropertyDescriptors(p); keys + '|' + log.join()");
    }
}
