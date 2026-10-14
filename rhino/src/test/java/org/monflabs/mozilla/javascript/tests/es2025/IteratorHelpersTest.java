/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2025;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The Iterator global and the iterator helpers (plan 11.1). */
class IteratorHelpersTest {

    private static final String GEN = "function* g() { yield 1; yield 2; yield 3; yield 4; }\n";

    @Test
    void iteratorGlobalIsGated() {
        // the Iterator of JavaScript 1.7 before VERSION_ECMASCRIPT
        Utils.assertGatedBehavior(
                "0,a,1,b|false|undefined",
                "TypeError|true|function",
                "var r = [];\n"
                        + "try { var it = Iterator(['a', 'b']); r.push([it.next(), it.next()].join()); }"
                        + " catch (e) { r.push(e.name); }\n"
                        + "var ip = Object.getPrototypeOf(Object.getPrototypeOf([][Symbol.iterator]()));\n"
                        + "r.push(ip === Iterator.prototype, typeof ip[Symbol.iterator]);\n"
                        + "r.join('|')");
    }

    @Test
    void builtInIteratorsInheritIteratorPrototype() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,true,true,true,true,false",
                GEN
                        + "var ip = Iterator.prototype, p = Object.getPrototypeOf;\n"
                        + "[p(p([].keys())), p(p(''[Symbol.iterator]())), p(p(new Map().keys())),"
                        + " p(p(new Set().values())), p(p(p(g()))) ].every(x => x === ip)"
                        + " + ',' + [p(p(p(g()))) === ip, p(p('a'.matchAll(/a/g))) === ip,"
                        + " p(p(g.prototype)) === ip, ip.hasOwnProperty(Symbol.iterator),"
                        + " p([].keys()).hasOwnProperty(Symbol.iterator)].join()");
    }

    @Test
    void lazyHelpers() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "4-6-8|2-3|11223344|1,2,3,4|[object Iterator Helper]",
                GEN
                        + "[g().map(x => x * 2).filter(x => x > 2).toArray().join('-'),"
                        + " g().drop(1).take(2).toArray().join('-'),"
                        + " g().flatMap(x => [x, x]).toArray().join(''),"
                        + " g().drop(Infinity).toArray().concat([...g().take(Infinity)]).join(),"
                        + " Object.prototype.toString.call(g().map(x => x))].join('|')");
    }

    @Test
    void eagerHelpers() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "10|16|true|false|3||1:0,2:1,3:2,4:3|TypeError",
                GEN
                        + "var log = []; g().forEach((x, i) => log.push(x + ':' + i));\n"
                        + "var e; try { [].values().reduce((a, b) => a); } catch (x) { e = x.name; }\n"
                        + "[g().reduce((a, b) => a + b), g().reduce((a, b) => a + b, 6),"
                        + " g().some(x => x == 3), g().every(x => x < 3), g().find(x => x > 2),"
                        + " g().find(x => x > 9), log.join(), e].join('|')");
    }

    @Test
    void closesTheUnderlyingIterator() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1|2|3|4",
                "var closed = 0;\n"
                        + "function it() { return Iterator.from({ next() { return { value: 1, done: false }; },"
                        + " return() { closed++; return {}; } }); }\n"
                        + "var r = [];\n"
                        + "it().some(x => true); r.push(closed);\n"
                        + "try { it().map(1); } catch (e) { r.push(closed); }\n"
                        + "var m = it().take(1); m.next(); m.next(); r.push(closed);\n"
                        + "var f = it().flatMap(x => [x, x]); f.next(); f.return(); r.push(closed);\n"
                        + "r.join('|')");
    }

    @Test
    void iteratorFromAndConstructor() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "0,1,2|true|a,b|TypeError|TypeError|true|RangeError|true",
                "var r = [];\n"
                        + "var plain = { i: 0, next() { return { done: this.i > 2, value: this.i++ }; } };\n"
                        + "r.push(Iterator.from(plain).toArray().join());\n"
                        + "var arrayIterator = [].values(); r.push(Iterator.from(arrayIterator) === arrayIterator);\n"
                        + "r.push(Iterator.from('ab').toArray().join());\n"
                        + "try { new Iterator(); } catch (e) { r.push(e.name); }\n"
                        + "try { Iterator(); } catch (e) { r.push(e.name); }\n"
                        + "class Counter extends Iterator { next() { return { done: true }; } }\n"
                        + "r.push(new Counter().map(x => x) instanceof Iterator);\n"
                        + "try { [].values().take(-1); } catch (e) { r.push(e.name); }\n"
                        + "r.push(Iterator.prototype.constructor === Iterator);\n"
                        + "r.join('|')");
    }

    @Test
    void reentrantHelperThrows() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError",
                "var h = [1].values().map(x => h.next());\n"
                        + "try { h.next(); 'none' } catch (e) { e.name }");
    }

    @Test
    void ignoringSetters() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|TypeError|x|Iterator",
                "var ip = Iterator.prototype, r = [];\n"
                        + "var o = Object.create(ip); o.constructor = 5; r.push(o.hasOwnProperty('constructor'));\n"
                        + "try { ip[Symbol.toStringTag] = 'y'; } catch (e) { r.push(e.name); }\n"
                        + "o[Symbol.toStringTag] = 'x'; r.push(o[Symbol.toStringTag], ip[Symbol.toStringTag]);\n"
                        + "r.join('|')");
    }

    @Test
    void primitiveThis() {
        // call and apply give the primitive this unchanged to these built-ins
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError|TypeError|4|TypeError",
                "var r = [];\n"
                        + "Object.defineProperty(Number.prototype, 'next', { get() { throw 1; } });\n"
                        + "try { Iterator.prototype.map.call(0, x => x); } catch (e) { r.push(e.name); }\n"
                        + "try { Iterator.prototype.toArray.apply(true); } catch (e) { r.push(e.name); }\n"
                        + "r.push(typeof Iterator.prototype[Symbol.iterator].call(4) == 'number' ? 4 : 0);\n"
                        + "var set = Object.getOwnPropertyDescriptor(Iterator.prototype, 'constructor').set;\n"
                        + "try { set.call(true, 1); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }
}
