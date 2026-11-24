package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class TypeHintsTest {

    private static final ContextFactory HINTS =
            new ContextFactory() {
                @Override
                protected boolean hasFeature(Context cx, int featureIndex) {
                    if (featureIndex == Context.FEATURE_TYPE_HINTS) {
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
    void variablesAndFunctions() {
        assertEquals(
                "6|a-b",
                eval(
                        HINTS,
                        "var x : number = 2, y : java.lang.String; let z : Foo[] = [1];"
                                + " const k : void = undefined;"
                                + " function f(a : number, b : Array[] = [1]) : number { return a * 3; }"
                                + " function g(s : String, t : java.lang.String) : String"
                                + " { return s + '-' + t; }"
                                + " f(x) + '|' + g('a', 'b')"));
    }

    @Test
    void ternaryAndLabelsAreUnaffected() {
        assertEquals(
                "2|3",
                eval(
                        HINTS,
                        "var a = 1; var r = a ? 2 : 3; lbl: for (;;) { break lbl; }"
                                + " var o = {p: function(q) { return q ? 3 : 4; }};"
                                + " r + '|' + o.p(1)"));
    }

    @Test
    void rejectedWithoutTheFeature() {
        assertThrows(
                EvaluatorException.class, () -> eval(new ContextFactory(), "var x : number = 1;"));
    }
}
