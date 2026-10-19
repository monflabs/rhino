/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Destructuring, for-of and spread read an array by index while its iteration methods are the
 * original ones; any change to them after a first use is seen.
 */
class ArrayIterationCacheTest {

    @Test
    void replacedIteratorMethod() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2 x,x 1,2",
                "var r = [];"
                        + "function f(a) { let [x, y] = a; return x + ',' + y; }"
                        + "r.push(f([1, 2]));"
                        + "var values = Array.prototype[Symbol.iterator];"
                        + "Array.prototype[Symbol.iterator] = function* () { yield 'x'; yield 'x'; };"
                        + "r.push(f([1, 2]));"
                        + "Array.prototype[Symbol.iterator] = values;"
                        + "r.push(f([1, 2]));"
                        + "r.join(' ')");
    }

    @Test
    void deletedAndRestoredIteratorMethod() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1 TypeError 1",
                "var r = [];"
                        + "function f(a) { try { const [x] = a; return x; }"
                        + "  catch (e) { return e.name; } }"
                        + "r.push(f([1]));"
                        + "var values = Array.prototype[Symbol.iterator];"
                        + "delete Array.prototype[Symbol.iterator];"
                        + "r.push(f([1]));"
                        + "Array.prototype[Symbol.iterator] = values;"
                        + "r.push(f([1]));"
                        + "r.join(' ')");
    }

    @Test
    void replacedNextMethod() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "3 next,next,next,next 3",
                "var r = [], log = [];"
                        + "function f(a) { var s = 0; for (var v of a) s += v; return s; }"
                        + "r.push(f([1, 2]));"
                        + "var proto = Object.getPrototypeOf([][Symbol.iterator]());"
                        + "var next = proto.next;"
                        + "proto.next = function () { log.push('next'); return next.call(this); };"
                        + "f([1, 2]); f([]);"
                        + "proto.next = next;"
                        + "r.push(log.join(), f([1, 2]));"
                        + "r.join(' ')");
    }

    @Test
    void ownIteratorMethodAndAccessor() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1 own get 1",
                "var r = [];"
                        + "function f(a) { const [x] = a; return x; }"
                        + "r.push(f([1]));"
                        + "var a = [1]; a[Symbol.iterator] = function* () { yield 'own'; };"
                        + "r.push(f(a));"
                        + "var values = Array.prototype[Symbol.iterator];"
                        + "Object.defineProperty(Array.prototype, Symbol.iterator, {"
                        + "  get() { return function* () { yield 'get'; }; }, configurable: true });"
                        + "r.push(f([1]));"
                        + "Object.defineProperty(Array.prototype, Symbol.iterator, {"
                        + "  value: values, writable: true, configurable: true });"
                        + "r.push(f([1]));"
                        + "r.join(' ')");
    }

    @Test
    void holesAndGrowingArrays() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,p,3 1,2,3,4",
                "var r = [];"
                        + "Array.prototype[1] = 'p';"
                        + "var [x, y, z] = [1, , 3]; r.push([x, y, z].join());"
                        + "delete Array.prototype[1];"
                        + "var a = [1, 2], seen = [];"
                        + "for (var v of a) { seen.push(v); if (a.length < 4) a.push(a.length + 1); }"
                        + "r.push(seen.join());"
                        + "r.join(' ')");
    }

    @Test
    void closedUnlessTheIteratorThrows() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "return Error,throw",
                "var log = [];"
                        + "function iter(throwAt) { var i = 0; return { [Symbol.iterator]() { return {"
                        + "  next() { if (++i == throwAt) throw new Error('throw');"
                        + "    return { value: i, done: false }; },"
                        + "  return() { log.push('return'); return {}; } }; } }; }"
                        + "let [a] = iter(0);"
                        + "try { let [b, c] = iter(2); } catch (e) { log.push(e.name + ',' + e.message); }"
                        + "log.join(' ')");
    }

    @Test
    void closedWhenATargetThrows() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "return,set",
                "var log = [];"
                        + "var it = { [Symbol.iterator]() { return {"
                        + "  next() { return { value: 1, done: false }; },"
                        + "  return() { log.push('return'); return {}; } }; } };"
                        + "var o = { set x(v) { throw new Error('set'); } };"
                        + "try { [o.x] = it; } catch (e) { log.push(e.message); }"
                        + "log.join()");
    }

    @Test
    void targetResolvedBeforeTheStep() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "has it, has x, next, has x, set x=1",
                "var log = [];"
                        + "var p = new Proxy({ x: 0 }, {"
                        + "  has(t, k) { if (typeof k === 'string') log.push('has ' + k);"
                        + "    return k === 'x'; },"
                        + "  set(t, k, v) { log.push('set ' + k + '=' + v); t[k] = v; return true; } });"
                        + "var it = { [Symbol.iterator]() { return {"
                        + "  next() { log.push('next'); return { value: 1, done: false }; },"
                        + "  return() { return {}; } }; } };"
                        + "with (p) { [x] = it; }"
                        + "log.join(', ')");
    }

    @Test
    void parametersAndRest() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1|2,3 4|",
                "function f([a, ...rest]) { return a + '|' + rest.join(); }"
                        + "[f([1, 2, 3]), f([4])].join(' ')");
    }
}
