/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * The literal ids of interpreted code keep no node of the syntax tree: a computed key, set at run
 * time, kept the whole tree of the script alive through the parents of its AST nodes.
 */
class InterpretedLiteralIdsTest {

    private static final String SOURCE =
            "var k = 'b', s = Symbol.iterator;\n"
                    + "function f(n) { return { a: n, [k + n]: n * 2, [s]: () => 1, c: 3 }; }\n"
                    + "class C { static [k]() { return 4; } ['m' + k]() { return 5; } }\n"
                    + "var o = f(1), p = { [`${k}x`]: function () { return { [k]: 6 }; } };\n"
                    + "[Object.keys(o).join(), typeof o[s], C.b(), new C().mb(), p.bx().b].join(';');";

    @Test
    void computedKeysWithoutTheirNodes() {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInterpretedMode(true);
            TopLevel scope = cx.initStandardObjects();
            JSScript script = (JSScript) cx.compileString(SOURCE, "ids.js", 1, null);
            assertFalse(holdsNode(script.getDescriptor()));
            assertEquals(
                    "a,b1,c;function;4;5;6",
                    Context.toString(script.exec(cx, scope, scope.getGlobalThis())));
        }
    }

    private static boolean holdsNode(JSDescriptor<?> descriptor) {
        if (descriptor.getCode() instanceof InterpreterData) {
            Object[] ids = ((InterpreterData<?>) descriptor.getCode()).literalIds;
            if (ids != null && holdsNode(ids)) {
                return true;
            }
        }
        for (JSDescriptor<?> nested : descriptor.getNestedFunctions()) {
            if (holdsNode(nested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsNode(Object[] values) {
        for (Object value : values) {
            if (value instanceof Node
                    || (value instanceof Object[] && holdsNode((Object[]) value))) {
                return true;
            }
        }
        return false;
    }
}
