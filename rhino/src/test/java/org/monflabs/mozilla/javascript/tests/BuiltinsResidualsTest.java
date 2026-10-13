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

    @Test
    void stringMethodsIgnoreSymbolMethodsOfPrimitives() {
        Utils.assertGatedBehavior(
                "match|replace|search",
                "1|a-b-c|2",
                "Object.defineProperty(Number.prototype, Symbol.match, { get() { throw new Error('match'); } });"
                        + " Object.defineProperty(Number.prototype, Symbol.replace, { get() { throw new Error('replace'); } });"
                        + " Object.defineProperty(Number.prototype, Symbol.search, { get() { throw new Error('search'); } });"
                        + " function t(f) { try { return f(); } catch (e) { return e.message; } }"
                        + " [t(() => 'a1b1c'.match(1).index),"
                        + " t(() => 'a1b1c'.replace(1, '-').replace(1, '-')),"
                        + " t(() => 'ab1'.search(1))].join('|')");
    }

    @Test
    void ownKeysOrder() {
        Utils.assertGatedBehavior(
                "1,12345678900,b,a,4294967294,4294967295,Symbol(z)|0,Symbol(),foo",
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
        Utils.assertGatedBehavior(
                "ok|ok|ok|ok|ok",
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
        Utils.assertGatedBehavior(
                "undefined|function|TypeError|TypeError|[object Promise]|[object Array]",
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
        Utils.assertWithAllModes_ECMASCRIPT(
                "NaN,0|-1|get a() {}",
                "var d = new Date(NaN); var v = d.setDate({ valueOf() { d.setTime(0); return 1; } });"
                        + " var r = [[v, d.getTime()].join()];"
                        + " r.push([].indexOf(1, { valueOf() { throw new Error('converted'); } }));"
                        + " r.push(String(Object.getOwnPropertyDescriptor(class { get a() {} }.prototype, 'a').get));"
                        + " r.join('|')");
    }

    @Test
    void objectHasOwnAssignAndToJSON() {
        Utils.assertGatedBehavior(
                "TypeError|false|1|TypeError|x",
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
        Utils.assertGatedBehavior(
                "{\"0\":1,\"1\":2}|{\"a\":1,\"b\":2}|{\"b\":2}|string,number|0,1,|0",
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
        Utils.assertGatedBehavior(
                "0|false|2,3|1|||9007199254740992|undefined|1,2,3",
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
        Utils.assertWithAllModes_ECMASCRIPT(
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
        for (int version : new int[] {Context.VERSION_ECMASCRIPT}) {
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
        // under VERSION_ES6, the interpreter keeps the rest-parameter bug of stock Rhino
        Utils.assertWithAllModes_ECMASCRIPT(
                "0,1,2|7,0,1",
                "function rest(...a) { return a; }"
                        + " [rest.apply(null, [0, 1, 2]), rest.bind(null, 7)(0, 1)].join('|')");
        Utils.assertGatedBehavior(
                "0|0|0|[object Object]",
                "Infinity|0|2147483648|TypeError",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " function len(l) { var f = function () {};"
                        + " Object.defineProperty(f, 'length', { value: l }); return f.bind().length; }"
                        + " t(() => len(Infinity)); t(() => len(NaN)); t(() => len(2 ** 31));"
                        + " t(() => { var h = Proxy.revocable(function () {}, { get() { h.revoke(); } });"
                        + " return new h.proxy(); });"
                        + " r.join('|')");
    }

    @Test
    void objectResiduals() {
        Utils.assertGatedBehavior(
                ",0|undefined|set,enumerable|TypeError|a|null|ownKeys,get:a|undefined",
                "TypeError,2|TypeError|get,set|TypeError|TypeError|TypeError|ownKeys,gopd:a,get:a|9",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => { var a = [0, 1, 2]; a.length = 10; a[5] = 5;"
                        + " Object.defineProperty(a, '1', { configurable: false }); var e;"
                        + " try { Object.defineProperty(a, 'length', { value: 0 }); } catch (x) { e = x.name; }"
                        + " return [e, a.length]; });"
                        + " t(() => { var a = [1]; Object.defineProperty(a, 'length', { writable: false });"
                        + " Object.defineProperty(a, 1, { value: 2 }); });"
                        + " t(() => { var o = {}; Object.defineProperty(o, 'x', { set: undefined });"
                        + " return Object.keys(Object.getOwnPropertyDescriptor(o, 'x')).slice(0, 2); });"
                        + " t(() => Object.setPrototypeOf(Object.prototype, {}));"
                        + " t(() => Object.assign('a', [1]));"
                        + " t(() => Object.getOwnPropertyDescriptor(Object.prototype, '__proto__').get.call(undefined));"
                        + " t(() => { var log = []; Object.values(new Proxy({ a: 1 }, {"
                        + " ownKeys(t) { log.push('ownKeys'); return Reflect.ownKeys(t); },"
                        + " getOwnPropertyDescriptor(t, k) { log.push('gopd:' + k);"
                        + " return Reflect.getOwnPropertyDescriptor(t, k); },"
                        + " get(t, k) { log.push('get:' + k); return t[k]; } })); return log; });"
                        + " t(() => { var tg = [7]; Object.assign(tg, new Proxy({ length: 0, 1: 9 },"
                        + " { ownKeys() { return ['length', '1']; } })); return tg[1]; });"
                        + " r.join('|')");
    }

    @Test
    void setDateBigIntErrorResiduals() {
        Utils.assertGatedBehavior(
                "TypeError||-1|-62167219200000|true|1|undefined|[object Error]",
                "size,ToNumber,has,keys|1,3|0|NaN|false|TypeError|c|[object Object]",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => { var log = []; new Set([1]).union({"
                        + " get size() { log.push('size'); return { valueOf() { log.push('ToNumber'); return 1; } }; },"
                        + " get has() { log.push('has'); return () => true; },"
                        + " get keys() { log.push('keys'); return () => ({ next() { return { done: true }; } }); } });"
                        + " return log; });"
                        + " t(() => [...new Set([1, 2]).symmetricDifference({ size: 2, has() { throw 0; },"
                        + " keys() { var a = [2, 3]; return { next() { return a.length"
                        + " ? { value: a.shift(), done: false } : { done: true }; } }; } })]);"
                        + " t(() => Object.is(new Date(-0.5).valueOf(), 0) ? 0 : -1);"
                        + " t(() => Date.parse('-000000-01-01T00:00:00Z'));"
                        + " t(() => Object.getOwnPropertyDescriptor(Date.prototype, Symbol.toPrimitive).writable);"
                        + " t(() => BigInt.asIntN(8, 1));"
                        + " t(() => new Error('m', new Proxy({ cause: 'c' }, {})).cause);"
                        + " t(() => Object.prototype.toString.call(TypeError.prototype));"
                        + " r.join('|')");
    }

    @Test
    void typedArrayResiduals() {
        Utils.assertGatedBehavior(
                "RangeError|Error|1|false|false|get Symbol(Symbol.toStringTag)||1,9|0",
                "RangeError|TypeError|TypeError|true|true|get [Symbol.toStringTag]|3,4|1,9|NaN",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => new Int8Array(-1));"
                        + " t(() => new Int8Array(Symbol()));"
                        + " t(() => new BigInt64Array([1]));"
                        + " var TA = Object.getPrototypeOf(Int8Array);"
                        + " t(() => TA.prototype.toString === Array.prototype.toString);"
                        + " t(() => TA.prototype[Symbol.iterator] === TA.prototype.values);"
                        + " t(() => Object.getOwnPropertyDescriptor(TA.prototype, Symbol.toStringTag).get.name);"
                        + " t(() => new Int8Array([1, 2, 3, 4]).subarray(-Infinity).subarray(2, Infinity));"
                        + " t(() => new Int8Array([1, 2]).with(-1, 9));"
                        + " t(() => Float64Array.from([, 1])[0]);"
                        + " r.join('|')");
    }

    @Test
    void typedArrayJsTestsExpectations() {
        Utils.assertGatedBehavior(
                "RangeError: Index -4 out of range|RangeError: Index -2,147,483,656 out of range"
                        + "|0,2,3|1,0,3|TypeError",
                "RangeError: Index -2 out of range|RangeError: Index -268,435,457 out of range"
                        + "|NaN,2,3|1,0,3|ok",
                "var r = []; function t(f) { try { r.push(String(f())); }"
                        + " catch (e) { r.push(e instanceof TypeError ? e.name : String(e)); } }"
                        + " t(() => new Int16Array(-2)); t(() => new Float64Array(-268435457));"
                        + " t(() => new Float32Array([1, 2, 3]).with(0));"
                        + " t(() => new Int8Array([1, 2, 3]).with(1));"
                        + " t(() => { var a = new Int8Array(2); a.set(0); a.set(0, 1); return 'ok'; });"
                        + " r.join('|')");
    }

    @Test
    void typedArrayByteOrder() {
        Utils.assertGatedBehavior(0, 1, "new Uint8Array(new Uint16Array([1]).buffer)[0]");
    }
}
