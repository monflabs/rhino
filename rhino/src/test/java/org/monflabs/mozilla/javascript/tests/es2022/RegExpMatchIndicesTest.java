/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2022;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The RegExp d flag: hasIndices and the indices of a match (plan 8.4). */
class RegExpMatchIndicesTest {

    @Test
    void indices() {
        Utils.assertWithAllModes_ES6(
                "[[[1,3],null,[2,3]],[1,3],true,false]",
                "var m = /a(?<x>b)?(c)/d.exec('zac');\n"
                        + "JSON.stringify([m.indices, m.indices.groups.x === undefined ? m.indices[0]"
                        + " : null, 'x' in m.indices.groups, 'indices' in /a/.exec('a')])");
        Utils.assertWithAllModes_ES6(
                "dgy,true,false,dg",
                "[/x/ygd.flags, /x/d.hasIndices, /x/.hasIndices, new RegExp('x', 'gd').flags].join()");
        Utils.assertWithAllModes_ES6(
                "[[0,2],[2,4]]",
                "var r = /(?<a>.)./dg; var res = [];\n"
                        + "for (var m of 'abcd'.matchAll(r)) res.push(m.indices[0]);\n"
                        + "JSON.stringify(res)");
    }
}
