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

    @Test
    void arrayMethodsThrowWhenAWriteFails() {
        assertBoth(
                "TypeError|TypeError|TypeError|TypeError|TypeError",
                "var r = []; function t(f) { try { f(); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t(() => Object.freeze([1]).push(2));"
                        + " t(() => { var a = [1]; Object.defineProperty(a, 'length', { writable: false }); a.pop(); });"
                        + " t(() => Array.prototype.push.call(Object.freeze({ length: 0 }), 1));"
                        + " t(() => Array.prototype.shift.call(Object.defineProperty({ length: 2, 0: 1 }, '1',"
                        + " { value: 2, configurable: false })));"
                        + " t(() => Array.prototype.pop.call(''));"
                        + " r.join('|')");
    }

    @Test
    void objectPrototypeMethods() {
        assertBoth(
                "TypeError|function|true|false|[object Object]|[object Array]",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => Object.prototype.__defineGetter__.call(Object.freeze({}), 'x', function () {}));"
                        + " t(() => { var o = {}; o.__defineGetter__('x', function () {});"
                        + " return typeof Object.create(o).__lookupGetter__('x'); });"
                        + " t(() => ({ [Symbol.iterator]: 1 }).hasOwnProperty({ toString() { return Symbol.iterator; } }));"
                        + " t(() => Object.prototype.isPrototypeOf.call(null, 1));"
                        + " t(() => { var p = new Promise(() => {}); delete Promise.prototype[Symbol.toStringTag];"
                        + " return Object.prototype.toString.call(p); });"
                        + " t(() => Object.prototype.toString.call(new Proxy([], {})));"
                        + " r.join('|')");
    }

    @Test
    void dateSettersAndIndexOf() {
        assertBoth(
                "NaN,0|-1|get a() {}",
                "var d = new Date(NaN); var v = d.setDate({ valueOf() { d.setTime(0); return 1; } });"
                        + " var r = [[v, d.getTime()].join()];"
                        + " r.push([].indexOf(1, { valueOf() { throw new Error('converted'); } }));"
                        + " r.push(String(Object.getOwnPropertyDescriptor(class { get a() {} }.prototype, 'a').get));"
                        + " r.join('|')");
    }
}
