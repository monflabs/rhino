/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
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

    @Test
    void objectHasOwnAssignAndToJSON() {
        assertBoth(
                "TypeError|true|2|TypeError|x",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => Object.hasOwn(null, { toString() { throw new Error('key'); } }));"
                        + " t(() => Object.hasOwn(new Proxy({}, { getOwnPropertyDescriptor() {"
                        + " return { value: 1, configurable: true }; } }), 'a'));"
                        + " t(() => Object.hasOwn.length);"
                        + " t(() => Object.assign(Object.freeze({ a: 1 }), { a: 2 }));"
                        + " t(() => Date.prototype.toJSON.call({ toISOString() { return 'x'; } }));"
                        + " r.join('|')");
    }

    @Test
    void jsonWithProxiesAndRevivers() {
        assertBoth(
                "[1,2]|{\"a\":1}|{\"b\":2}|string,string|0,1,|7",
                "var r = [];"
                        + " r.push(JSON.stringify(new Proxy([1, 2], {})));"
                        + " r.push(JSON.stringify({ a: 1, b: 2 }, new Proxy(['a'], {})));"
                        + " r.push(JSON.stringify(new Proxy({ a: 1, b: 2 }, { ownKeys() { return ['b']; } })));"
                        + " var types = []; JSON.stringify([1], (k, v) => { types.push(typeof k); return v; });"
                        + " r.push(types.join());"
                        + " var keys = []; JSON.parse('[1, 2]', function (k, v) { keys.push(k); return v; });"
                        + " r.push(keys.join());"
                        + " var o = JSON.parse('{\"a\": 1}', function (k, v) {"
                        + " if (k === '') { return v; } Object.defineProperty(this, 'a', { value: 0, writable: false });"
                        + " return 7; });"
                        + " r.push(o.a); r.join('|')");
    }

    @Test
    void arrayMethodsFollowSpecSteps() {
        assertBoth(
                "3|true|2,3|1|[object Object]|x|TypeError|TypeError|1,2,3",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => { var n; var a = [1, 2, 3, 4, 5]; a.constructor = {};"
                        + " a.constructor[Symbol.species] = function (len) { n = len; return []; };"
                        + " a.slice(1, -1); return n; });"
                        + " t(() => { var C = function () {}; var a = []; a.constructor = function () {};"
                        + " a.constructor[Symbol.species] = C;"
                        + " return Object.getPrototypeOf([].map.call(new Proxy(a, {}), x => x)) === C.prototype; });"
                        + " t(() => [].flat.call(new Proxy([[2, 3]], {})));"
                        + " t(() => { var o = { length: 1, 0: 1 }; Array.prototype.splice.call(o); return o.length; });"
                        + " t(() => Array.prototype.toString.call({ join: null }));"
                        + " t(() => Array.prototype.toString.call({ join() { return 'x'; } }));"
                        + " t(() => Array.prototype.push.call({ length: 2 ** 53 - 1 }, 1));"
                        + " t(() => { 'use strict'; var a = [1, 2, 3];"
                        + " a.length = { valueOf() { Object.defineProperty(a, 'length', { writable: false });"
                        + " return 0; } }; });"
                        + " t(() => { var a = [1, 2, 3]; a.copyWithin(0, { valueOf() { a.length = 3; return 0; } });"
                        + " return a; });"
                        + " r.join('|')");
    }

    @Test
    void stringAndConversionResiduals() {
        assertBoth(
                "2|x|TypeError|TypeError|TypeError",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => '__null__'.indexOf({ valueOf() { return null; }, toString: null }));"
                        + " t(() => { function g() {} g.valueOf = function () { return 'xy'; };"
                        + " return String.prototype.substring.call(g, 0, 1) === 'f' ? 'x' : 'bad'; });"
                        + " t(() => 'a'.startsWith({ [Symbol.match]: true }));"
                        + " t(() => 'a'.replaceAll({ [Symbol.match]: false, [Symbol.replace]: {},"
                        + " toString() { throw new Error('converted'); } }, ''));"
                        + " t(() => ''.includes({ get [Symbol.match]() { throw new TypeError(); } }));"
                        + " r.join('|')");
    }

    @Test
    void promiseCombinatorsFollowSpecSteps() {
        String script =
                "var r = [];"
                        + " Promise.allSettled([Promise.reject(1)]).then(v => r.push(v[0].status));"
                        + " class C extends Promise { static get resolve() { throw 'resolve'; } }"
                        + " Promise.all.call(C, { [Symbol.iterator]() { throw 'iterator'; } }).catch(e => r.push(e));"
                        + " class B { constructor(ex) { ex(() => {}, e => r.push(e)); }"
                        + " static resolve() { throw 'bad'; } }"
                        + " Promise.race.call(B, { [Symbol.iterator]() { return this; },"
                        + " next() { return { done: false }; }, return: 1 });"
                        + " Promise.resolve({ get then() { throw 'then'; } }).catch(e => r.push(e));";
        for (int version : new int[] {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT}) {
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(version);
                        var scope = cx.initStandardObjects();
                        cx.evaluateString(scope, script, "test", 1, null);
                        cx.processMicrotasks();
                        assertEquals(
                                "bad|resolve|then|rejected",
                                cx.evaluateString(scope, "r.join('|')", "test", 1, null));
                        return null;
                    });
        }
    }

    @Test
    void functionResiduals() {
        assertBoth(
                "0,1,2|7,0,1|Infinity|0|2147483648|TypeError",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " function rest(...a) { return a; }"
                        + " t(() => rest.apply(null, [0, 1, 2]));"
                        + " t(() => rest.bind(null, 7)(0, 1));"
                        + " function len(l) { var f = function () {};"
                        + " Object.defineProperty(f, 'length', { value: l }); return f.bind().length; }"
                        + " t(() => len(Infinity)); t(() => len(NaN)); t(() => len(2 ** 31));"
                        + " t(() => { var h = Proxy.revocable(function () {}, { get() { h.revoke(); } });"
                        + " return new h.proxy(); });"
                        + " r.join('|')");
    }
}
