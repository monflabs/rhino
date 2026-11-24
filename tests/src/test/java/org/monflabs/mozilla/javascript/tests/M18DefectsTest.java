package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.ClassShutter;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class M18DefectsTest {

    @Test
    public void sortComparatorMinusZeroAndNaNAreEqual() {
        Utils.assertWithAllModes_ES6("3,1,2", "[3,1,2].sort(function() { return -0; }).join()");
        Utils.assertWithAllModes_ES6("3,1,2", "[3,1,2].sort(function() { return NaN; }).join()");
    }

    public static class Host {
        public String[] names() {
            return new String[] {"a", "b"};
        }

        public Object[] objects() {
            return new Object[] {"a", 1};
        }

        public Object symbolIn(Object o) {
            return o;
        }

        public Object[] holder(Object[] a) {
            return a;
        }
    }

    private static Object eval(ClassShutter shutter, String script) {
        Object[] result = new Object[1];
        Utils.runWithAllModes(
                new ContextFactory(),
                cx -> {
                    var scope = cx.initStandardObjects();
                    if (shutter != null) {
                        cx.getClassShutterSetter().setClassShutter(shutter);
                    }
                    ScriptableObject.putProperty(
                            scope, "host", Context.javaToJS(new Host(), scope));
                    result[0] = cx.evaluateString(scope, script, "t", 1, null);
                    return null;
                });
        return result[0];
    }

    @Test
    public void symbolRoundTripsThroughJava() {
        assertEquals(true, eval(null, "var s = Symbol('x'); host.symbolIn(s) === s"));
        assertEquals("symbol", eval(null, "typeof host.symbolIn(Symbol('x'))"));
    }

    @Test
    public void holesInArraysPassedToJavaAreUndefined() {
        assertEquals(
                "a,undefined,c",
                String.valueOf(
                        eval(
                                null,
                                "String(Array.prototype.join.call(host.holder(['a',,'c']), ','))")));
    }

    @Test
    public void classShutterDoesNotBlockJavaArrays() {
        ClassShutter shutter =
                name ->
                        name.equals(Host.class.getName())
                                || name.equals("java.lang.String")
                                || name.equals("java.lang.Object");
        assertEquals(2, ((Number) eval(shutter, "host.names().length")).intValue());
        assertEquals("b", String.valueOf(eval(shutter, "String(host.names()[1])")));
        assertEquals(2, ((Number) eval(shutter, "host.objects().length")).intValue());
        assertThrows(
                EvaluatorException.class,
                () -> eval(name -> name.equals(Host.class.getName()), "host.names()"));
    }

    @Test
    public void symbolRegistryIsStable() {
        assertEquals(true, eval(null, "Symbol.for('k') === Symbol.for('k')"));
        assertEquals("k", eval(null, "Symbol.keyFor(Symbol.for('k'))"));
        assertEquals(true, eval(null, "Symbol.keyFor(Symbol('k')) === undefined"));
    }

    @Test
    public void sealedScopeSealsSymbolAndTypedArrayBase() {
        for (int version : new int[] {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT}) {
            try (Context cx = Context.enter()) {
                cx.setLanguageVersion(version);
                TopLevel scope = cx.initSafeStandardObjects(null, true);
                String[] targets = {
                    "Symbol",
                    "Symbol.prototype",
                    "Object.getPrototypeOf(Int8Array)",
                    "Object.getPrototypeOf(Int8Array).prototype",
                    "Int8Array",
                    "Int8Array.prototype",
                    "Map.prototype"
                };
                for (String target : targets) {
                    Object threw =
                            cx.evaluateString(
                                    scope,
                                    "try { " + target + ".zz = 1; false } catch (e) { true }",
                                    "sealed",
                                    1,
                                    null);
                    assertEquals(true, threw, target + " in version " + version);
                }
            }
        }
    }

    @Test
    public void unsealedScopeStaysWritable() {
        try (Context cx = Context.enter()) {
            TopLevel scope = cx.initSafeStandardObjects(null, false);
            assertEquals(
                    1.0,
                    ((Number)
                                    cx.evaluateString(
                                            scope,
                                            "Symbol.prototype.zz = 1;"
                                                    + " Object.getPrototypeOf(Int8Array).prototype.yy = 1;"
                                                    + " Symbol.prototype.zz",
                                            "unsealed",
                                            1,
                                            null))
                            .doubleValue());
        }
    }
}
