/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.CompilerEnvirons;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.optimizer.ClassCompiler;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Defects that no script can rely on are fixed in every language version, not only under
 * VERSION_ECMASCRIPT (BACKWARD_COMPATIBILITY.md 1.90, 1.92, 1.93, 1.94, 1.103, 1.107, 1.108).
 */
class DefectsFixedInAllVersionsTest {

    private static void assertInBothVersions(Object expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void strictEqualityOfADirectCallParameter() {
        assertInBothVersions(
                10, "function f(n) { if (n === 0) return 0; return 1 + f(n - 1); } f(10)");
    }

    @Test
    void restParameterOfADirectCall() {
        assertInBothVersions(
                "true/xy",
                "function rest(...a) { return a; }"
                        + " function f() { return rest('xy'); }"
                        + " var r = f(); Array.isArray(r) + '/' + r[0]");
    }

    @Test
    void bigIntParameters() {
        assertInBothVersions(
                "6", "function mul(x, y) { return x * y; } String(mul(BigInt(2), BigInt(3)))");
        assertInBothVersions(true, "function lt(x) { return x < 2; } lt(BigInt(1))");
        assertInBothVersions(
                "5",
                "function mod(a, b) {"
                        + " var zero = BigInt(0); var r = a % b; return r >= zero ? r : b + r; }"
                        + " String(mod(BigInt(-9), BigInt(7)))");
        assertInBothVersions(
                "3", "function inc(x) { var y = x; y++; return y; } String(inc(BigInt(2)))");
    }

    @Test
    void nextOfAnotherKindOfIterator() {
        assertInBothVersions(
                "TypeError",
                "var next = Object.getPrototypeOf([][Symbol.iterator]()).next;"
                        + " try { next.call('ab'[Symbol.iterator]()); 'no error'; }"
                        + " catch (e) { e.name; }");
    }

    @Test
    void symbolOnTheLeftOfInstanceof() {
        assertInBothVersions(false, "function C() {} Symbol() instanceof C.bind()");
        assertInBothVersions(false, "function C() {} Symbol() instanceof C");
    }

    @Test
    void callOfANumberVariable() {
        assertInBothVersions(
                "TypeError",
                "function f() { var x = 1; return x(); }"
                        + " try { f(); 'no error'; } catch (e) { e.name; }");
    }

    /** As the jsc tool does: there is no current Context while it compiles. */
    @Test
    void forInCompiledWithoutACurrentContext() {
        assertNull(Context.getCurrentContext());
        Object[] classes =
                new ClassCompiler(new CompilerEnvirons())
                        .compileToClassFiles(
                                "var o = {a: 1}; for (var k in o) { o[k]; }",
                                "forin.js",
                                1,
                                "ForIn");
        assertTrue(classes.length >= 2);
    }
}
