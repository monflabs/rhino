/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Defects of the built-ins found by a review of the engine. */
class ReviewDefectsBuiltinsTest {

    private static final String READ_ONLY_LENGTH =
            "function fixed(a) { return Object.defineProperty(a, 'length', {writable: false}); }\n"
                    + "function attempt(f) {\n"
                    + "  var a = fixed([1]);\n"
                    + "  try { f(a); } catch (e) { return e.name; }\n"
                    + "  return a.length + ':' + Object.keys(a);\n"
                    + "}\n"
                    + "[attempt(a => { a[1] = 2; }), attempt(a => { a[7] = 2; }),"
                    + " attempt(a => { a['1'] = 2; }), attempt(a => { a[4294967294] = 2; }),"
                    + " attempt(a => { a[1] += 2; }), attempt(a => { a[0] = 5; a.x = 6; }),"
                    + " attempt(a => { for (var i = 0; i < 3; i++) a[i] = 9; })].join(' ')";

    @Test
    void indexAtOrAboveReadOnlyLength() {
        Utils.assertGatedBehavior(
                "2:0,1 8:0,7 2:0,1 4294967295:0,4294967294 2:0,1 1:0,x 3:0,1,2",
                "1:0 1:0 1:0 1:0 1:0 1:0,x 1:0",
                READ_ONLY_LENGTH);
    }

    @Test
    void indexAtOrAboveReadOnlyLengthStrict() {
        Utils.assertGatedBehavior(
                "2:0,1 8:0,7 2:0,1 4294967295:0,4294967294 2:0,1 1:0,x 3:0,1,2",
                "TypeError TypeError TypeError TypeError TypeError 1:0,x TypeError",
                "'use strict';\n" + READ_ONLY_LENGTH);
    }

    @Test
    void indexBelowReadOnlyLength() {
        String script =
                "var a = [1, , 3]; Object.defineProperty(a, 'length', {writable: false});"
                        + " a[0] = 7; a[1] = 8;"
                        + " var o = Object.create(a); o[5] = 1;"
                        + " [a.join(), a.length, Object.keys(o).join(), Reflect.set(a, 3, 1)].join(' ')";
        Utils.assertGatedBehavior("7,8,3 3 5 true", "7,8,3 3 5 false", script);
        Utils.assertGatedBehavior("7,8,3 3 5 true", "7,8,3 3 5 false", "'use strict';\n" + script);
    }

    @Test
    void regExpReplaceStringLimit() {
        String big;
        try {
            big = "a".repeat(1 << 28);
        } catch (OutOfMemoryError e) {
            Assumptions.abort("not enough heap for a string of 2^28 characters");
            return;
        }
        // twice the string is past the limit: the pieces are counted before they are copied
        String script =
                "var re = /$/g, calls = 0;\n"
                        + "re.exec = function (str) {\n"
                        + "  if (calls++) return null;\n"
                        + "  var m = ['']; m.index = str.length; return m;\n"
                        + "};\n"
                        + "[() => s.replace(/$/g, () => s), () => s.replace(re, () => s)].map(f => {\n"
                        + "  try { return f().length; } catch (e) { return e.name + ': ' + e.message; }\n"
                        + "}).join()";
        for (int version : new int[] {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT}) {
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(version);
                        TopLevel scope = cx.initStandardObjects();
                        scope.put("s", scope, big);
                        Object res = cx.evaluateString(scope, script, "test.js", 1, null);
                        assertEquals(
                                "RangeError: Invalid string length,RangeError: Invalid string length",
                                res);
                        return null;
                    });
        }
    }

    private static final String ITERABLE =
            "function iterable(values, failing) {\n"
                    + "  var log = [], i = 0;\n"
                    + "  var it = {\n"
                    + "    next() {\n"
                    + "      log.push('next');\n"
                    + "      if (failing) throw new Error('step');\n"
                    + "      return i < values.length ? {value: values[i++], done: false}\n"
                    + "          : {value: undefined, done: true};\n"
                    + "    },\n"
                    + "    return() { log.push('return'); return {}; }\n"
                    + "  };\n"
                    + "  return {log: log, [Symbol.iterator]() { return it; }};\n"
                    + "}\n"
                    + "function run(f, values, failing) {\n"
                    + "  var src = iterable(values || [1], failing);\n"
                    + "  try { f(src); } catch (e) {}\n"
                    + "  return src.log.join('+');\n"
                    + "}\n"
                    + "var k = {};\n";

    private static final String CONSUMERS =
            "[run(it => new Set(it)), run(it => new Map(it), [[1, 1]]),"
                    + " run(it => new WeakSet(it), [k]), run(it => new WeakMap(it), [[k, 1]]),"
                    + " run(it => Array.from(it)), run(it => Object.fromEntries(it), [['a', 1]]),"
                    + " run(it => new Int8Array(it)), run(it => Int8Array.from(it)),"
                    + " run(it => Object.groupBy(it, x => x)), run(it => Map.groupBy(it, x => x))]"
                    + ".join()";

    @Test
    void exhaustedIteratorIsNotClosed() {
        String closed = "next+next+return";
        String notClosed = "next+next";
        Utils.assertGatedBehavior(
                String.join(",", java.util.Collections.nCopies(10, closed)),
                String.join(",", java.util.Collections.nCopies(10, notClosed)),
                ITERABLE + CONSUMERS);
    }

    @Test
    void failingIteratorIsNotClosed() {
        Utils.assertGatedBehavior(
                "next+return,next+return,next+return,next+return",
                "next,next,next,next",
                ITERABLE
                        + "[run(it => new Set(it), [1], true), run(it => new Map(it), [[1, 1]], true),"
                        + " run(it => Array.from(it), [1], true),"
                        + " run(it => Object.fromEntries(it), [['a', 1]], true)].join()");
    }

    @Test
    void iteratorIsClosedOnAbruptCompletion() {
        String script =
                ITERABLE
                        + "[run(it => new Map(it), [1, 2]), run(it => new WeakSet(it), [1, 2]),"
                        + " run(it => Array.from(it, () => { throw 1; }), [1, 2]),"
                        + " run(it => Object.fromEntries(it), [1, 2])].join()";
        String expected = "next+return,next+return,next+return,next+return";
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void regExpFlagsGetterReceiverType() {
        Utils.assertGatedBehavior(
                "ok,ok,object,gi",
                "number,string,undefined,gi",
                "var get = Object.getOwnPropertyDescriptor(RegExp.prototype, 'flags').get;\n"
                        + "[4, 'x', undefined].map(v => {\n"
                        + "  try { get.call(v); return 'ok'; }\n"
                        + "  catch (e) { return e.message.replace(/.*had type /, ''); }\n"
                        + "}).concat(get.call(/a/gi)).join()");
    }

    @Test
    void applyWithUnallocatableArrayLike() {
        String script =
                "function f() {}\n"
                        + "[() => f.apply(null, {length: 2 ** 31 - 1}),"
                        + " () => Reflect.apply(f, null, {length: 2 ** 31 - 1}),"
                        + " () => Reflect.construct(f, {length: 2 ** 31 - 1}),"
                        + " () => f.apply(null, {length: 2 ** 31 - 2})].map(g => {\n"
                        + "  try { g(); return 'ok'; } catch (e) { return e.name; }\n"
                        + "}).join()";
        String expected = "RangeError,RangeError,RangeError,RangeError";
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void deeplyNestedRegExpGroups() {
        String script =
                "var r;\n"
                        + "try { new RegExp('('.repeat(65537) + ')'.repeat(65537)); r = 'compiled'; }\n"
                        + "catch (e) { r = String(e instanceof RangeError || e instanceof SyntaxError); }\n"
                        + "r";
        Utils.assertGatedBehavior("true", "true", script);
    }

    private static final String LOGGING_PROXY =
            "function trapped(f) {\n"
                    + "  var log = [];\n"
                    + "  var p = new Proxy([1, 2, 3], {\n"
                    + "    has(t, k) { log.push('has ' + String(k)); return k in t; },\n"
                    + "    get(t, k) { log.push('get ' + String(k)); return t[k]; },\n"
                    + "    set(t, k, v) { log.push('set ' + String(k)); t[k] = v; return true; },\n"
                    + "    deleteProperty(t, k) { log.push('del ' + String(k)); return delete t[k]; }\n"
                    + "  });\n"
                    + "  f(p);\n"
                    + "  return log.join();\n"
                    + "}\n";

    @Test
    void spliceChecksTheElementsItMoves() {
        Utils.assertGatedBehavior(
                "get length,get 0,get 1,has 0,set 0,get 2,has 1,set 1,del 2,has length,set length",
                "get length,get constructor,has 0,get 0,has 1,get 1,set 0,has 2,get 2,set 1,del 2,"
                        + "set length",
                LOGGING_PROXY + "trapped(p => Array.prototype.splice.call(p, 0, 1))");
        Utils.assertGatedBehavior(
                "get length,get 2,has 3,set 3,get 1,has 2,set 2,get 0,has 1,set 1,has 0,set 0,"
                        + "has length,set length",
                "get length,get constructor,has 2,get 2,set 3,has 1,get 1,set 2,has 0,get 0,set 1,"
                        + "set 0,set length",
                LOGGING_PROXY + "trapped(p => Array.prototype.splice.call(p, 0, 0, 9))");
    }

    @Test
    void shiftAndUnshiftCheckTheElementsTheyMove() {
        Utils.assertGatedBehavior(
                "get length,get 0,get 1,has 0,set 0,get 2,has 1,set 1,del 2,has length,set length",
                "get length,get 0,has 1,get 1,set 0,has 2,get 2,set 1,del 2,set length",
                LOGGING_PROXY + "trapped(p => Array.prototype.shift.call(p))");
        Utils.assertGatedBehavior(
                "get length,get 2,has 3,set 3,get 1,has 2,set 2,get 0,has 1,set 1,has 0,set 0,"
                        + "has length,set length",
                "get length,has 2,get 2,set 3,has 1,get 1,set 2,has 0,get 0,set 1,set 0,set length",
                LOGGING_PROXY + "trapped(p => Array.prototype.unshift.call(p, 0))");
    }

    @Test
    void strictAssignmentToGlobalConst() {
        Utils.assertGatedBehavior(
                "TypeError: Cannot modify readonly property: k.",
                "TypeError: Assignment to constant variable \"k\".",
                "'use strict';\n"
                        + "const k = 1;\n"
                        + "var r;\n"
                        + "try { k = 2; r = 'assigned'; } catch (e) { r = e.name + ': ' + e.message; }\n"
                        + "r");
    }

    @Test
    void strictAssignmentToReadOnlyGlobalProperty() {
        String expected = "TypeError: Cannot modify readonly property: NaN.";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "'use strict';\n"
                        + "var r;\n"
                        + "try { NaN = 2; r = 'assigned'; } catch (e) { r = e.name + ': ' + e.message; }\n"
                        + "r");
    }
}
