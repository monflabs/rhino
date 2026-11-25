/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EcmaError;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Bindings whose temporal dead zone is only known at run time: global lexical declarations,
 * parameters and the bindings of the head of a for loop, reached from eval code, closures and other
 * scripts.
 */
class TemporalDeadZoneRuntimeTest {

    private static void assertDeadZone(String expression) {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError",
                "var r = 'no error'; try { " + expression + " } catch (e) { r = e.name } r");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "x = 1",
                "[x] = [1]",
                "'use strict'; x = 1",
                "for (x of [1]);",
                "for (x in {a: 1});",
                "(() => { x = 1 })()"
            })
    void writesToGlobalLexicals(String body) {
        for (String declaration : new String[] {"let x;", "const x = 2;", "class x {}"}) {
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                        TopLevel scope = cx.initStandardObjects();
                        // from a function of another script, and from a function of no script
                        cx.evaluateString(scope, "function f() { " + body + " }", "a", 1, null);
                        EcmaError e =
                                assertThrows(
                                        EcmaError.class,
                                        () ->
                                                cx.evaluateString(
                                                        scope,
                                                        "f(); " + declaration,
                                                        "b",
                                                        1,
                                                        null));
                        assertEquals("ReferenceError", e.getName());
                        return null;
                    });
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                        TopLevel scope = cx.initStandardObjects();
                        String source = "new Function(\"" + body + "\")(); " + declaration;
                        EcmaError e =
                                assertThrows(
                                        EcmaError.class,
                                        () -> cx.evaluateString(scope, source, "b", 1, null));
                        assertEquals("ReferenceError", e.getName());
                        return null;
                    });
        }
    }

    @Test
    void globalLexicalsAreInitializedAndAssigned() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2,3,4,5,6,7",
                "let a = 1, b; let [c, {d}] = [3, {d: 4}]; let {e, ...f} = {e: 5, g: 6};"
                        + "class K {}; K = 7; b = 2;"
                        + "[a, b, c, d, e, f.g, K].join()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "2,9", "let a = 1; new Function('a = 2')(); let s; for (s of [8, 9]); a + ',' + s");
        Utils.assertWithAllModes_ECMASCRIPT("3", "'use strict'; let t = 1; t = 3; String(t)");
        Utils.assertWithAllModes_ECMASCRIPT("4", "new Function('free = 4')(); String(free)");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "(function (a = eval('b'), b) {})()",
                "(function (a = eval('b = 1'), b) {})()",
                "(function (a = eval('a')) {})()",
                "(function (a = eval('b'), {b}) {})(undefined, {})",
                "(function (a = eval('b'), ...b) {})()",
                "(function ({a = eval('b'), b}) {})({})",
                "((a = eval('b'), b) => {})()",
                "({m(a = eval('b'), b) {}}).m()",
                "(function* (a = eval('b'), b) {})()",
                "(function (a = (() => b)(), b) {})()",
                "(function (a = (() => { b = 1 })(), b) {})()",
                "((a = (() => { b = 1 })(), b) => {})()",
                "(function (a = (() => typeof b)(), b) {})()",
                "(function ({a = (() => b)(), b}) {})({})",
                "(function (a = (b = 1), b) {})()",
                "(function (a = b++, b) {})()",
                "(function (a = ([b] = [1]), b) {})()"
            })
    void parameters(String call) {
        assertDeadZone(call);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "(function ({b = a}, a = 2) {})({})",
                "(function ({b = a}, a) {})({}, 4)",
                "(function ({a = b, b}) {})({b: 2})",
                "(function ({a = a}) {})({})",
                "(function ([a = b, b]) {})([])",
                "(function (...[a = b, b]) {})()",
                "(function ({a = b, ...b}) {})({})",
                "(function ({a: {x} = b, b}) {})({b: {x: 1}})",
                "(function ({a: {x = b}, b}) {})({a: {}, b: 1})",
                "(function ({a: [x = b], b}) {})({a: [], b: 1})",
                "(function ({[b]: a, b}) {})({b: 1})",
                "(function ({a} = b, b) {})(undefined, {a: 1})",
                "(function ({a} = {a: a}) {})()",
                "(function ({a = typeof b, b}) {})({})",
                "(function ({a = b = 1, b}) {})({})",
                "(function ({a = b++, b}) {})({})",
                "(function (a = {[b]: 1}, b) {})()",
                "(({a = b, b}) => {})({b: 2})",
                "({m({a = b, b}) {}}).m({})",
                "(function* ({a = b, b}) {})({})"
            })
    void patterns(String call) {
        assertDeadZone(call);
    }

    @Test
    void patternsReadInitializedNames() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2|3|1|1|7|5|1|undefined|4",
                "[(function ({b, a = b}) { return a })({b: 2}),"
                        + " (function ([a, b = a]) { return b })([3]),"
                        + " (function ({b, a: {x} = b}) { return x })({b: {x: 1}}),"
                        + " (function (b, {a} = b) { return a })({a: 1}),"
                        + " (function ({b, [b]: a}) { return a })({b: 'q', q: 7}),"
                        + " (function ({a = () => b, b}) { return a() })({b: 5}),"
                        + " (function ({a = {b: 1}, b}) { return a.b })({}),"
                        + " (function ({a = ({}).b, b}) { return String(a) })({}),"
                        + " (function (a = ((b) => b)(4), b) { return a })()"
                        + "].join('|')");
        // the name of a class shadows the parameter
        Utils.assertWithAllModes_ECMASCRIPT(
                "b", "(function (a = class b { static x = b.name }, b) { return a.x })()");
    }

    @Test
    void parametersAreInitialized() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "3|7|2|1,2|7|fn|2|9",
                "[(function (a, b = eval('a')) { return b })(3),"
                        + " (function (a = eval('1'), b) { var b; return b })(1, 7),"
                        + " (function (a = () => b, b = 2) { return a() })(),"
                        + " (function (a = eval('1'), ...b) { return b.join() })(0, 1, 2),"
                        + " (function (a = () => { b = 7 }, b = 2) { a(); return b })(),"
                        + " (function (a = eval('1'), b) {"
                        + "     return b(); function b() { return 'fn' } })(0, 8),"
                        + " (function* (a = () => b, b = 2) { yield a() })().next().value,"
                        + " (function (a = eval('1'), b) { return arguments[1] })(0, 9)"
                        + "].join('|')");
    }

    @Test
    void parametersAreInitializedInOrder() {
        Utils.assertGatedBehavior(
                "undefined,undefined",
                "1,5",
                "[(function (a = 1, {b = a}) { return b })(undefined, {}),"
                        + " (function (a, b = a, {c} = {c: b}) { return c })(5)]"
                        + ".map(String).join()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "3,2",
                "(function ({b = 3}, a = b, {c = a - 1}) { return [a, c] })({}, undefined, {}).join()");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "for (let x = eval('x = 1');;) break",
                "for (let x = eval('x');;) break",
                "for (let x = (() => typeof x)();;) break",
                "for (let x = (() => { x = 1 })();;) break",
                "for (let x = eval('y'), y;;) break",
                "for (let [x = eval('y'), y] = [];;) break",
                "for (let [x = (() => y)(), y] = [];;) break"
            })
    void forHeads(String loop) {
        assertDeadZone(loop);
        assertDeadZone("(function () { " + loop + " })()");
        assertDeadZone("(function () { let x = 5, y = 6; " + loop + " })()");
    }

    @Test
    void forHeadsAreInitialized() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "0,1,4,3",
                "var r = [];"
                        + "(function () { for (let i = 0, f = () => i; ;) { r.push(f()); break } })();"
                        + "(function () { for (let i = 1, {f} = {f: () => i}; ;) {"
                        + "  r.push(f()); break } })();"
                        + "(function () { for (let [x, y = () => x] = [4]; ;) {"
                        + "  r.push(y()); break } })();"
                        + "(function () { for (let x; ;) { eval('x = 3'); r.push(x); break } })();"
                        + "r.join()");
    }
}
