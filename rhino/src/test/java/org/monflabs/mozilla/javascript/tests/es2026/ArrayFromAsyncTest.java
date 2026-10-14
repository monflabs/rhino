/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2026;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Array.fromAsync (plan 12.7). */
class ArrayFromAsyncTest {

    /** Runs the script, then its promise jobs, and returns log.sort().join(' | '). */
    private static void assertLog(String expected, String script) {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(scope, "var log = [];\n" + script, "test.js", 1, null);
                    cx.processMicrotasks();
                    assertEquals(
                            expected,
                            cx.evaluateString(scope, "log.sort().join(' | ')", "log.js", 1, null));
                    return null;
                });
    }

    @Test
    void gated() {
        Utils.assertGatedBehavior("undefined", "function", "typeof Array.fromAsync");
    }

    @Test
    void sources() {
        assertLog(
                "async:1,2,3 | ctor:true2 | like:a,b | sync:40,50",
                "async function* ag() { yield 1; yield Promise.resolve(2); yield 3; }\n"
                        + "Array.fromAsync(ag()).then(a => log.push('async:' + a.join()));\n"
                        + "Array.fromAsync([Promise.resolve(4), 5], x => x * 10)"
                        + ".then(a => log.push('sync:' + a.join()));\n"
                        + "Array.fromAsync({ length: 2, 0: 'a', 1: Promise.resolve('b') })"
                        + ".then(a => log.push('like:' + a.join()));\n"
                        + "function C() {}\n"
                        + "Array.fromAsync.call(C, [1, 2])"
                        + ".then(a => log.push('ctor:' + (a instanceof C) + a.length));\n");
    }

    @Test
    void errors() {
        assertLog(
                "close:1 boom | mapfn:TypeError | null:TypeError | too long:RangeError",
                "Array.fromAsync(null).catch(e => log.push('null:' + e.name));\n"
                        + "Array.fromAsync([1], 5).catch(e => log.push('mapfn:' + e.name));\n"
                        + "Array.fromAsync.call({}, { length: 2 ** 32 })"
                        + ".catch(e => log.push('too long:' + e.name));\n"
                        + "var closed = 0;\n"
                        + "var it = { [Symbol.asyncIterator]() { return {"
                        + " next() { return Promise.resolve({ value: 1, done: false }); },"
                        + " return() { closed++; return {}; } }; } };\n"
                        + "Array.fromAsync(it, x => { throw new Error('boom'); })"
                        + ".catch(e => log.push('close:' + closed + ' ' + e.message));\n");
    }
}
