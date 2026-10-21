/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.serialize.ScriptableInputStream;
import org.monflabs.mozilla.javascript.serialize.ScriptableOutputStream;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The own properties of the functions a script creates, laid out at creation, and their prototype
 * object, created when it is first read.
 */
class FunctionObjectsTest {

    private static final String SHOW =
            "var out = [];"
                    + "function props(f) {"
                    + "  var names = Object.getOwnPropertyNames(f), r = [];"
                    + "  for (var i = 0; i < names.length; i++) {"
                    + "    var d = Object.getOwnPropertyDescriptor(f, names[i]);"
                    + "    r.push(names[i] + ':' + (d.writable ? 'w' : '-') + (d.enumerable ? 'e' : '-')"
                    + "      + (d.configurable ? 'c' : '-'));"
                    + "  }"
                    + "  return r.join(' ');"
                    + "}";

    @Test
    void ownPropertiesAndPrototype() {
        String script =
                SHOW
                        + "function show(label, f) {"
                        + "  var p = f.prototype;"
                        + "  out.push(label + ' ' + props(f) + ' / ' + [p === f.prototype,"
                        + "    p.constructor === f, Object.getPrototypeOf(p) === Object.prototype,"
                        + "    Object.getOwnPropertyNames(p).join('|')].join());"
                        + "}"
                        + "show('declaration', function f(a, b) {});"
                        + "show('expression', function (a) {});"
                        + "show('strict', function (a) { 'use strict'; });"
                        + "(function () { 'use strict'; show('in strict code', function (a) {}); })();"
                        + "var f1 = function () {}; var o1 = new f1();"
                        + "out.push('new ' + (o1 instanceof f1) + ' ' + (Object.getPrototypeOf(o1) === f1.prototype));"
                        + "var f2 = function () {}; f2.prototype = { x: 1 }; out.push('replaced ' + new f2().x);"
                        + "var f3 = function () {}; Object.defineProperty(f3, 'prototype', { writable: false });"
                        + "out.push('read-only ' + Object.getOwnPropertyDescriptor(f3, 'prototype').writable"
                        + "  + ' ' + (f3.prototype.constructor === f3));"
                        + "var f4 = function () {};"
                        + "out.push('instanceof ' + ({} instanceof f4) + ' ' + (Object.create(f4.prototype) instanceof f4));"
                        + "var f5 = function () {};"
                        + "out.push('own ' + f5.hasOwnProperty('prototype') + ' ' + delete f5.prototype"
                        + "  + ' ' + Object.keys(f5.prototype).length);"
                        + "out.join('; ')";
        String rest =
                "new true true; replaced 1; read-only false true; instanceof false true;"
                        + " own true false 0";
        Utils.assertGatedBehavior(
                "declaration length:--c name:--c arity:--c prototype:w-- / true,true,true,constructor;"
                        + " expression length:--c name:--c arity:--c prototype:w-- /"
                        + " true,true,true,constructor;"
                        + " strict length:--c name:--c arity:--c prototype:w-- / true,true,true,constructor;"
                        + " in strict code length:--c name:--c arity:--c prototype:w-- /"
                        + " true,true,true,constructor; "
                        + rest,
                "declaration length:--c name:--c arguments:--- caller:--- prototype:w-- /"
                        + " true,true,true,constructor;"
                        + " expression length:--c name:--c arguments:--- caller:--- prototype:w-- /"
                        + " true,true,true,constructor;"
                        + " strict length:--c name:--c prototype:w-- / true,true,true,constructor;"
                        + " in strict code length:--c name:--c prototype:w-- / true,true,true,constructor; "
                        + rest,
                script);
    }

    @Test
    void ownPropertiesOfOtherKindsOfFunctions() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "arrow length:--c name:--c / none; method length:--c name:--c / none;"
                        + " generator length:--c name:--c prototype:w-- / object;"
                        + " async length:--c name:--c / none;"
                        + " async generator length:--c name:--c prototype:w-- / object;"
                        + " class length:--c name:--c prototype:--- / object;"
                        + " generator prototype true false",
                SHOW
                        + "function show(label, f) {"
                        + "  out.push(label + ' ' + props(f) + ' / '"
                        + "    + (Object.getOwnPropertyDescriptor(f, 'prototype') ? typeof f.prototype : 'none'));"
                        + "}"
                        + "show('arrow', (a, b) => a);"
                        + "show('method', { m(a) {} }.m);"
                        + "show('generator', function* g(a) {});"
                        + "show('async', async function (a) {});"
                        + "show('async generator', async function* (a) {});"
                        + "show('class', class C { constructor(a) {} });"
                        + "var g = function* () {};"
                        + "out.push('generator prototype ' + (Object.getPrototypeOf(g.prototype)"
                        + "  === Object.getPrototypeOf(function* () {}).prototype) + ' '"
                        + "  + g.prototype.hasOwnProperty('constructor'));"
                        + "out.join('; ')");
    }

    @Test
    void prototypeNotReadBeforeSerialization() throws Exception {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(true);
            TopLevel scope = cx.initStandardObjects();
            Object f = cx.evaluateString(scope, "(function () {})", "f.js", 1, null);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ScriptableOutputStream out = new ScriptableOutputStream(bytes, scope)) {
                out.writeObject(f);
            }
            Object g;
            try (ScriptableInputStream in =
                    new ScriptableInputStream(
                            new ByteArrayInputStream(bytes.toByteArray()), scope)) {
                g = in.readObject();
            }
            ScriptableObject.putProperty(scope, "g", g);
            assertEquals(
                    "true true",
                    cx.evaluateString(
                            scope,
                            "(g.prototype.constructor === g) + ' '"
                                    + " + (Object.getPrototypeOf(g.prototype) === Object.prototype)",
                            "check.js",
                            1,
                            null));
        }
    }
}
