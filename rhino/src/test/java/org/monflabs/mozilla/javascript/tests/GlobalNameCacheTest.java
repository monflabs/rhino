/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The cache of top-level names must give the result of a lookup whatever happens to them. */
class GlobalNameCacheTest {

    private static void assertAllVersions(Object expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void readsAndWritesInALoop() {
        assertAllVersions(
                "45,45",
                "var s = 0; for (var i = 0; i < 10; i++) { s += i; } [s, globalThis.s].join()");
    }

    @Test
    void deletedGlobalIsNoLongerFound() {
        assertAllVersions(
                "1,1,ReferenceError",
                "x = 1; var r = [];"
                        + "for (var i = 0; i < 3; i++) {"
                        + "  if (i == 2) delete globalThis.x;"
                        + "  try { r.push(x); } catch (e) { r.push(e.name); }"
                        + "}"
                        + "r.join()");
    }

    @Test
    void globalRedefinedAsAccessor() {
        assertAllVersions(
                "1,g,g,set 5",
                "y = 1; var r = [], log = '';"
                        + "for (var i = 0; i < 3; i++) {"
                        + "  if (i == 1) Object.defineProperty(globalThis, 'y', {"
                        + "    get: function() { return 'g'; },"
                        + "    set: function(v) { log = 'set ' + v; }, configurable: true });"
                        + "  r.push(y);"
                        + "}"
                        + "for (var j = 0; j < 3; j++) { y = 5; }"
                        + "r.push(log); r.join()");
    }

    @Test
    void getterRunsOncePerAccess() {
        assertAllVersions(
                "3,3",
                "var n = 0, m = 0;"
                        + "Object.defineProperty(globalThis, 'f', {"
                        + "  get: function() { n++; return 7; }, configurable: true });"
                        + "Object.defineProperty(globalThis, 'g', {"
                        + "  get: function() { m++; return function() {}; }, configurable: true });"
                        + "for (var i = 0; i < 3; i++) { try { f(); } catch (e) {} g(); }"
                        + "[n, m].join()");
    }

    @Test
    void getterReturningNullRunsOnce() {
        assertAllVersions(
                "2,true",
                "var n = 0;"
                        + "Object.defineProperty(globalThis, 'nil', {"
                        + "  get: function() { n++; return null; }, configurable: true });"
                        + "var r; for (var i = 0; i < 2; i++) { r = nil; }"
                        + "[n, r === null].join()");
    }

    @Test
    void incrementConvertsOnce() {
        assertAllVersions(
                "1,3",
                "var n = 0; var x = { valueOf: function() { n++; return 1; } };"
                        + "for (var i = 0; i < 2; i++) { x++; }"
                        + "[n, x].join()");
    }

    @Test
    void globalMapGrowing() {
        assertAllVersions(
                "6,6",
                "x = 0;"
                        + "for (var i = 0; i < 3; i++) {"
                        + "  if (i == 1) for (var k = 0; k < 2000; k++) globalThis['g' + k] = k;"
                        + "  x++; x = x + 1;"
                        + "}"
                        + "[x, globalThis.x].join()");
    }

    @Test
    void readOnlyGlobal() {
        assertAllVersions(
                "10,10,TypeError",
                "z = 1;"
                        + "for (var i = 0; i < 3; i++) {"
                        + "  if (i == 1) Object.defineProperty(globalThis, 'z', { writable: false });"
                        + "  z = i + 10;"
                        + "}"
                        + "var r = [z];"
                        + "for (var j = 0; j < 3; j++) { z++; }"
                        + "r.push(z);"
                        + "try { (function() { 'use strict'; z = 3; })(); } catch (e) { r.push(e.name); }"
                        + "r.join()");
    }

    @Test
    void globalsReadFromFunctions() {
        assertAllVersions(
                "10,ReferenceError",
                "h = 1;"
                        + "function f() { var s = 0; for (var i = 0; i < 5; i++) { s += h; } return s; }"
                        + "var r = [f() + f()];"
                        + "delete globalThis.h;"
                        + "try { f(); } catch (e) { r.push(e.name); }"
                        + "r.join()");
    }

    @Test
    void withStatementStillWins() {
        assertAllVersions(
                "1,2,2",
                "var w = 1, r = [];"
                        + "var o = { w: 2 };"
                        + "for (var i = 0; i < 3; i++) {"
                        + "  if (i == 0) { r.push(w); } else { with (o) { r.push(w); } }"
                        + "}"
                        + "r.join()");
    }

    @Test
    void temporalDeadZoneOfGlobalLet() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError,ReferenceError,1,1",
                "var r = [];"
                        + "function read() { return t; }"
                        + "for (var i = 0; i < 2; i++) {"
                        + "  try { r.push(read()); } catch (e) { r.push(e.name); }"
                        + "}"
                        + "let t = 1;"
                        + "for (var j = 0; j < 2; j++) { r.push(read()); }"
                        + "r.join()");
    }

    @Test
    void oneScriptRunInTwoScopes() {
        Utils.runWithAllModes(
                cx -> {
                    Script script =
                            cx.compileString(
                                    "var r = 0; for (var i = 0; i < 3; i++) { r += v; } r",
                                    "test.js",
                                    1,
                                    null);
                    TopLevel one = cx.initStandardObjects();
                    TopLevel two = cx.initStandardObjects();
                    cx.evaluateString(one, "var v = 1;", "one.js", 1, null);
                    cx.evaluateString(two, "var v = 2;", "two.js", 1, null);
                    assertEquals(3, ((Number) script.exec(cx, one, one)).intValue());
                    assertEquals(6, ((Number) script.exec(cx, two, two)).intValue());
                    assertEquals(3, ((Number) script.exec(cx, one, one)).intValue());
                    return null;
                });
    }

    @Test
    void globalLetShadowsAConfigurableGlobal() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    Script read =
                            cx.compileString(
                                    "var r; for (var i = 0; i < 2; i++) { r = w; } r",
                                    "read.js",
                                    1,
                                    null);
                    cx.evaluateString(scope, "w = 1;", "a.js", 1, null);
                    assertEquals(1, ((Number) read.exec(cx, scope, scope)).intValue());
                    cx.evaluateString(scope, "let w = 2;", "b.js", 1, null);
                    assertEquals(2, ((Number) read.exec(cx, scope, scope)).intValue());
                    return null;
                });
    }

    @Test
    void sealedGlobalRejectsWrites() {
        Utils.runWithAllModes(
                cx -> {
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(scope, "var q = 0;", "a.js", 1, null);
                    Script write =
                            cx.compileString(
                                    "for (var i = 0; i < 2; i++) { q = i; } q",
                                    "write.js",
                                    1,
                                    null);
                    assertEquals(1, ((Number) write.exec(cx, scope, scope)).intValue());
                    scope.getGlobalThis().sealObject();
                    assertThrows(EvaluatorException.class, () -> write.exec(cx, scope, scope));
                    return null;
                });
    }

    @Test
    void writesOfAGlobalLet() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "6,3,4,7",
                "let n = 0, m = 0;"
                        + "function inc() { n++; }"
                        + "function add(k) { m += k; }"
                        + "for (var i = 0; i < 3; i++) { inc(); add(1); n = n + 1; }"
                        + "var r = [n, m];"
                        + "for (var j = 0; j < 2; j++) { --n; m = m + 2; }"
                        + "r.push(n, m); r.join()");
    }

    @Test
    void writesOfAGlobalConstThrowAfterTheCacheIsWarm() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError,TypeError,TypeError,1",
                "const c = 1; let r = [];"
                        + "function w() { c = 2; }"
                        + "function u() { c++; }"
                        + "for (var i = 0; i < 2; i++) { try { w(); } catch (e) { r.push(e.name); } }"
                        + "try { u(); } catch (e) { r.push(e.name); }"
                        + "r.push(c); r.join()");
    }

    @Test
    void writesOfAGlobalLetInItsTemporalDeadZone() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError,ReferenceError,5,6",
                "var r = [];"
                        + "function w() { z = 5; }"
                        + "function u() { z++; }"
                        + "try { w(); } catch (e) { r.push(e.name); }"
                        + "try { u(); } catch (e) { r.push(e.name); }"
                        + "let z = 0;"
                        + "for (var i = 0; i < 2; i++) w();"
                        + "r.push(z); u(); r.push(z); r.join()");
    }

    @Test
    void globalLetWrittenFromAnotherScript() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(scope, "let g = 0;", "a.js", 1, null);
                    Script write =
                            cx.compileString(
                                    "for (var i = 0; i < 3; i++) { g++; g = g + 1; } g",
                                    "write.js",
                                    1,
                                    null);
                    assertEquals(6, ((Number) write.exec(cx, scope, scope)).intValue());
                    assertEquals(12, ((Number) write.exec(cx, scope, scope)).intValue());
                    assertEquals(
                            "undefined",
                            cx.evaluateString(scope, "typeof globalThis.g", "c.js", 1, null));
                    return null;
                });
    }

    @Test
    void globalsReadFromNestedFunctions() {
        assertAllVersions(
                "3,v,true,undefined,v,3",
                "var gv = 'v';"
                        + "function sloppy() { return this === globalThis; }"
                        + "function strict() { 'use strict'; return this; }"
                        + "(function () {"
                        + "  var r = [];"
                        + "  function read() { return [Math.abs(-3), gv, sloppy(), strict()]; }"
                        + "  for (var i = 0; i < 3; i++) r = read();"
                        + "  r.push((function () { return (function () { return gv; })(); })());"
                        + "  r.push((function () { var x = 1; return function () { return Math.abs(-3); }; })()());"
                        + "  return r.join();"
                        + "})()");
    }

    @Test
    void nestedLookupSeesTheScopesOnTheWay() {
        assertAllVersions(
                "g,e,g,g,b,c,g,g,w,g",
                "var v = 'g';"
                        + "function outer(withEval) {"
                        + "  if (withEval) eval(\"var v = 'e'\");"
                        + "  return function () { return v; };"
                        + "}"
                        + "var r = [outer(false)(), outer(true)(), outer(false)()];"
                        + "function scopes() {"
                        + "  var read = function () { return v; };"
                        + "  r.push(read());"
                        + "  { let v = 'b'; r.push((function () { return v; })()); }"
                        + "  try { throw 'c'; } catch (v) { r.push((function () { return v; })()); }"
                        + "  r.push(read());"
                        + "}"
                        + "scopes();"
                        + "function inWith(o) { with (o) { return (function () { return v; })(); } }"
                        + "r.push(inWith({}), inWith({ v: 'w' }), inWith({}));"
                        + "r.join()");
    }

    @Test
    void nestedReadOfADeletedGlobal() {
        assertAllVersions(
                "1,1,ReferenceError,2",
                "x = 1;"
                        + "var f = (function () {"
                        + "  return function () { try { return x; } catch (e) { return e.name; } };"
                        + "})();"
                        + "var r = [];"
                        + "for (var i = 0; i < 3; i++) { if (i == 2) delete globalThis.x; r.push(f()); }"
                        + "x = 2; r.push(f());"
                        + "r.join()");
    }

    @Test
    void nestedReadOfAGlobalLetInItsTemporalDeadZone() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError,1,1",
                "var f = (function () {"
                        + "  return function () { try { return gl; } catch (e) { return e.name; } };"
                        + "})();"
                        + "var r = [f()];"
                        + "let gl = 1;"
                        + "r.push(f(), f());"
                        + "r.join()");
    }

    @Test
    void nestedFunctionRunInTwoScopes() {
        Utils.runWithAllModes(
                cx -> {
                    Script script =
                            cx.compileString(
                                    "(function () { var r = 0;"
                                            + " function add() { r += v; }"
                                            + " for (var i = 0; i < 3; i++) add(); return r; })()",
                                    "test.js",
                                    1,
                                    null);
                    TopLevel one = cx.initStandardObjects();
                    TopLevel two = cx.initStandardObjects();
                    cx.evaluateString(one, "var v = 1;", "one.js", 1, null);
                    cx.evaluateString(two, "var v = 2;", "two.js", 1, null);
                    assertEquals(3, ((Number) script.exec(cx, one, one)).intValue());
                    assertEquals(6, ((Number) script.exec(cx, two, two)).intValue());
                    assertEquals(3, ((Number) script.exec(cx, one, one)).intValue());
                    return null;
                });
    }
}
