/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Parser and scoping defects found by the review of the engine. */
class ReviewDefectsParserTest {

    /** The result of a source, or the name of the error it throws. */
    private static final String RUN =
            "function run(src) { try { return String(eval(src)); } catch (e) { return e.name; } }\n";

    /** ok, or the name of the error of a source that is only parsed. */
    private static final String PARSE =
            "function parse(src) { try { Function(src); return 'ok'; } catch (e) { return e.name; } }\n";

    @Test
    void patternDefaultDoesNotSeeBodyVars() {
        Utils.assertGatedBehavior(
                "inside|undefined|inside|inside|insideinside|inside inside",
                "outside|outside|outside|outside|outsideoutside|outside outside",
                "var x = 'outside';\n"
                        + "function f1({g} = {g: () => x}) { var x = 'inside'; return g(); }\n"
                        + "function f2([a] = [x]) { var x = 'inside'; return String(a); }\n"
                        + "var f3 = ({g} = {g: () => x}) => { var x = 'inside'; return g(); };\n"
                        + "function f4({g} = {g: () => x}) { let x = 'inside'; return g(); }\n"
                        + "function f5({g} = {g: () => x}, h = () => x) {\n"
                        + "  var x = 'inside'; return g() + h();\n"
                        + "}\n"
                        + "function f6(b = () => x, {g} = {g: () => x}) {\n"
                        + "  var x = 'inside'; return b() + ' ' + g();\n"
                        + "}\n"
                        + "[f1(), f2(), f3(), f4(), f5(), f6()].join('|')");
    }

    @Test
    void updateOfFunctionExpressionName() {
        Utils.assertGatedBehavior(
                "number,number,number,number,number",
                "function,function,function,function,function",
                "[(function g() { g++; return typeof g; })(),\n"
                        + " (function g() { ++g; return typeof g; })(),\n"
                        + " (function g() { g--; return typeof g; })(),\n"
                        + " (function g() { eval(''); --g; return typeof g; })(),\n"
                        + " (function g() { (() => { g++; })(); return typeof g; })()].join()");
    }

    @Test
    void updateOfFunctionExpressionNameInStrictCode() {
        Utils.assertGatedBehavior(
                "number,number,number,number",
                "TypeError function,TypeError function,TypeError function,TypeError function",
                "function probe(f) { return f(); }\n"
                        + "[(function g() { 'use strict';\n"
                        + "   try { g++; } catch (e) { return e.name + ' ' + typeof g; }\n"
                        + "   return typeof g; })(),\n"
                        + " (function g() { 'use strict';\n"
                        + "   try { --g; } catch (e) { return e.name + ' ' + typeof g; }\n"
                        + "   return typeof g; })(),\n"
                        + " (function g() { 'use strict'; eval('');\n"
                        + "   try { g--; } catch (e) { return e.name + ' ' + typeof g; }\n"
                        + "   return typeof g; })(),\n"
                        + " (function g() { 'use strict';\n"
                        + "   try { probe(() => ++g); } catch (e) { return e.name + ' ' + typeof g; }\n"
                        + "   return typeof g; })()].join()");
    }

    @Test
    void updateOfFunctionExpressionNameValue() {
        // the value is that of the update, although the binding is not written
        Utils.assertGatedBehavior(
                "5,7,7,5|5,7,7,5|NaN",
                "5,6,5,4|5,6,5,4|NaN",
                "[(function g() { g.valueOf = () => 5; return [g++, ++g, g--, --g].join(); })(),\n"
                        + " (function g() { g.valueOf = () => 5n; return [g++, ++g, g--, --g].join(); })(),\n"
                        + " (function g() { return g++; })()].join('|')");
    }

    @Test
    void namesOtherThanTheFunctionExpressionNameAreUpdated() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2,2,2,2",
                "[(function g() { var g = 1; g++; return g; })(),\n"
                        + " (function g(g) { g++; return g; })(1),\n"
                        + " (function g() { { let g = 1; g++; return g; } })(),\n"
                        + " (function g() { return (function h() { var h = 1; return ++h; })(); })()]"
                        + ".join()");
    }

    @Test
    void escapedKeywords() {
        String sources =
                "['v@ar x = 1', 'i@f (1) 2', 'typ@eof 1', '(functi@on () {})', 'd@elete this.a',\n"
                        + " 'a: for (;;) { br@eak a; }', 'for (;;) { c@ontinue; }', 'wh@ile (0) {}',\n"
                        + " 'c@onst y = 1', 'thr@ow 1', 'r@eturn 1', 'tr@y {} catch (e) {}',\n"
                        + " 'try {} c@atch (e) {}', 'try {} f@inally {}', 'sw@itch (1) {}',\n"
                        + " 'switch (1) { c@ase 1: }', 'switch (1) { d@efault: }', '1 @in {}',\n"
                        + " '({}) inst@anceof Object', 'v@oid 0', 'd@o {} while (0)',\n"
                        + " 'if (1) 2; @else 3', 'f@or (;;) {}', 'for (var k @in {}) {}',\n"
                        + " 'w@ith ({}) {}', 'd@ebugger', 'function* g() { yi@eld 1 }']";
        // the letter after @ is written as a Unicode escape
        String escape =
                "function escape(s) {\n"
                        + "  return s.replace(/@(.)/, (m, c) => '\\\\u00' + c.charCodeAt(0).toString(16));\n"
                        + "}\n";
        String summary =
                "results.length + ' ' + results.filter((r, i, a) => a.indexOf(r) === i).join()";
        Utils.assertGatedBehavior(
                "27 ok",
                "27 ok",
                PARSE
                        + "var results = "
                        + sources
                        + ".map(s => parse(s.replace('@', '')));\n"
                        + summary);
        Utils.assertGatedBehavior(
                "27 ok",
                "27 SyntaxError",
                PARSE
                        + escape
                        + "var results = "
                        + sources
                        + ".map(s => parse(escape(s)));\n"
                        + summary);
    }

    @Test
    void escapedKeywordsOfEcmaScript() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                PARSE
                        + "['cl\\\\u0061ss A {}', 'class A {}; class B \\\\u0065xtends A {}',\n"
                        + " 'var o = { m() { return sup\\\\u0065r.x; } }', 'imp\\\\u006frt(\"x\")',\n"
                        + " 'label: v\\\\u0061r x'].map(parse).join()");
    }

    @Test
    void escapedKeywordsAsNames() {
        // an IdentifierName is not an Identifier: a property name can be an escaped keyword
        Utils.assertWithAllModes_ECMASCRIPT(
                "6|1,2,3|5|10|function|1",
                "var o = {v\\u0061r: 1, \\u0069f: 2, n\\u0065w() { return 3; }, get \\u0069n() { return 0; }};\n"
                        + "class C { v\\u0061r() { return 1; } static d\\u0065lete() { return 2; }\n"
                        + "  \\u0069f = 3; }\n"
                        + "var {v\\u0061r: z} = {var: 5};\n"
                        + "var l\\u0065t = 1, yi\\u0065ld = 2, \\u0061sync = 3, aw\\u0061it = 4;\n"
                        + "function* g() { return function yi\\u0065ld() {}; }\n"
                        + "function* h() { return {yi\\u0065ld: 1}.yi\\u0065ld; }\n"
                        + "[o.v\\u0061r + o.\\u0069f + o.n\\u0065w() + o.\\u0069n,\n"
                        + " [new C().v\\u0061r(), C.d\\u0065lete(), new C().\\u0069f].join(),\n"
                        + " z, l\\u0065t + yi\\u0065ld + \\u0061sync + aw\\u0061it,\n"
                        + " typeof g().next().value, h().next().value].join('|')");
    }

    @Test
    void varOfCatchBlockCannotRedeclarePatternBinding() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                PARSE
                        + "['try {} catch ([e]) { var e; }', 'try {} catch ({e}) { var e; }',\n"
                        + " 'try {} catch ({a: e = 1, ...r}) { var r; }',\n"
                        + " 'try {} catch ([e]) { { var e = 1; } }',\n"
                        + " 'try {} catch ([e]) { for (var e of []) {} }',\n"
                        + " 'try {} catch ([e]) { var {e} = {}; }'].map(parse).join()");
        // Annex B.3.4: the var of a simple parameter is allowed
        Utils.assertWithAllModes_ECMASCRIPT(
                "ok,ok,ok,ok",
                PARSE
                        + "['try {} catch (e) { var e; }', 'try {} catch (e) { for (var e of []) {} }',\n"
                        + " 'try {} catch ([e]) { var f = function () { var e; }; }',\n"
                        + " 'try {} catch ([e]) { var x; }'].map(parse).join()");
    }

    @Test
    void functionNamedLet() {
        Utils.assertGatedBehavior(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                "1,function,2,let,SyntaxError,SyntaxError",
                RUN
                        + "[run('function let() { return 1; } let()'),\n"
                        + " run('(function let() { return typeof let; })()'),\n"
                        + " run('function* let() { yield 2; } let().next().value'),\n"
                        + " run('{ function let() {} } let.name'),\n"
                        + " run('\"use strict\"; function let() {}'),\n"
                        + " run('function let() { \"use strict\"; }')].join()");
    }

    @Test
    void constInTheHeadOfAForLoop() {
        String script =
                "var s = 0, t = 0, u = 0;\n"
                        + "for (const i = 5; s < 3; s++) {}\n"
                        + "for (const [i, n = i] = [1]; t < 3; t++) { t += n; }\n"
                        + "(function () { for (const {i, n = i} = {i: 2}, j = 1; u < 3; u++) { u += n + j; } })();\n"
                        + "[s, t, u].join()";
        Utils.assertWithAllModes_ES6("3,4,4", script);
        Utils.assertWithAllModes_ECMASCRIPT("3,4,4", script);
    }

    @Test
    void yieldInArrowParametersBeforeEcmaScript() {
        // accepted before VERSION_ECMASCRIPT (a SyntaxError there): compiled as it is interpreted
        Utils.assertWithAllModes_ES6(
                "true:3|SyntaxError",
                "function* g() { var f = (a = yield 7) => a; return f; }\n"
                        + "var r = g().next(), f = r.value, error;\n"
                        + "try { f(); } catch (e) { error = e.name; }\n"
                        + "r.done + ':' + f(3) + '|' + error");
    }
}
