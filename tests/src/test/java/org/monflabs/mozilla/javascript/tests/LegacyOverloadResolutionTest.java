package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class LegacyOverloadResolutionTest {

    public static class Host {
        public String nul(String s) {
            return "string";
        }

        public String nul(Object o) {
            return "object";
        }

        public String arr(int[] a) {
            return "int[]";
        }

        public String arr(String[] a) {
            return "String[]";
        }

        public String fixed(String s) {
            return "fixed";
        }

        public String fixed(String... s) {
            return "vararg";
        }
    }

    private static ContextFactory factory(boolean legacy) {
        return new ContextFactory() {
            @Override
            protected boolean hasFeature(Context cx, int featureIndex) {
                if (featureIndex == Context.FEATURE_LEGACY_OVERLOAD_RESOLUTION) {
                    return legacy;
                }
                return super.hasFeature(cx, featureIndex);
            }
        };
    }

    private static Object eval(boolean legacy, String script) {
        Object[] result = new Object[1];
        Utils.runWithAllModes(
                factory(legacy),
                cx -> {
                    var scope = cx.initStandardObjects();
                    ScriptableObject.putProperty(
                            scope, "host", Context.javaToJS(new Host(), scope));
                    result[0] = cx.evaluateString(scope, script, "t", 1, null);
                    return null;
                });
        return result[0];
    }

    @Test
    void legacyRanksAnArrayByItsFirstElement() {
        assertEquals("String[]|int[]", eval(true, "host.arr(['a']) + '|' + host.arr([1, 2])"));
        assertEquals(
                "String[]|String[]",
                eval(true, "host.arr([null, 'a']) + '|' + host.arr([undefined, 'b'])"));
    }

    @Test
    void legacyNonVarargBeatsVarargOnATie() {
        assertEquals("fixed", eval(true, "String(host.fixed('a'))"));
    }
}
