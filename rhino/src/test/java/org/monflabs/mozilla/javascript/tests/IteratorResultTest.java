/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Iterator results are ordinary objects with own data properties, so the built-in iterators can be
 * stepped without creating them.
 */
class IteratorResultTest {

    @Test
    void resultIgnoresSettersOfObjectPrototype() {
        Utils.assertGatedBehavior(
                "g,g|1,2,,,3|undefined",
                "1,2||3",
                "var log = [];"
                        + "Object.defineProperty(Object.prototype, 'value', {"
                        + "  set(v) { log.push(v); }, get() { return 'g'; }, configurable: true });"
                        + "var r = [...[1, 2]];"
                        + "var g = (function*() { yield 3; })().next();"
                        + "delete Object.prototype.value;"
                        + "r.join() + '|' + log.join() + '|' + g.value");
    }

    @Test
    void resultHasOwnDataProperties() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "value,done|true|true",
                "var r = [1].values().next();"
                        + "Object.getOwnPropertyNames(r).join()"
                        + " + '|' + (Object.getPrototypeOf(r) === Object.prototype)"
                        + " + '|' + Object.getOwnPropertyDescriptor(r, 'done').writable");
    }

    @Test
    void builtInIteratorsSpread() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2,3,4|a,b|5,6|0,1|3|true",
                "[...new Map([[1, 2], [3, 4]])].join()"
                        + " + '|' + [...'ab'].join()"
                        + " + '|' + Array.from(new Set([5, 6])).join()"
                        + " + '|' + [...[7, 8].keys()].join()"
                        + " + '|' + [...[1, , 3]].length"
                        + " + '|' + ([...[1, , 3]][1] === undefined)");
    }

    @Test
    void replacedNextIsCalled() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "x|1",
                "var proto = Object.getPrototypeOf([].values());"
                        + "var next = proto.next, n = 0;"
                        + "proto.next = function() {"
                        + "  return n++ ? { done: true } : { value: 'x', done: false }; };"
                        + "var r = [...[1, 2]];"
                        + "proto.next = next;"
                        + "r.join() + '|' + [...[1]].join()");
    }

    @Test
    void arrayGrowsDuringSpread() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2,3,9",
                "var a = [1, 2];"
                        + "Object.defineProperty(a, 1, {"
                        + "  get() { a.push(3); a.push(9); return 2; } });"
                        + "Array.from(a.values()).join()");
    }

    @Test
    void exhaustedIteratorStaysDone() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2||true",
                "var a = [1, 2], it = a.values();"
                        + "var first = [...it];"
                        + "a.push(3);"
                        + "first.join() + '|' + [...it].join() + '|' + it.next().done");
    }

    @Test
    void outOfBoundsTypedArray() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError",
                "var b = new ArrayBuffer(4, { maxByteLength: 8 });"
                        + "var t = new Uint8Array(b, 2, 2), it = t.values();"
                        + "b.resize(1);"
                        + "try { [...it]; 'none'; } catch (e) { e.name }");
    }
}
