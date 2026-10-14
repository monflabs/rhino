/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2026;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** JSON.parse source text access, JSON.rawJSON and JSON.isRawJSON (plan 12.6). */
class JsonSourceTextTest {

    @Test
    void gated() {
        Utils.assertGatedBehavior(
                "undefined|2",
                "function|3",
                "var n; JSON.parse('1', function () { n = arguments.length; });\n"
                        + "typeof JSON.rawJSON + '|' + n");
    }

    @Test
    void reviverSource() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "a:1.0|0:-0|1:\"x\\u0041\"|b:undefined|d:null|c:undefined|:undefined|e:undefined",
                "var log = [];\n"
                        + "JSON.parse('{\"a\": 1.0, \"b\": [-0, \"x\\\\u0041\"], \"c\": {\"d\": null}}',"
                        + " function (k, v, ctx) { log.push(k + ':' + ctx.source);"
                        + " if (k === 'a') this.e = 2; return v; });\n"
                        + "JSON.parse('{\"e\": 1}', function (k, v, ctx) {"
                        + " if (k === '') { log.push('e:' + ctx.source); } else this.e = 5; return v; });\n"
                        + "log.slice(0, 7).concat(log.slice(-1)).join('|')");
    }

    @Test
    void rawJson() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "{\"n\":12345678901234567890,\"m\":[\"s\"]}|true|false|true||SyntaxError|SyntaxError",
                "var raw = JSON.rawJSON('12345678901234567890'), r = [];\n"
                        + "r.push(JSON.stringify({ n: raw, m: [JSON.rawJSON('\"s\"')] }),"
                        + " JSON.isRawJSON(raw), JSON.isRawJSON({}), Object.isFrozen(raw),"
                        + " Object.getPrototypeOf(raw));\n"
                        + "try { JSON.rawJSON(' 1'); } catch (e) { r.push(e.name); }\n"
                        + "try { JSON.rawJSON('[]'); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }
}
