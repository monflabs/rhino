/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * push and unshift write the elements of a dense array directly while its prototypes have no
 * indexed properties; an indexed property added to them after a first use is seen.
 */
class ArrayPushCacheTest {

    private static final String LOG =
            "var log = '';"
                    + "function f() { var a = [], b = [9]; a.push(1, 2); b.unshift(8);"
                    + "  return a.length + ':' + a[0] + ',' + a[1] + '/' + b.join(); }"
                    + "var r = f();";

    @Test
    void indexAddedToArrayPrototype() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2:1,2/8,9 2:undefined,2/8,9 set0=1; 2:1,2/8,9",
                LOG
                        + "Object.defineProperty(Array.prototype, '0', {"
                        + "  set(v) { log += 'set0=' + v + ';'; }, configurable: true });"
                        + "r += ' ' + f() + ' ' + log;"
                        + "delete Array.prototype[0];"
                        + "r += ' ' + f();"
                        + "r");
    }

    @Test
    void indexAddedToObjectPrototype() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2:1,2/8,9 2:1,undefined/8, set1=2;set1=9; 2:1,2/8,9",
                LOG
                        + "Object.defineProperty(Object.prototype, '1', {"
                        + "  set(v) { log += 'set1=' + v + ';'; }, configurable: true });"
                        + "r += ' ' + f() + ' ' + log;"
                        + "delete Object.prototype[1];"
                        + "r += ' ' + f();"
                        + "r");
    }

    @Test
    void readOnlyIndexOnObjectPrototype() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2:1,2/8,9 TypeError",
                LOG
                        + "Object.defineProperty(Object.prototype, '0', { value: 0, configurable: true });"
                        + "try { f(); } catch (e) { r += ' ' + e.name; }"
                        + "r");
    }

    @Test
    void prototypeOfArrayPrototypeReplaced() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2:1,2/8,9 2:1,undefined/8, set1=2;set1=9;",
                LOG
                        + "var p = Object.create(Object.prototype, { 1: {"
                        + "  set(v) { log += 'set1=' + v + ';'; }, configurable: true } });"
                        + "Object.setPrototypeOf(Array.prototype, p);"
                        + "r += ' ' + f() + ' ' + log;"
                        + "r");
    }

    @Test
    void otherPrototypes() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2 set0=1;",
                "var log = '';"
                        + "var p = Object.create(Array.prototype, { 0: {"
                        + "  set(v) { log += 'set0=' + v + ';'; } } });"
                        + "var a = []; a.push(0);"
                        + "Object.setPrototypeOf(a = [], p); a.push(1, 2);"
                        + "a.length + ' ' + log");
    }
}
