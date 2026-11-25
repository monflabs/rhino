/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.monflabs.mozilla.javascript.ast.AstRoot;

/** {@link AstRoot#hasNoLexicalBindings}, with which the temporal dead zone checks are skipped. */
class LexicalBindingsFlagTest {

    private static AstRoot parse(String source) {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ECMASCRIPT);
        AstRoot root = new Parser(env).parse(source, "test", 1);
        new IRFactory(env, "test", source, env.getErrorReporter()).transformTree(root);
        return root;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "let x;",
                "const x = 1;",
                "class C {}",
                "function f() { let x; }",
                "var f = () => { const x = 1; };",
                "{ let [a] = []; }",
                "for (let i = 0;;) break;",
                "for (const k in {});",
                "switch (1) { case 1: let x; }",
                "class C { static { let x; } }",
                "var o = { m(a = () => { let y; }) {} };"
            })
    void bindings(String source) {
        assertFalse(parse(source).hasNoLexicalBindings());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "var x = y;", "function f(a, b) { return a + b; }"})
    void noBindings(String source) {
        assertTrue(parse(source).hasNoLexicalBindings());
    }

    @Test
    void unknownByDefault() {
        assertFalse(new AstRoot().hasNoLexicalBindings());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "eval('x'); let x;",
                "(function () { x = 1; let x; })()",
                "{ a; let [a] = []; }",
                "(function () { (() => { b = 1; })(); let [b] = [1]; })()",
                "const {z = z} = {};",
                "var C1 = class C extends C {};",
                "(function (a = b, b) {})()",
                "for (let x of x);",
                "(() => x)(); let x;"
            })
    void deadZoneStillChecked(String source) {
        for (boolean interpreted : new boolean[] {false, true}) {
            try (Context cx = Context.enter()) {
                cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                cx.setInterpretedMode(interpreted);
                Object result =
                        cx.evaluateString(
                                cx.initStandardObjects(),
                                "var r; try { (0, eval)(\""
                                        + source
                                        + "\"); r = 'none' } catch (e) { r = e.name } r",
                                "test",
                                1,
                                null);
                assertEquals("ReferenceError", Context.toString(result), source);
            }
        }
    }

    @Test
    void asciiIdentifierCharacters() {
        for (int c = -1; c < 0x80; c++) {
            boolean expected =
                    (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '_';
            assertEquals(expected, UnicodeIdentifiers.isIdContinue(c), "character " + c);
        }
    }
}
