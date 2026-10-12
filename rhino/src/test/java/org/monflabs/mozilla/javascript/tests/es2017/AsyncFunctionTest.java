/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2017;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Async functions (plan 3.1). */
class AsyncFunctionTest {

    /** Runs the script, then its promise jobs, and returns the value of log.join(). */
    private static void assertLog(int version, String expected, String script) {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(version);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(scope, "var log = [];\n" + script, "test", 1, null);
                    assertEquals(
                            expected,
                            cx.evaluateString(scope, "log.join()", "log", 1, null),
                            script);
                    return null;
                });
    }

    private static void assertLog(String expected, String script) {
        assertLog(Context.VERSION_ECMASCRIPT, expected, script);
        assertLog(Context.VERSION_ES6, expected, script);
    }

    @Test
    void bodyRunsUntilTheFirstAwait() {
        assertLog(
                "start,after call,got 21,42",
                "async function f(x) { log.push('start'); var y = await x;"
                        + " log.push('got ' + y); return y * 2; }\n"
                        + "f(21).then(v => log.push(v));\n"
                        + "log.push('after call');");
    }

    @Test
    void rejections() {
        assertLog(
                "TypeError,param,finally,caught r",
                "async function thrower() { null.x; }\n"
                        + "async function param(a = (() => { throw 'param'; })()) {}\n"
                        + "async function catcher() {\n"
                        + "  try { await Promise.reject('r'); } catch (e) { return 'caught ' + e; }\n"
                        + "  finally { Promise.resolve().then(() => log.push('finally')); }\n"
                        + "}\n"
                        + "thrower().catch(e => log.push(e.name));\n"
                        + "param().catch(e => log.push(e));\n"
                        + "catcher().then(v => log.push(v));");
    }

    @Test
    void methodsAndArrows() {
        assertLog(
                "3,s,42,arrow 2,lexical 7",
                "var o = { async m() { return await 1 + await 2; } };\n"
                        + "class C { async m() { return this.v; } static async s() { return 's'; } }\n"
                        + "var c = new C(); c.v = 42;\n"
                        + "var f = async x => 'arrow ' + await x;\n"
                        + "var holder = { v: 7, m() { return (async () => 'lexical ' + this.v)(); } };\n"
                        + "o.m().then(v => log.push(v))"
                        + ".then(() => C.s()).then(v => log.push(v))"
                        + ".then(() => c.m()).then(v => log.push(v))"
                        + ".then(() => f(2)).then(v => log.push(v))"
                        + ".then(() => holder.m()).then(v => log.push(v));");
    }

    @Test
    void asyncFunctionObjects() {
        assertLog(
                "AsyncFunction,false,TypeError,true,5",
                "var af = async function() {};\n"
                        + "var AsyncFunction = Object.getPrototypeOf(af).constructor;\n"
                        + "log.push(AsyncFunction.name, af.hasOwnProperty('prototype'));\n"
                        + "try { new af(); } catch (e) { log.push(e.name); }\n"
                        + "log.push(af() instanceof Promise);\n"
                        + "AsyncFunction('a', 'return await a;')(5).then(v => log.push(v));");
    }

    @Test
    void earlyErrors() {
        for (String source :
                new String[] {
                    "async function f() { var await; }",
                    "async function f(a = await 1) {}",
                    "async (await) => {}",
                    "async (a = await 1) => {}",
                    "async (...a,) => {}",
                    "async () => { (a = await 1) => {} }",
                    "label: async function f() {}",
                    "if (1) async function f() {}",
                    "class C { async constructor() {} }",
                    "await 1;"
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
    void awaitAndAsyncAreIdentifiersOutsideAsyncFunctions() {
        assertLog(
                "3,call 1,4",
                "var await = 3; log.push(await);\n"
                        + "function async(x) { return 'call ' + x; } log.push(async(1));\n"
                        + "var async = 4; log.push(async);");
    }
}
