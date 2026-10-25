/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ErrorReporter;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptCompileSpec;
import org.monflabs.mozilla.javascript.TopLevel;

/**
 * A function too large for a class file is split into smaller methods (Splitter) and runs compiled,
 * with the same results as unsplit code: returns, breaks and continues across the methods, try
 * statements, closures, this, arguments and stack traces.
 */
class MethodSplittingTest {

    private static final class RecordingReporter implements ErrorReporter {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void warning(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            warnings.add(message);
        }

        @Override
        public void error(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            throw new AssertionError(message);
        }

        @Override
        public EvaluatorException runtimeError(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
        }
    }

    /** Statements on one line each, about 2 KB of bytecode per 100. */
    private static String filler(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("if (n > ").append(i).append(") t++;\n");
        }
        return sb.toString();
    }

    private static Object run(int version, String source, boolean interpreted) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(interpreted);
            cx.setLanguageVersion(version);
            TopLevel scope = cx.initStandardObjects();
            RecordingReporter reporter = new RecordingReporter();
            Script script =
                    cx.compileScript(
                            ScriptCompileSpec.fromSource(source)
                                    .sourceName("split.js")
                                    .lineno(1)
                                    .compilationErrorReporter(reporter));
            Object result = Context.toString(script.exec(cx, scope, scope));
            assertTrue(reporter.warnings.isEmpty(), reporter.warnings.toString());
            return result;
        }
    }

    /** Runs a script compiled and interpreted: same result, and no fallback when compiled. */
    private static void assertRuns(int version, String expected, String source) {
        assertEquals(expected, run(version, source, true));
        assertEquals(expected, run(version, source, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void functionBeyondTheMethodLimit(int version) {
        assertRuns(
                version,
                "3000",
                "function f(n) { var t = 0;\n" + filler(3000) + "return t; }\nf(5000);");
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void loopBeyondTheJumpLimit(int version) {
        assertRuns(
                version,
                "3000",
                "function f(n) { var t = 0;\n"
                        + "for (var k = 0; k < 2; k++) {\n"
                        + filler(1500)
                        + "}\nreturn t; }\nf(5000);");
    }

    /** A single statement too large, split from the innermost else part out. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void elseIfChain(int version) {
        StringBuilder sb = new StringBuilder("function f(n) { var r = 'none';\n");
        for (int i = 0; i < 2000; i++) {
            sb.append(i == 0 ? "" : "else ").append("if (n == ").append(i).append(") {\n");
            sb.append("  r = 'v").append(i).append("'; if (n == 1500) return r + '!';\n}\n");
        }
        sb.append("return r; }\n[f(3), f(1500), f(1999), f(5000)].join();");
        assertRuns(version, "v3,v1500!,v1999,none", sb.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void returnsAndJumpsAcrossTheChunks(int version) {
        String source =
                "function f(n, stop) { var t = 0, log = [];\n"
                        + filler(1000)
                        + "outer: for (var i = 0; i < 4; i++) {\n"
                        + "  for (var j = 0; j < 4; j++) {\n"
                        + filler(300)
                        + "    if (j == 1) continue;\n"
                        + "    if (j == 3) continue outer;\n"
                        + "    if (i == 2) break outer;\n"
                        + "    log.push(i + '' + j);\n"
                        + "  }\n"
                        + "}\n"
                        + "if (stop) { log.push('stop'); return log.join(); }\n"
                        + filler(1000)
                        + "try {\n"
                        + filler(500)
                        + "  return log.join() + ':' + t;\n"
                        + "} finally { log.push('finally'); }\n"
                        + "}\n"
                        + "f(5000, true) + ' ' + f(5000, false);";
        assertRuns(version, "00,02,10,12,stop 00,02,10,12:5200", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void bindingsAndThisAcrossTheChunks(int version) {
        String source =
                "function f(n) { var t = 0;\n"
                        + "var self = this, fs = [];\n"
                        + filler(1500)
                        + "var count = 0; function inc() { return ++count; }\n"
                        + "fs.push(function () { return t; });\n"
                        + filler(1500)
                        + "inc(); inc();\n"
                        + "return [self === this, this.v, arguments.length, arguments[1],"
                        + " fs[0](), inc(), typeof g].join(); }\n"
                        + "f.call({ v: 7 }, 5000, 'b');";
        assertRuns(version, "true,7,2,b,3000,3,undefined", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ECMASCRIPT})
    void lexicalBindingsAcrossTheChunks(int version) {
        String source =
                "function f(n) { let t = 0; const k = 2;\n"
                        + filler(1500)
                        + "let late;\n"
                        + "var fs = []; for (let i = 0; i < 3; i++) fs.push(() => i * k);\n"
                        + filler(1500)
                        + "var tdz; try { before; } catch (e) { tdz = e.name; } let before = 1;\n"
                        + "return [t, fs.map(g => g()).join(''), late, tdz].join(); }\n"
                        + "f(5000);";
        assertRuns(version, "3000,024,,ReferenceError", source);
    }

    /** The top-level code of a script, with its completion value. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void scriptBeyondTheMethodLimit(int version) {
        String source =
                "var n = 5000, t = 0, log = [];\n"
                        + filler(1500)
                        + "function g() { return t; }\n"
                        + "var self = this;\n"
                        + filler(1500)
                        + "for (var k = 0; k < 2; k++) {\n"
                        + filler(1500)
                        + "  if (k == 1) break;\n"
                        + "  log.push(k);\n"
                        + "}\n"
                        + "[g(), log.join(), self === this, typeof n].join();\n"
                        + "var unused = 1;";
        assertRuns(version, "6000,0,true,number", source);
    }

    /** Top-level code calls a function directly in no version, split or not. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void restParameterCalledFromASplitScript(int version) {
        String source =
                "var n = 5000, t = 0;\n"
                        + "function rest(...a) { return a; }\n"
                        + filler(3000)
                        + "var r = rest('xy');\n"
                        + "Array.isArray(r) + '-' + r.length;";
        assertRuns(version, "true-1", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void completionValueOfASplitScript(int version) {
        String source = "var n = 5000, t = 0;\n" + filler(3000) + "t;\nif (false) t = 1;";
        // UpdateEmpty: the if statement completes with undefined under VERSION_ECMASCRIPT
        assertRuns(version, version == Context.VERSION_ECMASCRIPT ? "undefined" : "3000", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void stackTraceShowsTheFunction(int version) {
        String source =
                "function f(n) { var t = 0;\n"
                        + filler(3000)
                        + "try { null.x; } catch (e) { return e.stack.split('\\n')[0].trim(); } }\n"
                        + "f(5000);";
        assertRuns(version, "at split.js:3002 (f)", source);
    }

    /** Runs a script compiled and interpreted: the same result, and no fallback when compiled. */
    private static void assertSame(int version, String source) {
        assertEquals(run(version, source, true), run(version, source, false));
    }

    /** A switch of more clauses than a method holds, which the Splitter splits by runs of them. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void switchOfManyClauses(int version) {
        StringBuilder sb =
                new StringBuilder("var order = [];\nfunction k(i) { order.push(i); return i; }\n");
        sb.append("function f(n, log) {\n  for (var round = 0; round < 2; round++) {\n");
        sb.append("    switch (n + round) {\n");
        for (int i = 0; i < 2000; i++) {
            if (i == 400) {
                sb.append("      default: log.push('default');\n");
            }
            if (i % 7 == 0) {
                // the case expressions are evaluated in order until one matches
                sb.append("      case k(").append(i).append("):\n");
            } else {
                sb.append("      case ").append(i).append(":\n");
            }
            if (i % 5 == 0) {
                // falls through to the next clause
                sb.append("        log.push('f").append(i).append("' + this.tag);\n");
            } else if (i == 777) {
                sb.append("        return log.concat(arguments.length).join();\n");
            } else if (i == 601) {
                sb.append("        continue;\n");
            } else {
                sb.append("        log.push(").append(i).append(" * 2 + n); break;\n");
            }
        }
        sb.append("    }\n    log.push('after' + round);\n  }\n  return log.join();\n}\n");
        sb.append("var o = { tag: '!' }, r = [];\n");
        sb.append("[0, 5, 9, 399, 400, 599, 600, 776, 777, 1999, 2500, -1, 'x'].forEach(");
        sb.append("function (n) { r.push(f.call(o, n, [])); });\n");
        sb.append("r.join('|') + '#' + order.join();");
        assertSame(version, sb.toString());
    }

    /** An object literal of more properties than a method defines, which the Splitter spreads. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ECMASCRIPT})
    void objectLiteralOfManyProperties(int version) {
        StringBuilder sb = new StringBuilder("var order = [];\n");
        sb.append("function k(s) { order.push(s); return s; }\n");
        sb.append("var proto = { inherited: 1, m() { return 'proto'; } };\n");
        sb.append("var extra = { e1: 'extra', [Symbol.for('s')]: 's' };\n");
        sb.append("function make(n) {\n  return {\n");
        for (int i = 0; i < 5000; i++) {
            switch (i) {
                case 1000:
                    sb.append("    __proto__: proto,\n");
                    break;
                case 2000:
                    sb.append("    get g() { return this.p1 + n; },\n");
                    break;
                case 2500:
                    sb.append("    m() { return 'own+' + super.m(); },\n");
                    break;
                case 3000:
                    sb.append("    ...extra,\n");
                    break;
                case 3500:
                    // a duplicate: the value is the last one, the position the first one
                    sb.append("    p10: 'again',\n");
                    break;
                default:
                    if (i % 97 == 0) {
                        sb.append("    [k('c").append(i).append("')]: k(").append(i).append("),\n");
                    } else {
                        sb.append("    p").append(i).append(": ").append(i).append(" + n,\n");
                    }
            }
        }
        sb.append("    f: function () {}, a: () => this\n  };\n}\n");
        sb.append("var o = make.call('self', 1), keys = Object.keys(o);\n");
        sb.append("[keys.length, keys.slice(0, 12).join(), keys.slice(-3).join(), o.p10, o.g,");
        sb.append(" o.m(), Object.getPrototypeOf(o) === proto, o.inherited, o.f.name,");
        sb.append(" String(o.a()), o[Symbol.for('s')], order.length, order.slice(0, 6).join()]");
        sb.append(".join('|');");
        assertSame(version, sb.toString());
    }

    /** A ?: too deep for a method, whose operands the Splitter moves to chunks. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void conditionalOfManyOperands(int version) {
        StringBuilder sb = new StringBuilder("function f(n) {\n  return ");
        for (int i = 0; i < 2500; i++) {
            sb.append("n < ").append(i).append(" ? ");
            if (i % 3 == 0) {
                sb.append("this.base + ").append(i);
            } else if (i % 3 == 1) {
                sb.append("arguments.length * ").append(i);
            } else {
                sb.append("(n = n + 0.5, n)");
            }
            sb.append(" :\n    ");
        }
        sb.append("-1;\n}\nvar o = { base: 10 }, r = [];\n");
        sb.append("for (var n = -1; n < 2502; n += 17) r.push(f.call(o, n, 0));\nr.join();");
        assertSame(version, sb.toString());
    }

    /** A var statement of more declarators than a method initializes, split into several. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void varStatementOfManyDeclarators(int version) {
        StringBuilder sb = new StringBuilder("function f(seed) {\n  var a0 = seed");
        for (int i = 1; i < 800; i++) {
            sb.append(",\n    a").append(i).append(" = [a").append(i - 1).append(", ").append(i);
            sb.append(", seed, 3, 5].reduce(function (s, x) { return (s * 3 + x) % 1000; }, 0)");
        }
        sb.append(";\n  return [a0, a400, a799, typeof a800].join();\n}\nvar a800;\nf(7);");
        assertSame(version, sb.toString());
    }

    /** Thousands of function declarations in a function: their creation alone exceeded a method. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void functionOfManyFunctionDeclarations(int version) {
        StringBuilder sb = new StringBuilder("function f(n) {\n");
        for (int i = 0; i < 4000; i++) {
            sb.append("function g").append(i).append("() { return ").append(i).append("; }\n");
        }
        sb.append("return g0() + g3999() + eval('g' + n)() + (typeof g17) + g17.name; }\nf(2000);");
        assertRuns(version, "5999functiong17", sb.toString());
    }

    /**
     * Comma expressions too large for a method, as minifiers write them (a return, a condition, an
     * operand of &&), whose runs of operands the Splitter moves to chunks.
     */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void commaExpressionOfManyOperands(int version) {
        StringBuilder operands = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            operands.append(
                    i % 2 == 0 ? "log.push(this.base + " + i + ")" : "t += arguments.length");
            operands.append(",\n    ");
        }
        String source =
                "function f(n) { var log = [], t = 0;\n"
                        + "  if (("
                        + operands
                        + "n > 2)) t = -t;\n"
                        + "  n > 1 && ("
                        + operands
                        + "t++);\n"
                        + "  return "
                        + operands
                        + "[t, log.length, log[1499]].join();\n}\n"
                        + "[f.call({ base: 1 }, 3), f.call({ base: 2 }, 1)].join(';');";
        assertRuns(version, "751,2250,1499;1500,1500,1500", source);
    }

    /**
     * A function of thousands of let, const and class declarations, as the module function of a
     * bundle: the Splitter moves them to chunks, which initialize the bindings of the function.
     */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void functionOfManyLexicalDeclarations(int version) {
        boolean latest = version == Context.VERSION_ECMASCRIPT;
        StringBuilder sb = new StringBuilder("function f(n) {\n");
        sb.append(
                "  var early = () => v2999; var tdz; try { early(); } catch (e) { tdz = e.name; }\n");
        sb.append("  let v0 = n;\n");
        for (int i = 1; i < 3000; i++) {
            if (latest && i % 100 == 0) {
                sb.append("  class v").append(i).append(" { static m() { return v");
                sb.append(i - 1).append(" + v").append(i).append(".name; } }\n");
            } else {
                sb.append(i % 2 == 0 ? "  let v" : "  const v").append(i);
                sb.append(" = [v").append(i - 1).append(", ").append(i).append("].length + ");
                sb.append(i).append(";\n");
            }
        }
        sb.append("  var assign; try { v2999 = 1; } catch (e) { assign = e.name; }\n");
        sb.append("  return [tdz, early(), v1, v2998, assign");
        sb.append(latest ? ", v100.m(), v2900.m()" : "").append("].join();\n}\nf(5);");
        assertRuns(
                version,
                latest ? "ReferenceError,3001,3,3000,TypeError,101v100,2901v2900" : ",3001,3,3000,",
                sb.toString());
    }
}
