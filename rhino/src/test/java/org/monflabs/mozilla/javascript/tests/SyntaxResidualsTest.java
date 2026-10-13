/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.RhinoException;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Language residuals (plan 1.10). */
class SyntaxResidualsTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    private static void assertES6(Context.EvaluationMethod mode, Object expected, String script) {
        Utils.runWithMode(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    var scope = cx.initStandardObjects();
                    assertEquals(expected, cx.evaluateString(scope, script, "test.js", 1, null));
                    return null;
                },
                mode);
    }

    @Test
    void completionValues() {
        Utils.assertGatedBehavior(
                "1,2,2,8,3",
                ",,,7,",
                "[eval('1; do { } while (false)'), eval('2; if (true) {}'),"
                        + " eval('1; try { } finally { 2; }'), eval('6; try { 7; } finally { 8; }'),"
                        + " eval('3; switch (0) { case 1: 4; }')].join()");
    }

    @Test
    void logicalAssignmentShortCircuits() {
        assertBoth(
                "0|1|ok|2",
                "var r = [];"
                        + " var o = {}; Object.defineProperty(o, 'p', { value: 0, writable: false });"
                        + " r.push(o.p &&= 1);"
                        + " var q = { get p() { return 1; } }; r.push(q.p ||= 2);"
                        + " var calls = 0; var k = { toString() { calls++; return 'x'; } };"
                        + " var t = {}; t[k] ??= 'ok'; r.push(t.x);"
                        + " var n; n ||= 2; r.push(n);"
                        + " r.join('|')");
    }

    @Test
    void logicalAssignmentShortCircuitsInStrictCode() {
        Utils.assertGatedBehavior(
                "TypeError|TypeError",
                "0|1",
                "(function () { 'use strict'; var r = [];"
                        + " var o = {}; Object.defineProperty(o, 'p', { value: 0, writable: false });"
                        + " try { r.push(o.p &&= 1); } catch (e) { r.push(e.name); }"
                        + " var q = { get p() { return 1; } };"
                        + " try { r.push(q.p ||= 2); } catch (e) { r.push(e.name); }"
                        + " return r.join('|'); })()");
    }

    @Test
    void compoundAssignmentConvertsKeyOnce() {
        Utils.assertGatedBehavior(
                "2|evaluated|2",
                "1|TypeError|1",
                "var r = []; var calls = 0;"
                        + " var k = { toString() { calls++; return 'a'; } };"
                        + " var o = { a: 1 }; o[k] += 1; r.push(calls);"
                        + " try { null[{ toString() { throw 'evaluated'; } }] += 1; } catch (e) { r.push(e.name || e); }"
                        + " calls = 0; o[k]++; r.push(calls);"
                        + " r.join('|')");
    }

    @Test
    void withUnscopablesAndStrictPutValue() {
        Utils.assertGatedBehavior(
                "inner|no|no",
                "outer|ReferenceError|TypeError",
                "var r = []; var v = 'outer';"
                        + " var env = { v: 'inner', [Symbol.unscopables]: { v: true } };"
                        + " with (env) { r.push(v); }"
                        + " Object.defineProperty(this, 'gx', { configurable: true, value: 1 });"
                        + " var self = this;"
                        + " (function () { 'use strict'; try { gx = (delete self.gx, 2); r.push('no'); }"
                        + " catch (e) { r.push(e.name); } })();"
                        + " (function () { 'use strict'; var o = {};"
                        + " Object.defineProperty(o, 'p', { get() { return 1; }, set: undefined });"
                        + " try { o.p = 2; r.push('no'); } catch (e) { r.push(e.name); } })();"
                        + " r.join('|')");
    }

    @Test
    void arrowCoverAndCommentsAndEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1|SyntaxError|2|1|local",
                "var r = [];"
                        + " var af = ({ x = 1 }) => x; r.push(af({}));"
                        + " try { eval('({ x = 1 })'); r.push('no'); } catch (e) { r.push(e.name); }"
                        + " r.push(eval(\"1/*\\n*/\\n2\"));"
                        + " var x = 'global';"
                        + " (function () { var x = 'local'; eval(...['x = 1']); r.push(x); })();"
                        + " r.push((function () { var x = 'local'; eval(...['1']); return x; })());"
                        + " r.join('|')");
    }

    @Test
    void arrowCoverAndCommentLineTerminator() {
        Utils.assertGatedBehavior(
                "SyntaxError|SyntaxError",
                "1|2",
                "var r = [];"
                        + " try { r.push(eval('var af = ({ x = 1 }) => x; af({})')); }"
                        + " catch (e) { r.push(e.name); }"
                        + " try { r.push(eval(\"1 /*\\n*/ 2\")); } catch (e) { r.push(e.name); }"
                        + " r.join('|')");
    }

    @Test
    void instanceofAndRestWithProxies() {
        Utils.assertGatedBehavior(
                "false|false|0,a,1",
                "true|false|TypeError|a,1",
                "var r = [];"
                        + " var F = { [Symbol.hasInstance](v) { return v === 0; } }; r.push(0 instanceof F);"
                        + " r.push(1 instanceof function () {});"
                        + " try { 1 instanceof Math; } catch (e) { r.push(e.name); }"
                        + " var log = []; var p = new Proxy({ 0: 1, a: 2 }, { getOwnPropertyDescriptor(t, k) {"
                        + " log.push(k); return Reflect.getOwnPropertyDescriptor(t, k); } });"
                        + " var { 0: z, ...rest } = p; r.push(Object.keys(rest).concat(z).join());"
                        + " r.join('|')");
    }

    @Test
    void letAsIdentifier() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "1|2|3",
                "try { eval('var let = 1; var r = [let]; let = 2; r.push(let);"
                        + " var o = { let }; r.push(o.let + 1); r.join(\"|\")'); } catch (e) { e.name }");
    }

    @Test
    void earlyErrors() {
        Utils.assertGatedBehavior(
                "ok,ok,ok,ok,ok,ok,ok,ok,ok,ok,ok,ok,ok,ok,ok,ok",
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,"
                        + "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,"
                        + "SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                "['f() &&= 1', '\"use strict\"; f() = 1', '\"use strict\"; delete x',"
                        + " '\"use strict\"; eval++', 'a?.b`c`', 'tru\\\\u0065',"
                        + " 'for (x o\\\\u0066 []) ;', '3in []', '08n', '\"use strict\"; \"\\\\1\"',"
                        + " '\"\\\\1\"; \"use strict\";', 'l: const x = 1;',"
                        + " 'l: { for (;;) continue l; }', 'try {} catch (e) { let e; }', 'const x;',"
                        + " 'with ({}) function g() {}'].map(function (src) {"
                        + " try { eval('(function () { ' + src + ' })'); return 'ok'; }"
                        + " catch (e) { return e.name; } }).join()");
    }

    @Test
    void moreEarlyErrors() {
        Utils.assertGatedBehavior(
                "ok,ok,ok",
                "SyntaxError,SyntaxError,SyntaxError",
                "['\"use strict\"; function* g() { 0, function (x = yield) {}; }',"
                        + " 'function f(p) { let p; }', '() => new.target'].map(function (src) {"
                        + " try { (0, eval)(src); return 'ok'; } catch (e) { return e.name; } }).join()");
    }

    @Test
    void finallyAfterReturnRunsOnce() {
        String script =
                "var r = []; var c = 0;"
                        + " function f1() { try { return 1; } catch (e) { } finally { c++; throw 'f'; } }"
                        + " try { f1(); } catch (e) { r.push(e, c); }"
                        + " function f2() { try { throw 'a'; return 1; } catch (e) { throw 'b'; }"
                        + " finally { throw 'c'; } }"
                        + " try { f2(); } catch (e) { r.push(e); }"
                        + " r.join('|')";
        Utils.assertWithAllModes_ECMASCRIPT("f|1|c", script);
        // VERSION_ES6: the compiled finally block runs twice, as in stock Rhino
        assertES6(Context.EvaluationMethod.Interpreter, "f|1|c", script);
        assertES6(Context.EvaluationMethod.Compiler, "f|2|c", script);
    }

    @Test
    void generatorMethodPrototypeAndShiftOrder() {
        Utils.assertGatedBehavior(
                "false|TypeError",
                "true|mine",
                "var r = [];"
                        + " r.push(({ *g() {} }).g.hasOwnProperty('prototype'));"
                        + " try { 0n >>> { [Symbol.toPrimitive]() { throw 'mine'; } }; }"
                        + " catch (e) { r.push(e === 'mine' ? e : e.name); }"
                        + " r.join('|')");
    }

    @Test
    void nestedCoverInitializedNames() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "3",
                "try { eval('var x, y; [{ x = 1 }] = [{}]; ({ a: { y = 2 } } = { a: {} });"
                        + " String(x + y)'); } catch (e) { e.name }");
        Utils.assertGatedBehavior(
                "SyntaxError,ok",
                "SyntaxError,SyntaxError",
                "['[{x = 1}].length', 'var a = {x = 1} + 1'].map(function (s) {"
                        + " try { eval(s); return 'ok'; } catch (e) { return e.name; } }).join()");
    }

    @Test
    void forInShadowingAndEscapedDirective() {
        Utils.assertGatedBehavior(
                "p,q|true",
                "q|false",
                "var keys = [];"
                        + " var o = Object.create({ p: 1, q: 2 }, { p: { value: 0, enumerable: false } });"
                        + " for (var k in o) keys.push(k);"
                        + " function f() { 'use str\\\nict'; return this === undefined; }"
                        + " keys.join() + '|' + f()");
    }

    @Test
    void optionalCallOfEvalIsIndirect() {
        Utils.assertGatedBehavior(
                "local",
                "global",
                "var r = 'global'; (function () { var r = 'local'; return eval?.('r'); })()");
    }

    @Test
    void supplementaryIdentifiers() {
        assertBoth(
                "3",
                "var \uD80C\uDC00 = 1; var a\uD80C\uDC00 = 2; String(\uD80C\uDC00 + a\uD80C\uDC00)");
    }

    @Test
    void globalDeclarationInstantiation() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    var scope = cx.initStandardObjects();
                    cx.evaluateString(
                            scope,
                            "Object.defineProperty(this, 'cfg', { configurable: true, value: 0 });",
                            "a",
                            1,
                            null);
                    cx.evaluateString(scope, "var NaN; function cfg() {}", "b", 1, null);
                    String error;
                    try {
                        cx.evaluateString(scope, "var created; let undefined;", "c", 1, null);
                        error = "none";
                    } catch (RhinoException e) {
                        error = "SyntaxError";
                    }
                    assertEquals(
                            "function|NaN|SyntaxError|undefined",
                            cx.evaluateString(
                                    scope,
                                    "[typeof cfg, String(NaN), '"
                                            + error
                                            + "', typeof created].join('|')",
                                    "d",
                                    1,
                                    null));
                    return null;
                });
    }
}
