package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Objects created by {@code new} start with a hash table rather than an empty slot map. */
public class ConstructedObjectMapTest {

    private static final String SCRIPT =
            "function P() {}\n"
                    + "function Q(a, b) { this.a = a; this.b = b; }\n"
                    + "Q.prototype.sum = function() { return this.a + this.b; };\n"
                    + "var empty = new P();\n"
                    + "var q = new Q(1, 2);\n"
                    + "var many = new P();\n"
                    + "for (var i = 0; i < 20; i++) many['k' + i] = i;\n"
                    + "delete many.k3; delete many.k10; many.k3 = 'again';\n"
                    + "var keys = Object.keys(many);\n"
                    + "[Object.keys(empty).length, q.sum(), Object.keys(q).join(''),\n"
                    + " keys.length, keys[0], keys[2], keys[3], keys[keys.length - 1], many.k3,\n"
                    + " 'k10' in many, q instanceof Q, Object.getPrototypeOf(empty) === P.prototype].join(',')";

    private static final String EXPECTED = "0,3,ab,19,k0,k2,k4,k3,again,false,true,true";

    @Test
    public void constructedObjects() {
        Utils.assertWithAllModes_ES6(EXPECTED, SCRIPT);
        Utils.assertWithAllModes_ECMASCRIPT(EXPECTED, SCRIPT);
    }

    @Test
    public void constructedObjectsThreadSafe() {
        ContextFactory factory =
                Utils.contextFactoryWithFeatures(Context.FEATURE_THREAD_SAFE_OBJECTS);
        for (boolean interpreted : new boolean[] {false, true}) {
            try (Context cx = factory.enterContext()) {
                cx.setInterpretedMode(interpreted);
                TopLevel scope = cx.initStandardObjects();
                assertEquals(EXPECTED, cx.evaluateString(scope, SCRIPT, "test", 1, null));
            }
        }
    }
}
