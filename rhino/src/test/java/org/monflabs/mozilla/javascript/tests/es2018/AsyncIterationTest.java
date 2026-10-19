/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2018;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Async generators, yield* in them, and for await (plan 4.1). */
class AsyncIterationTest {

    /** Runs the script, then its promise jobs, and returns the value of log.join(). */
    private static void assertLog(String expected, String script) {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(scope, "var log = [];\n" + script, "test", 1, null);
                    assertEquals(
                            expected,
                            cx.evaluateString(scope, "log.join()", "log", 1, null),
                            script);
                    return null;
                });
    }

    @Test
    void asyncGenerators() {
        assertLog(
                "got 10,1:false,12:false,end:true,undefined:true",
                "async function* g() { var x = yield 1; log.push('got ' + x);"
                        + " var y = await Promise.resolve(2); yield y + x; return 'end'; }\n"
                        + "var it = g(); var r = v => log.push(v.value + ':' + v.done);\n"
                        + "it.next().then(r); it.next(10).then(r); it.next().then(r);"
                        + " it.next().then(r);");
        assertLog(
                "1,cleanup,42:true,rejected bad",
                "async function* g() { try { yield 1; yield 2; } finally { log.push('cleanup'); } }\n"
                        + "async function* h() { yield Promise.reject('bad'); }\n"
                        + "var it = g();\n"
                        + "it.next().then(r => { log.push(r.value); return it.return(42); })"
                        + ".then(r => log.push(r.value + ':' + r.done))"
                        + ".then(() => h().next())"
                        + ".catch(e => log.push('rejected ' + e));");
    }

    @Test
    void objectsAndPrototypes() {
        assertLog(
                "AsyncGeneratorFunction,[object AsyncGenerator],true,true",
                "async function* g() {}\n"
                        + "var AGF = Object.getPrototypeOf(g).constructor;\n"
                        + "var it = g();\n"
                        + "log.push(AGF.name, Object.prototype.toString.call(it),"
                        + " it[Symbol.asyncIterator]() === it,"
                        + " Object.getPrototypeOf(it) === g.prototype);");
    }

    @Test
    void yieldStarAndForAwait() {
        assertLog(
                "a,b,20,40,1,2,3",
                "async function* inner() { yield 'a'; yield 'b'; }\n"
                        + "async function* doubled() { for await (const x of [10, 20]) yield x * 2; }\n"
                        + "async function* outer() { yield* inner(); yield* doubled(); yield* [1, 2, 3]; }\n"
                        + "(async () => { for await (const v of outer()) log.push(v); })();");
    }

    @Test
    void forAwaitClosesItsIterator() {
        assertLog(
                "0,1,return,return,boom",
                "function iterable(onReturn) { return { [Symbol.asyncIterator]() { var i = 0;"
                        + " return { next() { return Promise.resolve({ value: i++, done: false }); },"
                        + " return() { log.push('return'); return {}; } }; } }; }\n"
                        + "(async () => {\n"
                        + "  for await (const v of iterable()) { if (v == 2) break; log.push(v); }\n"
                        + "  try { for await (const v of iterable()) throw new Error('boom'); }\n"
                        + "  catch (e) { log.push(e.message); }\n"
                        + "})();");
    }

    @Test
    void forAwaitEarlyErrors() {
        for (String source :
                new String[] {
                    "async function f() { for await (var x in {}) {} }",
                    "async function f() { for await (var x = 0; x < 1; x++) {} }",
                    "function f() { for await (var x of []) {} }"
                }) {
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                        TopLevel scope = cx.initStandardObjects();
                        assertThrows(
                                EvaluatorException.class,
                                () -> cx.evaluateString(scope, source, "test", 1, null),
                                source);
                        return null;
                    });
        }
    }

    @Test
    void forAwaitLeftByAJump() {
        assertLog(
                "1,returned,1,broken,2,continued",
                "var x = 'outer';\n"
                        + "async function r() { for await (const x of [1]) { log.push(x); return; } }\n"
                        + "async function b() { for await (const x of [1]) { log.push(x); break; } }\n"
                        + "async function c() { for await (const x of [1, 2]) {"
                        + " if (x == 1) continue; log.push(x); } }\n"
                        + "r().then(() => log.push('returned'))"
                        + ".then(b).then(() => log.push('broken'))"
                        + ".then(c).then(() => log.push('continued'));");
    }

    @Test
    void forAsyncOfIsGated() {
        Utils.assertGatedBehavior(
                "ok",
                "SyntaxError",
                "try { eval('for (async of []);'); 'ok' } catch (e) { e.name }");
    }
}
