/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Tests for correct block-scoping of {@code const} and fix of the dead {@code if} guard. */
class ConstLetScopingTest {

    // --- const block-scoping in ES6 ---

    @Test
    void constInBlockIsBlockScoped() {
        // const inside an explicit block must not be visible outside it
        Utils.assertWithAllModes_ES6(42.0, "var result; { const x = 42; result = x; } result;");
    }

    @Test
    void constShadowingOuterConstInInnerBlockIsAllowed() {
        // inner const x shadows outer const x — must not produce a redeclaration error
        Utils.assertWithAllModes_ES6(2.0, "const x = 1; { const x = 2; x; }");
    }

    @Test
    void constRedeclarationInSameScopeIsError() {
        // Two const declarations for the same name in the same scope must be an error
        Utils.assertEvaluatorExceptionES6(
                "redeclaration of const \"x\"", "const x = 1; const x = 2;");
    }

    @Test
    void varRedeclarationAcrossBlocksIsAllowed() {
        // var hoists to function scope, so redeclaring in a nested block must be fine
        Utils.assertWithAllModes_ES6(2.0, "var x = 1; { var x = 2; } x;");
    }

    @Test
    void varRedeclarationOfParamIsAllowed() {
        // var hoists to function scope, so redeclaring in a nested block must be fine
        Utils.assertWithAllModes_ES6(3.0, "(function f(a) { var a; return a; })(3)");
        Utils.assertWithAllModes_ES6(3.0, "(function f(a) { 'use strict'; var a; return a; })(3)");
    }

    // --- const re-evaluated in a loop body creates a new binding each time ---

    @Test
    void constInLoopBodyTakesEachIterationsValue() {
        // function without activation: the const lives in a register (initialized once before)
        Utils.assertGatedBehavior(
                "0,0,0",
                "0,1,2",
                "(function () { var r = []; for (var i = 0; i < 3; i++) { const c = i; r.push(c); }"
                        + " return r.join(); })()");
        // numeric const in a register
        Utils.assertGatedBehavior(
                0.0,
                3.0,
                "(function () { var s = 0; for (var i = 0; i < 3; i++) { const c = i * 1; s += c; }"
                        + " return s; })()");
        // function with activation (nested closure)
        Utils.assertWithAllModes_ES6(
                "0,1,2",
                "(function () { var r = []; for (var i = 0; i < 3; i++) { const c = i;"
                        + " r.push(function () { return c; }); }"
                        + " return r.map(function (f) { return f(); }).join(); })()");
        // script top level
        Utils.assertWithAllModes_ES6(
                "0,1,2",
                "var r = []; for (var i = 0; i < 3; i++) { const c = i; r.push(c); } r.join()");
    }

    @Test
    void legacyConstKeepsItsFirstValue() {
        // JavaScript 1.x const is function-scoped and only initialized once
        Utils.assertWithAllModes(
                Context.VERSION_1_8,
                null,
                "0,0,0",
                "(function () { var r = []; for (var i = 0; i < 3; i++) { const c = i; r.push(c); }"
                        + " return r.join(); })()");
    }

    // --- per-iteration bindings of let/const loop heads ---

    private static void assertPerIteration(String expectedES6, String expected, String script) {
        Utils.assertGatedBehavior(expectedES6, expected, script);
        // the same code inside a function with an activation (it creates closures)
        Utils.assertGatedBehavior(
                expectedES6, expected, "(function () { return eval(" + quote(script) + "); })()");
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static final String CALL_ALL = "fs.map(function (f) { return f(); }).join()";

    @Test
    void forLetClosuresCaptureEachIteration() {
        assertPerIteration(
                "3,3,3",
                "0,1,2",
                "var fs = []; for (let i = 0; i < 3; i++) { fs.push(function () { return i; }); } "
                        + CALL_ALL);
    }

    @Test
    void forLetWithContinueStillCopiesBeforeTheIncrement() {
        assertPerIteration(
                "3,3,3",
                "0,1,2",
                "var fs = []; for (let i = 0; i < 3; i++) {"
                        + " fs.push(function () { return i; }); if (i < 3) { let z = i; continue; } } "
                        + CALL_ALL);
    }

    @Test
    void closureInForLetInitSeesTheFirstIterationsValue() {
        assertPerIteration(
                "3",
                "0",
                "var g; for (let i = 0, f = function () { return i; }; i < 3; i++) { g = f; } '' + g()");
    }

    @Test
    void forInAndForOfLetCreateABindingPerIteration() {
        assertPerIteration(
                "c,c,c",
                "a,b,c",
                "var fs = []; for (let k in {a: 1, b: 2, c: 3}) { fs.push(function () { return k; }); } "
                        + CALL_ALL);
        assertPerIteration(
                "3,3,3",
                "1,2,3",
                "var fs = []; for (const v of [1, 2, 3]) { fs.push(function () { return v; }); } "
                        + CALL_ALL);
    }

    @Test
    void forLetInFunctionWithoutClosures() {
        // bindings in registers: no per-iteration copy, but names must still resolve
        Utils.assertWithAllModes_ES6(
                4.0,
                "(function () { var s = 0; for (let i = 0; i < 2; i++) {"
                        + " for (let j = 0; j < 2; j++) { let r = i + j; s += r; } } return s; })()");
        // a for-of const head was not initialized in a register before
        Utils.assertGatedBehavior(
                Double.NaN,
                6.0,
                "(function () { var s = 0; for (const v of [1, 2, 3]) { let w = v; s += w; }"
                        + " return s; })()");
    }

    @Test
    void varLoopsStillShareOneBinding() {
        assertPerIteration(
                "3,3,3",
                "3,3,3",
                "var fs = []; for (var i = 0; i < 3; i++) { fs.push(function () { return i; }); } "
                        + CALL_ALL);
    }

    // --- assignment to a const: no effect under VERSION_ES6, TypeError under VERSION_ECMASCRIPT
    // ---

    private static final String[] CONST_ASSIGNMENTS = {
        "k = 2", "k += 1", "k++", "--k", "[k] = [2]", "({k} = {k: 2})", "for (k of [2]) {}"
    };

    private static void assertConstAssignment(String wrap) {
        assertConstAssignment(wrap, new String[] {"k=1", "k=1", "k=1", "k=1", "k=1", "k=1", "k=1"});
    }

    private static void assertConstAssignment(String wrap, String[] expectedES6) {
        for (int i = 0; i < CONST_ASSIGNMENTS.length; i++) {
            String script =
                    wrap.replace(
                            "ASSIGN",
                            "try { "
                                    + CONST_ASSIGNMENTS[i]
                                    + "; } catch (e) { return e.name; } return 'k=' + k;");
            Utils.assertGatedBehavior(expectedES6[i], "TypeError", script);
        }
    }

    @Test
    void constAssignmentInRegisters() {
        assertConstAssignment("(function () { const k = 1; ASSIGN })()");
    }

    @Test
    void constAssignmentInABlockScope() {
        // before VERSION_ECMASCRIPT, a const in a block scope object is an ordinary binding
        assertConstAssignment(
                "(function () { { const k = 1; (function () {}); ASSIGN } })()",
                new String[] {"k=2", "k=2", "k=2", "k=0", "k=2", "k=2", "k=2"});
    }

    @Test
    void constAssignmentInAnActivation() {
        assertConstAssignment(
                "(function () { const k = 1; var f = function () { return k; }; ASSIGN })()");
    }

    @Test
    void constAssignmentFromAClosure() {
        assertConstAssignment(
                "(function () { const k = 1; return (function () { ASSIGN })(); })()");
    }

    @Test
    void constAssignmentInAScriptBlock() {
        Utils.assertGatedBehavior(
                "k=2",
                "TypeError",
                "var r; { const k = 1; try { k = 2; r = 'k=' + k; } catch (e) { r = e.name; } } r");
    }

    @Test
    void assignmentToReadOnlyGlobalsStaysSilent() {
        Utils.assertGatedBehavior(
                true, true, "undefined = 1; NaN = 2; undefined === void 0 && isNaN(NaN)");
    }

    // --- temporal dead zone: ReferenceError under VERSION_ECMASCRIPT, unchanged under VERSION_ES6
    // ---

    private static void assertTdz(String body) {
        Utils.assertGatedBehavior(
                "no TDZ",
                "ReferenceError",
                "(function () { try { "
                        + body
                        + " } catch (e) { return e.name; } return 'no TDZ'; })()");
    }

    @Test
    void tdzInTheSameFunction() {
        assertTdz("x; let x = 1;");
        assertTdz("typeof x; let x = 1;");
        assertTdz("let x = x;");
        assertTdz("x = 2; let x = 1;");
        assertTdz("x++; let x = 1;");
        assertTdz("{ y; let y = 1; }");
        assertTdz("c; const c = 1;");
    }

    @Test
    void tdzInAForOfHead() {
        // VERSION_ES6 already throws here (a TypeError: the inner a is undefined)
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError",
                "(function () { var a = [1]; try { for (let a of a) {} } catch (e) { return e.name; } })()");
    }

    @Test
    void tdzThroughAClosure() {
        assertTdz("function f() { return x; } f(); let x = 1;");
        assertTdz("function f() { x = 3; } f(); let x = 1;");
        assertTdz("function f() { x++; } f(); let x = 1;");
        assertTdz("function f() { return typeof x; } f(); let x = 1;");
        assertTdz("function f() { return c; } f(); const c = 1;");
        assertTdz("{ let f = function () { return y; }; f(); let y = 1; }");
    }

    @Test
    void noTdzOnceInitialized() {
        Utils.assertGatedBehavior(
                3.0,
                3.0,
                "(function () { let x = 1; function f() { return x; } { let y = 2; x += y; } return f(); })()");
    }

    @Test
    void letWithoutInitializerIsUndefinedOnEachIteration() {
        Utils.assertGatedBehavior(
                "1,1",
                "1,",
                "(function () { var r = []; for (var i = 0; i < 2; i++) { let x; if (i == 0) x = 1;"
                        + " r.push(x); } return r.join(); })()");
    }

    // --- single-statement const/let errors in ES6 ---

    @Test
    void constInSingleStatementForBodyIsError() {
        Utils.assertEvaluatorExceptionES6(
                "const declarations may only appear at the top level or within a block",
                "for (;;) const x = 1;");
    }

    @Test
    void letInSingleStatementIfBodyIsError() {
        Utils.assertEvaluatorExceptionES6(
                "let declaration not directly within block", "if (true) let x = 1;");
    }

    @Test
    void constInSingleStatementIfBodyIsError() {
        Utils.assertEvaluatorExceptionES6(
                "const declarations may only appear at the top level or within a block",
                "if (true) const x = 1;");
    }

    @Test
    void letInSingleStatementElseBodyIsError() {
        Utils.assertEvaluatorExceptionES6(
                "let declaration not directly within block", "if (false) 0; else let x = 1;");
    }

    @Test
    void constInSingleStatementElseBodyIsError() {
        Utils.assertEvaluatorExceptionES6(
                "const declarations may only appear at the top level or within a block",
                "if (false) 0; else const x = 1;");
    }

    @Test
    void letInsideBlockBodyOfIfIsAllowed() {
        Utils.assertWithAllModes_ES6(1.0, "if (true) { let x = 1; x; }");
    }

    @Test
    void constInsideBlockBodyOfIfIsAllowed() {
        Utils.assertWithAllModes_ES6(1.0, "if (true) { const x = 1; x; }");
    }

    // --- pre-ES6 regression: no new errors at VERSION_1_8 ---

    @Test
    void constInSingleStatementIfBodyIsAllowedPreES6() {
        // In pre-ES6 mode the parser must not reject this
        Utils.assertEvaluatorException_1_8(
                "redeclaration of const \"x\"", "const x = 1; const x = 2;");
    }

    @Test
    void constShadowingIsErrorPreES6() {
        // Pre-ES6: const is function-scoped, so inner const x is a redeclaration error
        Utils.assertEvaluatorException_1_8(
                "redeclaration of const \"x\"", "const x = 1; { const x = 2; }");
    }

    // --- block-scoped function declarations in strict mode ---

    @Test
    void blockScopedFunctionInStrictModeNotVisibleOutside() {
        // In strict mode, a function declared inside a block should not be visible outside it
        Utils.assertWithAllModes_ES6(
                "undefined", "'use strict'; { function f() { return 1; } } typeof f;");
    }

    @Test
    void blockScopedFunctionInStrictModeVisibleInside() {
        // In strict mode, a function declared inside a block should be callable within the block
        Utils.assertWithAllModes_ES6(
                1.0,
                "'use strict'; var result; { function f() { return 1; } result = f(); } result;");
    }

    @Test
    void topLevelFunctionInStrictModeStillHoists() {
        // Top-level function declarations in strict mode should still hoist normally
        Utils.assertWithAllModes_ES6(1.0, "'use strict'; function f() { return 1; } f();");
    }

    @Test
    void blockScopedFunctionShadowsOuterInStrictMode() {
        // A block-scoped function should shadow an outer function within the block
        Utils.assertWithAllModes_ES6(
                2.0,
                "'use strict'; function f() { return 1; } var result; { function f() { return 2; } result = f(); } result;");
    }

    @Test
    void outerFunctionUnchangedAfterBlockInStrictMode() {
        // The outer function should be unaffected after the block exits
        Utils.assertWithAllModes_ES6(
                1.0,
                "'use strict'; function f() { return 1; } { function f() { return 2; } } f();");
    }
}
