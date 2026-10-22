/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A large literal of constants (a table of data) compiled as data, parsed once into a template:
 * each evaluation makes new objects, with the values and keys of the literal.
 */
class ConstantLiteralTest {

    /** 1,200 elements of every kind of constant, nested arrays and objects. */
    private static String literal() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1200; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            switch (i % 9) {
                case 0:
                    sb.append(i * 40000);
                    break;
                case 1:
                    sb.append('-').append(i);
                    break;
                case 2:
                    sb.append(i / 7.0);
                    break;
                case 3:
                    sb.append("'s").append(i).append("\\u00e9\\ud83d\\ude00:,;[{'");
                    break;
                case 4:
                    sb.append('[').append(i).append(", -0, true, null]");
                    break;
                case 5:
                    sb.append("{a: ").append(i).append(", 'b c': 'x', 12: false, 1.5: 2}");
                    break;
                case 6:
                    sb.append("-0");
                    break;
                case 7:
                    sb.append("1e300");
                    break;
                default:
                    sb.append("0.1");
            }
        }
        return sb.append(']').toString();
    }

    @Test
    void largeLiteralOfConstants() {
        String script =
                "function make() { return "
                        + literal()
                        + "; }"
                        + "var a = make(), b = make();"
                        + "var r = [a !== b, a[4] !== b[4], a.length, JSON.stringify(a).length,"
                        + "  1 / a[6] === -Infinity, 1 / a[4][1], Object.keys(a[5]).join('|'),"
                        + "  typeof a[0], a[0], a[2], a[3] === 's3\\u00e9\\ud83d\\ude00:,;[{'];"
                        + "a[4].push(9); r.push(b[4].length);"
                        + "r.join(' ; ')";
        String expected =
                "true ; true ; 1200 ; 15400 ; true ; -Infinity ; 12|a|b c|1.5 ; number ; 0 ;"
                        + " 0.2857142857142857 ; true ; 4";
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }
}
