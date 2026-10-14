/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2026;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Uint8Array to and from base64 and hex (plan 12.5). */
class Uint8ArrayBase64Test {

    @Test
    void gated() {
        Utils.assertGatedBehavior(
                "undefined,undefined",
                "function,function",
                "[typeof Uint8Array.fromBase64, typeof Uint8Array.prototype.toHex].join()");
    }

    @Test
    void encode() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SGVsbG/7/w==|SGVsbG_7_w|SGVsbG_7_w==|48656c6c6ffbff",
                "var a = new Uint8Array([72, 101, 108, 108, 111, 251, 255]);\n"
                        + "[a.toBase64(), a.toBase64({ alphabet: 'base64url', omitPadding: true }),"
                        + " a.toBase64({ alphabet: 'base64url' }), a.toHex()].join('|')");
    }

    @Test
    void decode() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "72,101,108,108,111|72,101,108|2|4/3/72,101,108,0|SyntaxError|SyntaxError|TypeError",
                "var r = [Uint8Array.fromBase64(' SGVs\\nbG8= ').join(),"
                        + " Uint8Array.fromHex('48656C').join(),"
                        + " Uint8Array.fromBase64('SGk').buffer.byteLength];\n"
                        + "var t = new Uint8Array(4), res = t.setFromBase64('SGVsbG8=');\n"
                        + "r.push(res.read + '/' + res.written + '/' + t.join());\n"
                        + "try { Uint8Array.fromBase64('SGk', { lastChunkHandling: 'strict' }); }"
                        + " catch (e) { r.push(e.name); }\n"
                        + "try { Uint8Array.fromHex('abc'); } catch (e) { r.push(e.name); }\n"
                        + "try { Uint8Array.fromBase64('', { alphabet: 'other' }); }"
                        + " catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }
}
