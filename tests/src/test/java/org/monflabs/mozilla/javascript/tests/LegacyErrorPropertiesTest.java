package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class LegacyErrorPropertiesTest {

    private static final ContextFactory LEGACY =
            new ContextFactory() {
                @Override
                protected boolean hasFeature(Context cx, int featureIndex) {
                    if (featureIndex == Context.FEATURE_LEGACY_ERROR_PROPERTIES) {
                        return true;
                    }
                    return super.hasFeature(cx, featureIndex);
                }
            };

    private static Object eval(ContextFactory factory, String script) {
        Object[] result = new Object[1];
        Utils.runWithAllModes(
                factory,
                cx -> {
                    var scope = cx.initStandardObjects();
                    result[0] = cx.evaluateString(scope, script, "t", 1, null);
                    return null;
                });
        return result[0];
    }

    @Test
    void defaultKeepsTheStandardProperties() {
        assertEquals(
                "|function|function",
                eval(
                        new ContextFactory(),
                        "var k=''; for (var p in new Error('x')) k += p;"
                                + " k + '|' + typeof CallSite + '|' + typeof Error.captureStackTrace"));
    }

    @Test
    void namesAreEnumerable() {
        assertEquals(
                "message,name",
                eval(LEGACY, "var k=[]; for (var p in new Error('x')) k.push(p); k.sort().join()"));
    }

    @Test
    void stackIsEnumerableOnceThrown() {
        assertEquals(
                "false|true",
                eval(
                        LEGACY,
                        "var e = new Error('x');"
                                + " var before = Object.prototype.hasOwnProperty.call(e, 'stack');"
                                + " try { throw e; } catch (x) {}"
                                + " var k=[]; for (var p in e) k.push(p);"
                                + " before + '|' + (k.indexOf('stack') >= 0)"));
    }

    @Test
    void stackTraceApiIsHidden() {
        assertEquals(
                "undefined|undefined|undefined",
                eval(
                                LEGACY,
                                "typeof CallSite + '|' + typeof Error.stackTraceLimit + '|'"
                                        + " + typeof Error.prepareStackTrace")
                        .toString()
                        .replace("object", "undefined"));
    }
}
