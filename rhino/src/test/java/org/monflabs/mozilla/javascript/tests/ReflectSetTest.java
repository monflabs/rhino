/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** [[Set]] returning a boolean: Reflect.set, receivers and proxies (plan 1.8b). */
class ReflectSetTest {

    private static final String PRELUDE =
            "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }";

    private static void assertGated(Object expectedES6, Object expected, String script) {
        Utils.assertGatedBehavior(expectedES6, expected, PRELUDE + script + " r.join('|')");
    }

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, PRELUDE + script + " r.join('|')");
        Utils.assertWithAllModes_ECMASCRIPT(expected, PRELUDE + script + " r.join('|')");
    }

    @Test
    void reflectSetReportsFailures() {
        assertGated(
                "true|true|true|true,1|TypeError|true",
                "false|false|false|true,1|false|false",
                "t(() => Reflect.set(Object.defineProperty({}, 'x', { value: 1 }), 'x', 2));"
                        + " t(() => Reflect.set(Object.preventExtensions({}), 'x', 2));"
                        + " t(() => Reflect.set({ get x() { return 1; } }, 'x', 2));"
                        + " t(() => { var o = {}; return [Reflect.set({}, 'x', 1, o), o.x].join(); });"
                        + " t(() => Reflect.set({}, 'x', 1, 'a primitive'));"
                        + " t(() => Reflect.set({}, 'x', 1, Object.defineProperty({}, 'x', { get() {} })));");
    }

    @Test
    void proxies() {
        assertGated(
                "true|undefined||Error|[object Object]",
                "false|TypeError|x,x|1|TypeError",
                "var p = new Proxy({}, { set() { return false; } });"
                        + " t(() => Reflect.set(p, 'x', 1));"
                        + " t(function () { 'use strict'; p.x = 1; });"
                        + " var log = []; var recv = new Proxy({}, { defineProperty(t, k, d) { log.push(k);"
                        + " return Reflect.defineProperty(t, k, d); },"
                        + " getOwnPropertyDescriptor(t, k) { log.push(k); return Reflect.getOwnPropertyDescriptor(t, k); } });"
                        + " t(() => { Reflect.set({}, 'x', 1, recv); return log.join(); });"
                        + " t(() => { var proto = new Proxy({}, { has() { throw new Error('has'); } });"
                        + " var o = Object.create(proto); o.y = 1; return o.y; });"
                        + " t(() => Object.getOwnPropertyDescriptor(new Proxy({}, {"
                        + " getOwnPropertyDescriptor() { return { value: 1, configurable: false }; } }), 'x'));");
    }

    @Test
    void deleteDefineAndSetPrototype() {
        assertGated(
                "true|undefined|false|[object Object]|false|[object Object]|false",
                "false|TypeError|false|TypeError|false|TypeError|true",
                "var nd = new Proxy({}, { deleteProperty() { return false; } });"
                        + " t(() => Reflect.deleteProperty(nd, 'x'));"
                        + " t(function () { 'use strict'; delete nd.x; });"
                        + " var ndef = new Proxy({}, { defineProperty() { return false; } });"
                        + " t(() => Reflect.defineProperty(ndef, 'x', { value: 1 }));"
                        + " t(() => Object.defineProperty(ndef, 'x', { value: 1 }));"
                        + " t(() => Reflect.setPrototypeOf(Object.preventExtensions({}), {}));"
                        + " t(() => Object.setPrototypeOf(new Proxy({}, { setPrototypeOf() { return false; } }), {}));"
                        + " t(() => { var tgt = { get a() { return this; } }; var p = new Proxy(tgt, {});"
                        + " return p.a === p; });");
    }
}
