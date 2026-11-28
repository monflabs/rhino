/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2024;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Element writes check the bounds once, after the value is converted. */
public class TypedArrayElementWriteTest {

    private static void assertBothVersions(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    public void inBoundsWritesConvertTheValue() {
        assertBothVersions(
                "-56,255,44,1,0.5,7",
                Utils.lines(
                        "var i8 = new Int8Array(2); i8[0] = 200;",
                        "var c = new Uint8ClampedArray(2); c[0] = 300;",
                        "var u = new Uint8Array(2); u[1] = 300;",
                        "var f = new Float32Array(2); f[0] = 1; f[1] = 0.5;",
                        "var d = new Float64Array(2); d['1'] = '7';",
                        "[i8[0], c[0], u[1], f[0], f[1], d[1]].join()"));
    }

    @Test
    public void writesPastTheEndAreIgnored() {
        assertBothVersions(
                "undefined,2",
                Utils.lines(
                        "var a = new Uint8Array(2); a[2] = 1; a[1000] = 1;",
                        "String(a[2]) + ',' + a.length"));
    }

    @Test
    public void nonIndexKeysWriteNoElement() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "0,0,undefined,undefined,9",
                Utils.lines(
                        "var a = new Uint8Array(2);",
                        "a[-1] = 1; a[1.5] = 1; a['-0'] = 1; a[-0] = 9;",
                        "[a[1], Object.keys(a).length - 2, a[-1], a[1.5], a[0]].map(String).join()"));
    }

    @Test
    public void aDetachedViewIgnoresWrites() {
        assertBothVersions(
                "undefined,0",
                Utils.lines(
                        "var b = new ArrayBuffer(8); var a = new Uint8Array(b);",
                        "b.transfer(); a[0] = 1;",
                        "String(a[0]) + ',' + a.length"));
    }

    @Test
    public void detachingWhileConvertingCancelsTheWrite() {
        assertBothVersions(
                "true,undefined",
                Utils.lines(
                        "var b = new ArrayBuffer(8); var a = new Uint8Array(b); var called = false;",
                        "a[0] = { valueOf() { called = true; b.transfer(); return 7; } };",
                        "called + ',' + a[0]"));
    }

    @Test
    public void shrinkingWhileConvertingCancelsTheWrite() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "4,undefined,5",
                Utils.lines(
                        "var b = new ArrayBuffer(8, { maxByteLength: 16 }); var a = new Uint8Array(b);",
                        "a[6] = { valueOf() { b.resize(4); return 9; } };",
                        "var r = [a.length, a[6]];",
                        "b.resize(16); a[12] = 5; r.push(a[12]);",
                        "r.map(String).join()"));
    }

    @Test
    public void aFixedLengthViewOnAShrunkBufferIgnoresWrites() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "undefined,0,3",
                Utils.lines(
                        "var b = new ArrayBuffer(8, { maxByteLength: 16 });",
                        "var a = new Uint8Array(b, 0, 8);",
                        "b.resize(4); a[0] = 1;",
                        "var r = [a[0], a.length];",
                        "b.resize(8); a[0] = 3; r.push(a[0]);",
                        "r.map(String).join()"));
    }

    @Test
    public void aFixedLengthViewOnAGrownSharedBufferKeepsItsLength() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "4,undefined,2",
                Utils.lines(
                        "var b = new SharedArrayBuffer(4, { maxByteLength: 16 });",
                        "var a = new Uint8Array(b, 0, 4);",
                        "b.grow(16); a[4] = 1; a[3] = 2;",
                        "[a.length, a[4], a[3]].map(String).join()"));
    }

    @Test
    public void aBigIntArrayStillRejectsNumbers() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,true,5",
                Utils.lines(
                        "var a = new BigInt64Array(2); var r = [];",
                        "try { a[0] = 1; } catch (e) { r.push(e instanceof TypeError); }",
                        "try { a[0] = 1.5; } catch (e) { r.push(e instanceof TypeError); }",
                        "a[1] = 5n; r.push(a[1]);",
                        "r.map(String).join()"));
    }
}
