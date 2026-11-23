/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Defects of calls, eval code and assignments found by the review of 2026-11-22. */
class ReviewDefectsRuntimeTest {

    private static final String STRICT_CALLBACK =
            "var s = [];\n"
                    + "function f() { 'use strict'; s.push(this === null ? 'null' : typeof this);"
                    + " return true; }\n";

    private static final String SLOPPY_CALLBACK =
            "var s = [];\n"
                    + "function f() { s.push(this === globalThis ? 'global' : typeof this);"
                    + " return true; }\n";

    private static final String ARRAY_METHODS =
            "['map', 'forEach', 'find', 'findLast', 'findIndex', 'findLastIndex', 'every', 'some',"
                    + " 'filter', 'flatMap'].forEach(function (m) { [1][m](f, 1); });\n";

    private static final String TYPED_ARRAY_METHODS =
            "['map', 'forEach', 'find', 'findLast', 'findIndex', 'findLastIndex', 'every', 'some',"
                    + " 'filter'].forEach(function (m) { new Int8Array(1)[m](f, 's'); });\n";

    @Test
    void primitiveThisArgOfArrayMethods() {
        Utils.assertGatedBehavior(
                "object,object,object,object,object,object,object,object,object,object",
                "number,number,number,number,number,number,number,number,number,number",
                STRICT_CALLBACK + ARRAY_METHODS + "s.join()");
        Utils.assertGatedBehavior(
                "object,object,object,object,object,object,object,object,object",
                "string,string,string,string,string,string,string,string,string",
                STRICT_CALLBACK + TYPED_ARRAY_METHODS + "s.join()");
    }

    @Test
    void primitiveThisArgOfFromAndForEach() {
        Utils.assertGatedBehavior(
                "object,object,object",
                "string,boolean,symbol",
                STRICT_CALLBACK
                        + "Array.from([1], f, 's'); Array.from({length: 1}, f, true);\n"
                        + "new Map([[1, 1]]).forEach(f, Symbol());\n"
                        + "s.join()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "string,object",
                STRICT_CALLBACK
                        + "Int8Array.from([1], f, 's');\n"
                        + "Int8Array.from([1], function () { s.push(typeof this); }, 's');\n"
                        + "s.join()");
    }

    @Test
    void primitiveThisOfReflectApplyAndBind() {
        Utils.assertGatedBehavior(
                "object,object,object,object",
                "boolean,number,string,bigint",
                STRICT_CALLBACK
                        + "Reflect.apply(f, true, []); f.bind(1)(); f.bind('s', 1).bind(2)();\n"
                        + "new (function () {})().constructor.prototype.constructor.call.call("
                        + "f.bind(1n));\n"
                        + "s.join()");
    }

    @Test
    void nullAndUndefinedThisArg() {
        Utils.assertGatedBehavior(
                "object,object,object,object,null",
                "null,undefined,null,undefined,null",
                STRICT_CALLBACK
                        + "[1].map(f, null); [1].map(f, undefined);\n"
                        + "Reflect.apply(f, null, []); Reflect.apply(f, undefined, []);\n"
                        + "f.bind(null)();\n"
                        + "s.join()");
    }

    @Test
    void sloppyCallbacksStillReceiveAnObject() {
        String script =
                SLOPPY_CALLBACK
                        + ARRAY_METHODS
                        + TYPED_ARRAY_METHODS
                        + "Array.from([1], f, 's'); new Map([[1, 1]]).forEach(f, 1);\n"
                        + "Reflect.apply(f, true, []); f.bind(1)();\n"
                        + "[1].map(f, null); [1].forEach(f); f.bind(null)(); f.bind()();\n"
                        + "s.join()";
        String objects = "object,".repeat(23);
        Utils.assertGatedBehavior(
                objects + "global,global,global,global",
                objects + "global,global,global,global",
                script);
    }

    @Test
    void indirectEvalFromAStrictScriptIsSloppy() {
        String script =
                "'use strict';\n"
                        + "var o = Object.freeze({a: 1}), r = [];\n"
                        + "function t(src) {\n"
                        + "  try { r.push(String((0, eval)(src))); } catch (e) { r.push(e.name); }\n"
                        + "}\n"
                        + "t('o.a = 2; o.a'); t('NaN = 1; 5'); t('delete o.a');\n"
                        + "t('undeclaredByEval = 1; undeclaredByEval');\n"
                        + "t('\"use strict\"; o.a = 2;');\n"
                        + "try { o.a = 3; r.push('silent'); } catch (e) { r.push(e.name); }\n"
                        + "r.join()";
        Utils.assertGatedBehavior(
                "TypeError,TypeError,TypeError,ReferenceError,TypeError,TypeError",
                "1,5,false,1,TypeError,TypeError",
                script);
    }

    @Test
    void evalCodeWithItsOwnDirectiveIsStrictAtRunTime() {
        String script =
                "var o = Object.freeze({a: 1}), r = [];\n"
                        + "function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }\n"
                        + "t(function () { return eval('\"use strict\"; o.a = 2; o.a'); });\n"
                        + "t(function () { return (0, eval)('\"use strict\"; o.a = 2; o.a'); });\n"
                        + "t(function () { eval('\"use strict\"; 1'); o.a = 2; return o.a; });\n"
                        + "t(function () { 'use strict'; (0, eval)('1'); o.a = 2; });\n"
                        + "t(function () { 'use strict';"
                        + " try { (0, eval)('throw 1'); } catch (e) {} o.a = 2; });\n"
                        + "r.join()";
        Utils.assertGatedBehavior(
                "1,1,1,TypeError,TypeError", "TypeError,TypeError,1,TypeError,TypeError", script);
    }

    private static final String OPTIONAL_CALLS =
            "var u, z = null, o = {f() { return 1; }, 0: function () { return 2; }}, r = [];\n"
                    + "function t(src) {\n"
                    + "  try { r.push(String(eval(src))); } catch (e) { r.push(e.name); }\n"
                    + "}\n";

    @Test
    void optionalCallOnAMemberOfANullBase() {
        Utils.assertGatedBehavior(
                "TypeError,TypeError,undefined,undefined,undefined,TypeError,TypeError",
                "TypeError,TypeError,TypeError,TypeError,TypeError,TypeError,TypeError",
                OPTIONAL_CALLS
                        + "['u[0]?.()', 'null[\"k\"]?.()', 'u.k?.()', 'z.k?.()', 'o.a.b?.()',"
                        + " 'o.a[0]?.()', 'u[Symbol.iterator]?.()'].forEach(t);\n"
                        + "r.join()");
        Utils.assertGatedBehavior(
                "undefined", "TypeError", OPTIONAL_CALLS + "t('u.k?.(r.push(1))'); r.join()");
    }

    @Test
    void optionalCallCoveredByAnOptionalChain() {
        Utils.assertGatedBehavior(
                "undefined,TypeError,undefined,undefined,undefined,undefined,1,2,undefined",
                "undefined,undefined,undefined,undefined,undefined,undefined,1,2,undefined",
                OPTIONAL_CALLS
                        + "['u?.k?.()', 'u?.[0]?.()', 'u?.k()', 'o.k?.()', 'o[1]?.()', '(u?.k)?.()',"
                        + " 'o.f?.()', 'o?.[0]?.()', 'u?.()'].forEach(t);\n"
                        + "r.join()");
        // the key and the arguments are not evaluated
        Utils.assertGatedBehavior(
                "1,TypeError",
                "undefined",
                OPTIONAL_CALLS + "t('u?.[r.push(1)]?.(r.push(2))'); r.join()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "undefined,TypeError,undefined",
                OPTIONAL_CALLS
                        + "class C {\n"
                        + "  #p;\n"
                        + "  static covered(x) { return x?.#p?.(); }\n"
                        + "  static bare(x) { return x.#p?.(); }\n"
                        + "}\n"
                        + "t('C.covered(u)'); t('C.bare(u)'); t('C.bare(new C())');\n"
                        + "r.join()");
    }

    private static final String COUNTING_KEY =
            "var r = [];\n"
                    + "function key() {\n"
                    + "  var k = {count: 0, toString() { this.count++; return 'p'; }};\n"
                    + "  return k;\n"
                    + "}\n";

    @Test
    void logicalAssignmentConvertsTheKeyOnce() {
        Utils.assertGatedBehavior(
                "2:2:2,2:2:2,2:2:2,2:0:0,2:3:3",
                "1:2:2,1:2:2,1:2:2,1:0:0,1:3:3",
                COUNTING_KEY
                        + "var k, o, v;\n"
                        + "k = key(); o = {p: 1}; v = (o[k] &&= 2); r.push(k.count + ':' + o.p + ':' + v);\n"
                        + "k = key(); o = {p: 0}; v = (o[k] ||= 2); r.push(k.count + ':' + o.p + ':' + v);\n"
                        + "k = key(); o = {}; v = (o[k] ??= 2); r.push(k.count + ':' + o.p + ':' + v);\n"
                        + "k = key(); o = {p: 0}; v = (o[k] &&= 2); r.push(k.count + ':' + o.p + ':' + v);\n"
                        + "k = key(); o = {p: 1}; v = (o[k] += 2); r.push(k.count + ':' + o.p + ':' + v);\n"
                        + "r.join()");
        // the TypeError of a null base comes before the conversion of the key
        Utils.assertGatedBehavior(
                "TypeError:1",
                "TypeError:0",
                COUNTING_KEY
                        + "var k = key(), o = null;\n"
                        + "try { o[k] ||= 2; } catch (e) { r.push(e.name + ':' + k.count); }\n"
                        + "r.join()");
    }

    @Test
    void logicalAssignmentOfElementsKeepsItsResults() {
        String script =
                "var a = [0, 1], s = Symbol(), o = {};\n"
                        + "for (var i = 0; i < 2; i++) a[i] ||= 7;\n"
                        + "a['1'] &&= 9; a[2] ??= 3; o[s] ??= 4; o[1.5] ||= 5;\n"
                        + "function* g() { var t = {}; t[yield 1] ??= yield 2; return t.z; }\n"
                        + "var it = g(); it.next(); it.next('z');\n"
                        + "[a.join(), o[s], o['1.5'], it.next(6).value].join(';')";
        Utils.assertGatedBehavior("7,9,3;4;5;6", "7,9,3;4;5;6", script);
    }

    private static final String WITH_PROXY =
            "function run(src) {\n"
                    + "  var log = [];\n"
                    + "  var proxy = new Proxy({p: 0, q: 1}, {\n"
                    + "    has(t, k) { log.push('has'); return k === 'p' || k === 'q'; },\n"
                    + "    get(t, k) {\n"
                    + "      log.push(typeof k === 'symbol' ? 'get @@unscopables' : 'get ' + k);\n"
                    + "      return t[k];\n"
                    + "    },\n"
                    + "    set(t, k, v) { log.push('set ' + k); t[k] = v; return true; }\n"
                    + "  });\n"
                    + "  var v = Function('proxy', 'with (proxy) { return ' + src + '; }')(proxy);\n"
                    + "  return log.join(', ') + ' = ' + v;\n"
                    + "}\n";

    @Test
    void logicalAssignmentInWithResolvesTheNameOnce() {
        Utils.assertGatedBehavior(
                "has, get p, has, set p = 1",
                "has, get @@unscopables, has, get p, has, set p = 1",
                WITH_PROXY + "run('p ||= 1')");
        Utils.assertGatedBehavior(
                "has, get q, has, set q = 5",
                "has, get @@unscopables, has, get q, has, set q = 5",
                WITH_PROXY + "run('q &&= 5')");
        Utils.assertGatedBehavior(
                "has, get p, has, set p = 0",
                "has, get @@unscopables, has, get p = 0",
                WITH_PROXY + "run('p ??= 1')");
        Utils.assertGatedBehavior(
                "has, get p, has, set p = 1",
                "has, get @@unscopables, has, get p, has, set p = 1",
                WITH_PROXY + "run('p += 1')");
    }

    @Test
    void logicalAssignmentInWithOfOuterNames() {
        String script =
                "var r = [];\n"
                        + "function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }\n"
                        + "t(function () { var x = 0; with ({}) { x ||= 4; x &&= x + 1; x ??= 9; }"
                        + " return x; });\n"
                        + "t(function () { with ({}) { undeclaredName ||= 4; } });\n"
                        + "t(function () { var fn; with ({}) { fn ||= function () {}; }"
                        + " return fn.name; });\n"
                        + "t(function () { var x = 0; (function () { with ({}) { x ||= 4; } })();"
                        + " return x; });\n"
                        + "t(function () { var o = {p: 0}; with (o) { p ||= (delete o.p, 3); }"
                        + " return JSON.stringify(o); });\n"
                        + "r.join()";
        String expected = "5,ReferenceError,fn,4,{\"p\":3}";
        Utils.assertGatedBehavior(expected, expected, script);
        Utils.assertGatedBehavior(
                "4",
                "ReferenceError",
                "(function () { try { with ({}) { x ??= 4; } let x; return String(x); }"
                        + " catch (e) { return e.name; } })()");
    }
}
