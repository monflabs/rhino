/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Annex B (plan 1.11). */
class AnnexBTest {

    @Test
    void stringAndDateMethods() {
        Utils.assertGatedBehavior(
                "false,trimRight,0,-1",
                "true,trimEnd,1,1900",
                "var d = new Date(2000, 0, 1); d.setYear(-0.5);"
                        + " [String.prototype.trimLeft === String.prototype.trimStart,"
                        + " String.prototype.trimRight.name, 'x'.anchor.length, d.getFullYear()].join()");
    }

    @Test
    void regExpLegacyFeatures() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "false,TypeError,TypeError",
                "var r = [];"
                        + " r.push(Object.getOwnPropertyDescriptor(RegExp, 'lastMatch').enumerable);"
                        + " try { /a/.compile(/b/, 'g'); r.push('no'); } catch (e) { r.push(e.name); }"
                        + " try { new (class extends RegExp {})('a').compile(); r.push('no'); }"
                        + " catch (e) { r.push(e.name); }"
                        + " r.join()");
    }

    @Test
    void regExpLegacyFeaturesGated() {
        Utils.assertGatedBehavior(
                "true,no",
                "false,TypeError",
                "var r = [];"
                        + " r.push(Object.getOwnPropertyDescriptor(RegExp, 'lastMatch').enumerable);"
                        + " try { /a/.compile(/b/, 'g'); r.push('no'); } catch (e) { r.push(e.name); }"
                        + " r.join()");
    }

    @Test
    void regExpPatternGrammar() {
        Utils.assertGatedBehavior(
                "false,false,false",
                "true,true,true",
                "[/\\xa/.test('xa'), /[\\c0]/.test('\\x10'), /\\0022/.test('\\x022')].join()");
    }

    @Test
    void htmlCloseCommentAfterBlockComment() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "0",
                "try { eval('var n = 0;\\n/*\\n*/--> n += 1;\\n/* c */ --> n += 1;\\nString(n)'); }"
                        + " catch (e) { e.name }");
    }

    @Test
    void protoInObjectLiteralsAndGlobalVars() {
        Utils.assertGatedBehavior(
                "true,false,true,[object Object],function",
                "false,true,true,2,undefined",
                "var p = {}; var x = '__proto__'; var __proto__ = 2; var toString;"
                        + " [Object.getPrototypeOf({ ['__proto__']: p }) === p,"
                        + " ({ [x]: 1 }).hasOwnProperty('__proto__'),"
                        + " Object.getPrototypeOf({ __proto__: p }) === p,"
                        + " ({ __proto__ }).__proto__, typeof toString].join()");
    }

    @Test
    void globalVarKeepsSharedScopeBinding() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel shared = cx.initStandardObjects();
                    cx.evaluateString(shared, "var config = 'shared';", "shared", 1, null);
                    TopLevel request = TopLevel.createIsolate(shared);
                    assertEquals(
                            "shared",
                            cx.evaluateString(request, "var config; config", "request", 1, null));
                    return null;
                });
    }

    @Test
    void callTargetsForInInitializersAndCatchPatterns() {
        Utils.assertGatedBehavior(
                "ReferenceError,false,ok,function",
                "ReferenceError,true,SyntaxError,undefined",
                "var r = []; var called = false; function f() { called = true; }"
                        + " try { f() = 1; } catch (e) { r.push(e.name); }"
                        + " r.push(called);"
                        + " try { eval('\"use strict\"; for (var a = 0 in {});'); r.push('ok'); }"
                        + " catch (e) { r.push(e.name); }"
                        + " try { throw {}; } catch ({ g }) { { function g() {} } }"
                        + " r.push(typeof g);"
                        + " r.join()");
    }
}
