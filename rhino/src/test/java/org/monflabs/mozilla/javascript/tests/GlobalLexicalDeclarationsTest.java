/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Context.EvaluationMethod;
import org.monflabs.mozilla.javascript.RhinoException;
import org.monflabs.mozilla.javascript.TopLevel;

/** Top-level let/const of scripts: global lexical declarations under VERSION_ECMASCRIPT. */
class GlobalLexicalDeclarationsTest {

    /** Runs the scripts in one realm and returns the last result, or the error name. */
    private static Object run(int version, EvaluationMethod method, String... scripts) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(version);
            cx.setEvaluationMethod(method);
            TopLevel scope = cx.initStandardObjects();
            Object result = null;
            for (String script : scripts) {
                try {
                    result = cx.evaluateString(scope, script, "test.js", 1, null);
                } catch (RhinoException e) {
                    return e.details().split(":")[0];
                }
            }
            return Context.toString(result);
        }
    }

    private static void assertGated(
            EvaluationMethod method, Object es6, Object ecma, String... scripts) {
        assertEquals(es6, run(Context.VERSION_ES6, method, scripts), "VERSION_ES6");
        assertEquals(ecma, run(Context.VERSION_ECMASCRIPT, method, scripts), "VERSION_ECMASCRIPT");
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void lexicalBindingsAreNotPropertiesOfTheGlobalObject(EvaluationMethod method) {
        assertGated(
                method,
                "true,1,true",
                "false,,false",
                "let x = 1; const c = 2;"
                        + " [('x' in globalThis), globalThis.x, Object.keys(globalThis).includes('c')]"
                        + ".join()");
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void assignmentToAGlobalConst(EvaluationMethod method) {
        assertGated(
                method,
                "c=2",
                "TypeError",
                "const c = 2; try { c = 3; } catch (e) { throw e; } 'c=' + c");
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void globalTemporalDeadZone(EvaluationMethod method) {
        assertGated(
                method, "undefined", "ReferenceError", "function f() { return y; } f(); let y;");
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void bindingsAreSharedByTheScriptsOfARealm(EvaluationMethod method) {
        assertGated(method, "3", "3", "let x = 1;", "x + 2");
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void redeclarationAcrossScripts(EvaluationMethod method) {
        assertGated(method, "ok", "SyntaxError", "let x = 1;", "let x = 2; 'ok'");
        assertGated(method, "ok", "SyntaxError", "var v = 1;", "let v = 2; 'ok'");
        assertGated(method, "ok", "SyntaxError", "let w = 1;", "var w = 2; 'ok'");
        // VERSION_ES6 already rejects it (TypeError: undefined is read-only)
        assertGated(method, "TypeError", "SyntaxError", "let undefined = 1; 'ok'");
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void evalCodeLetIsNotGlobal(EvaluationMethod method) {
        assertGated(method, "1", "1", "eval('let q = 1; q')");
    }
}
