/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2017;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;
import org.monflabs.mozilla.javascript.typedarrays.NativeSharedArrayBuffer;

/** SharedArrayBuffer and Atomics (plans 3.3 and 3.4). */
class SharedArrayBufferAtomicsTest {

    @Test
    void sharedArrayBuffer() {
        Utils.assertWithAllModes_ES6(
                "8,true,16,12,12,TypeError,RangeError,SharedArrayBuffer",
                "var sab = new SharedArrayBuffer(8, { maxByteLength: 16 });\n"
                        + "var r = [sab.byteLength, sab.growable, sab.maxByteLength];\n"
                        + "sab.grow(12); r.push(sab.byteLength, new Uint8Array(sab).length);\n"
                        + "try { ArrayBuffer.prototype.slice.call(sab); }"
                        + " catch (e) { r.push(e.name); }\n"
                        + "try { sab.grow(4); } catch (e) { r.push(e.name); }\n"
                        + "r.push(Object.prototype.toString.call(sab).slice(8, -1));\n"
                        + "r.join()");
        Utils.assertWithAllModes_ES6(
                "2,3",
                "var sab = new SharedArrayBuffer(4); new Uint8Array(sab).set([1, 2, 3, 4]);\n"
                        + "Array.from(new Uint8Array(sab.slice(1, 3))).join()");
    }

    @Test
    void readModifyWrite() {
        Utils.assertWithAllModes_ES6(
                "5,7,2,6,6,4,5,4294967295,-1,3",
                "var ta = new Int32Array(new SharedArrayBuffer(16));\n"
                        + "var r = [Atomics.store(ta, 0, 5.9), Atomics.add(ta, 0, 2) + 2];\n"
                        + "r.push(Atomics.sub(ta, 0, 5) + 0 - 5);\n"
                        + "Atomics.store(ta, 1, 6); r.push(Atomics.or(ta, 1, 2), Atomics.and(ta, 1, 4));\n"
                        + "r.push(Atomics.exchange(ta, 1, 5), Atomics.load(ta, 1));\n"
                        + "var u = new Uint32Array(ta.buffer); Atomics.store(u, 2, -1);\n"
                        + "r.push(Atomics.load(u, 2), Atomics.load(ta, 2));\n"
                        + "Atomics.compareExchange(ta, 3, 0, 3); Atomics.compareExchange(ta, 3, 1, 9);\n"
                        + "r.push(Atomics.load(ta, 3));\n"
                        + "r.join()");
        Utils.assertWithAllModes_ES6(
                "-128,127,10n,18446744073709551615n",
                "var i8 = new Int8Array(4); Atomics.store(i8, 0, 127);\n"
                        + "var r = [Atomics.add(i8, 0, 1) - 255, Atomics.load(i8, 0) + 255];\n"
                        + "var b = new BigInt64Array(new SharedArrayBuffer(16)); Atomics.store(b, 0, 10n);\n"
                        + "r.push(Atomics.load(b, 0) + 'n');\n"
                        + "var bu = new BigUint64Array(b.buffer); Atomics.sub(bu, 1, 1n);\n"
                        + "r.push(Atomics.load(bu, 1) + 'n');\n"
                        + "r.join()");
    }

    @Test
    void validation() {
        Utils.assertWithAllModes_ES6(
                "TypeError,TypeError,RangeError,0,TypeError,true",
                "var r = [];\n"
                        + "function t(f) { try { f(); r.push('none'); } catch (e) { r.push(e.name); } }\n"
                        + "t(() => Atomics.add(new Float64Array(4), 0, 1));\n"
                        + "t(() => Atomics.add(new Uint8ClampedArray(4), 0, 1));\n"
                        + "t(() => Atomics.load(new Int32Array(4), 4));\n"
                        + "r.push(Atomics.notify(new Int32Array(new SharedArrayBuffer(8)), 0));\n"
                        + "t(() => Atomics.notify(new Uint32Array(4), 0));\n"
                        + "r.push(Atomics.isLockFree(4));\n"
                        + "r.join()");
    }

    @Test
    void waitWithoutNotify() {
        Utils.assertWithAllModes_ES6(
                "not-equal,timed-out,TypeError,TypeError",
                "var ta = new Int32Array(new SharedArrayBuffer(8));\n"
                        + "var r = [Atomics.wait(ta, 0, 1), Atomics.wait(ta, 0, 0, 10)];\n"
                        + "try { Atomics.wait(new Int32Array(4), 0, 0, 0); } catch (e) { r.push(e.name); }\n"
                        + "try { Atomics.wait(new Uint32Array(ta.buffer), 0, 0, 0); }"
                        + " catch (e) { r.push(e.name); }\n"
                        + "r.join()");
    }

    @Test
    void waitAndNotifyAcrossThreads() throws Exception {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            TopLevel scope = cx.initStandardObjects();
            NativeSharedArrayBuffer sab =
                    (NativeSharedArrayBuffer)
                            cx.evaluateString(
                                    scope, "var sab = new SharedArrayBuffer(8); sab", "t", 1, null);
            AtomicReference<Object> woken = new AtomicReference<>();
            Thread notifier =
                    new Thread(
                            () -> {
                                try (Context other = Context.enter()) {
                                    other.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                                    TopLevel realm = other.initStandardObjects();
                                    ScriptableObject.putProperty(
                                            realm,
                                            "sab",
                                            NativeSharedArrayBuffer.share(realm, sab));
                                    // notify until the waiter is woken
                                    woken.set(
                                            other.evaluateString(
                                                    realm,
                                                    "var ta = new Int32Array(sab); var n = 0;\n"
                                                            + "while (n === 0) { Atomics.store(ta, 1, 7);"
                                                            + " n = Atomics.notify(ta, 0); }\n"
                                                            + "n",
                                                    "notifier",
                                                    1,
                                                    null));
                                }
                            });
            notifier.start();
            Object result =
                    cx.evaluateString(
                            scope,
                            "var ta = new Int32Array(sab);"
                                    + " Atomics.wait(ta, 0, 0) + ',' + Atomics.load(ta, 1)",
                            "waiter",
                            1,
                            null);
            notifier.join(10_000);
            assertEquals("ok,7", result);
            assertEquals(1, ((Number) woken.get()).intValue());
        }
    }

    @Test
    void strictGeneratorResumedBySloppyCode() {
        Utils.assertWithAllModes_ES6(
                "TypeError",
                "var g = (function() { 'use strict'; return function* G() { eval('G = 1'); }; })();\n"
                        + "function sloppy() { try { g().next(); return 'none'; }"
                        + " catch (e) { return e.name; } }\n"
                        + "sloppy()");
    }
}
