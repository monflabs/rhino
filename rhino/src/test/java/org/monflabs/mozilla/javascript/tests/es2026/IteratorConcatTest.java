/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2026;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Iterator.concat (plan 12.4). */
class IteratorConcatTest {

    @Test
    void concatenates() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2,a,b,3|[object Iterator Helper]|0",
                "var it = Iterator.concat([1, 2], new String('ab'), new Set([3]));\n"
                        + "[[...it].join(), Object.prototype.toString.call(it),"
                        + " [...Iterator.concat()].length].join('|')");
    }

    @Test
    void opensLazilyAndForwardsReturn() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "open:a|1|open:b|close:b|true",
                "var log = [];\n"
                        + "function tracked(name, values) { return { [Symbol.iterator]() {"
                        + " log.push('open:' + name); var i = 0;"
                        + " return { next() { return { done: i >= values.length, value: values[i++] }; },"
                        + " return() { log.push('close:' + name); return {}; } }; } }; }\n"
                        + "var it = Iterator.concat(tracked('a', [1]), tracked('b', [2, 3]));\n"
                        + "log.push(it.next().value);\n"
                        + "it.next(); it.return();\n"
                        + "log.push(it.next().done);\n"
                        + "log.join('|')");
    }

    @Test
    void checksItemsFirst() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError|TypeError",
                "var r = [];\n"
                        + "try { Iterator.concat([1], 'not an object'); } catch (e) { r.push(e.name); }\n"
                        + "try { Iterator.concat({}); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }
}
