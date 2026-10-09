/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Early errors of parameter lists, rest patterns and the Function constructor (plan 1.2c); the same
 * in both versions.
 */
class FunctionEarlyErrorsTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    private static String errorNames(String... sources) {
        StringBuilder sb = new StringBuilder("var r = [];");
        for (String src : sources) {
            sb.append(" try { eval(")
                    .append('"')
                    .append(src.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"')
                    .append("); r.push('ok'); } catch (e) { r.push(e.name); }");
        }
        return sb.append(" r.join()").toString();
    }

    @Test
    void duplicateParameters() {
        assertBoth(
                "ok,SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok",
                errorNames(
                        "function f(a, a) {}",
                        "function f(a, a) { 'use strict'; }",
                        "function f(a = 0, a) {}",
                        "function f([a], a) {}",
                        "({ m(a, a) {} })",
                        "function* g(a, a) {}"));
    }

    @Test
    void strictBodyChecksTheNameAndParameters() {
        assertBoth(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError",
                errorNames(
                        "function eval() { 'use strict'; }",
                        "function f(arguments) { 'use strict'; }",
                        "(eval) => { 'use strict'; }",
                        "function f([a]) { 'use strict'; }",
                        "function f() { 'use strict'; eval = 1; }"));
    }

    @Test
    void functionConstructor() {
        assertBoth(
                "SyntaxError,SyntaxError,TypeError,TypeError",
                "var r = [];"
                        + " try { Function('a', 'a', '\"use strict\";'); } catch (e) { r.push(e.name); }"
                        + " try { new Function(' ', \"'use strict'; eval = 42;\"); }"
                        + " catch (e) { r.push(e.name); }"
                        + " var s = new Function(\"'use strict';\");"
                        + " try { s.arguments; } catch (e) { r.push(e.name); }"
                        + " try { s.caller = 1; } catch (e) { r.push(e.name); }"
                        + " r.join()");
    }

    @Test
    void restParameterWithABindingPattern() {
        assertBoth(
                "1/2/3,4,5",
                "function f(a, ...[b, c]) { return [a, b, c].join('/'); }"
                        + " function g(...{length}) { return length; }"
                        + " [f(1, 2, 3), g(1, 2, 3, 4), ((...[x, y]) => x + y)(2, 3)].join()");
    }

    @Test
    void parametersHaveATemporalDeadZone() {
        Utils.assertGatedBehavior(
                "undefined,undefined,undefined,2,3",
                "ReferenceError,ReferenceError,ReferenceError,2,3",
                "var r = []; var y = 0;"
                        + " function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(function () { function f(x = y, y) { return x; } return f(); });"
                        + " t(function () { function f(x = x) { return x; } return f(); });"
                        + " t(function () { function f(x = typeof y, y) { return x; } return f(); });"
                        + " t(function () { function f(a, x = a + 1) { return x; } return f(1); });"
                        + " t(function () { function f(x = () => y, y = 3) { return x(); } return f(); });"
                        + " r.join()");
    }

    @Test
    void argumentsInADefaultParameter() {
        assertBoth(
                "third", "function f(x = arguments[2], z) { return x; } f(undefined, 0, 'third')");
    }
}
