/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Optional calls, generators, array-likes and constructors: stock behavior under VERSION_ES6. */
class LanguageResidualsGateTest {

    private static Object evalES6(Context.EvaluationMethod mode, String script) {
        Object[] result = new Object[1];
        Utils.runWithMode(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    result[0] =
                            cx.evaluateString(cx.initStandardObjects(), script, "test.js", 1, null);
                    return null;
                },
                mode);
        return result[0];
    }

    @Test
    void optionalCallOfAnElement() {
        Utils.assertGatedBehavior(
                "TypeError|TypeError|TypeError|undefined",
                "undefined|undefined|undefined|undefined",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => { var x; return x[0]?.(); });"
                        + " t(() => { var x; return x['k']?.(); });"
                        + " t(() => { var o = {}; return o['k']?.(); });"
                        + " t(() => { var o = {}; return o[0]?.(); });"
                        + " r.join('|')");
    }

    @Test
    void strictGeneratorResumedFromSloppyCode() {
        Utils.assertGatedBehavior(
                "no|false",
                "TypeError|TypeError",
                "var g = (function* () { 'use strict'; yield 0;"
                        + " var o = Object.freeze({ p: 1 });"
                        + " try { o.p = 2; yield 'no'; } catch (e) { yield e.name; }"
                        + " try { yield String(delete Object.prototype); } catch (e) { yield e.name; } })();"
                        + " g.next(); [g.next().value, g.next().value].join('|')");
    }

    @Test
    void generatorReturnThroughAFinallyThatYields() {
        String script =
                "function* g() { try { yield 1; } finally { yield 2; } }"
                        + " var it = g(); it.next(); var r = [];"
                        + " try { var a = it.return(9); var b = it.next();"
                        + " r.push(a.value, a.done, b.value, b.done); } catch (e) { r.push(e.name); }"
                        + " function* g2() { try { try { yield 1; } finally { yield 2;"
                        + " throw new Error('fin'); } } catch (e) { yield 'caught ' + e.message; } }"
                        + " var it2 = g2(); it2.next(); it2.next();"
                        + " try { r.push(it2.next().value); } catch (e) { r.push('escaped'); }"
                        + " r.join()";
        Utils.assertWithAllModes_ECMASCRIPT("2,false,9,true,caught fin", script);
        // VERSION_ES6: the stock behaviors, which differ by mode
        assertEquals("TypeError,caught fin", evalES6(Context.EvaluationMethod.Interpreter, script));
        assertEquals("2,false,,true,escaped", evalES6(Context.EvaluationMethod.Compiler, script));
    }

    @Test
    void arrayLikeTooLong() {
        String script = "try { new Int8Array({ length: Math.pow(2, 53) }); } catch (e) { e.name }";
        Utils.assertWithAllModes_ECMASCRIPT("RangeError", script);
        for (Context.EvaluationMethod mode : Context.EvaluationMethod.values()) {
            assertThrows(IllegalArgumentException.class, () -> evalES6(mode, script));
        }
    }

    @Test
    void prototypeFromConstructorFallback() {
        Utils.assertGatedBehavior(
                "true|Error|true|true",
                "false|RangeError|false|false",
                "var r = []; function nt() {} nt.prototype = null;"
                        + " r.push(Object.getPrototypeOf(Reflect.construct(TypeError, [], nt))"
                        + " === Error.prototype);"
                        + " function nt2() {} nt2.prototype = 3;"
                        + " r.push(Reflect.construct(RangeError, ['m'], nt2).name);"
                        + " function F() {}"
                        + " r.push(Object.getPrototypeOf(Reflect.construct(F, [], nt)) === F.prototype);"
                        + " r.push(Object.getPrototypeOf(Reflect.construct(F, [], function () {}.bind()))"
                        + " === F.prototype);"
                        + " r.join('|')");
    }
}
