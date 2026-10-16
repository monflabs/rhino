/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Lazily loaded constructors are built on first use, not when the scope is initialized: the
 * constructor used internally is the original one, and the global property stays as the script left
 * it.
 */
class LazyBuiltinCtorTest {

    @Test
    void promiseRedefinedAsAccessorBeforeFirstUse() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "function,Promise,,true",
                "var log = [];"
                        + "Object.defineProperty(globalThis, 'Promise', {"
                        + "  get() { log.push('get'); return 1; },"
                        + "  set(v) { log.push('set'); }, configurable: true });"
                        + "async function f() {}"
                        + "var p = f();"
                        + "[typeof p.then,"
                        + " Object.getPrototypeOf(p).constructor.name,"
                        + " log.join(),"
                        + " Object.getOwnPropertyDescriptor(globalThis, 'Promise').get !== undefined"
                        + "].join()");
    }

    @Test
    void promiseDeletedBeforeFirstUse() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,false",
                "delete globalThis.Promise;"
                        + "async function f() {}"
                        + "var p = f();"
                        + "[typeof p.then === 'function', 'Promise' in globalThis].join()");
    }

    @Test
    void arrayBufferReplacedBeforeFirstUse() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,true,false",
                "ArrayBuffer = 1;"
                        + "var u = Uint8Array.fromBase64('AQI=');"
                        + "[ArrayBuffer,"
                        + " typeof Object.getPrototypeOf(u.buffer).slice === 'function',"
                        + " Object.getOwnPropertyDescriptor(globalThis, 'ArrayBuffer').enumerable"
                        + "].join()");
    }

    @Test
    void bigIntReplacedBeforeFirstUse() {
        Utils.assertWithAllModes_ES6(
                "0,bigint", "BigInt = 0; [BigInt, typeof Object(1n).valueOf()].join()");
    }

    @Test
    void frozenGlobal() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,true,true",
                "Promise = null;"
                        + "Object.freeze(globalThis);"
                        + "async function f() {}"
                        + "[typeof f().then === 'function', Promise === null, Object.isFrozen(globalThis)]"
                        + ".join()");
    }

    @Test
    void usedBeforeAndAfterCache() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,true",
                "var M = Map; var m = new M();"
                        + "Map = null;"
                        + "[m instanceof M, Object.getPrototypeOf(new M()) === M.prototype].join()");
    }

    @Test
    void isolateSharesTheLazyConstructors() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel parent = cx.initStandardObjects();
                    TopLevel isolate = TopLevel.createIsolate(parent);
                    Object result =
                            cx.evaluateString(
                                    isolate,
                                    "async function f() {}"
                                            + "Object.getPrototypeOf(f()) === Promise.prototype",
                                    "isolate.js",
                                    1,
                                    null);
                    assertEquals(Boolean.TRUE, result);
                    return null;
                });
    }
}
