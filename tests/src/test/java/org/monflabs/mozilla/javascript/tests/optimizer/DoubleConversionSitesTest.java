package org.monflabs.mozilla.javascript.tests.optimizer;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The conversion sites of a value first seen as a double are linked without a guard, so they must
 * convert every other value they see later as the generic path does.
 */
public class DoubleConversionSitesTest {

    private static final String SCRIPT =
            "function bool(x) { return x ? 't' : 'f'; }\n"
                    + "function not(x) { return !x; }\n"
                    + "function num(x) { return +x; }\n"
                    + "function neg(x) { return -x; }\n"
                    + "function inc(x) { return ++x; }\n"
                    + "function i32(x) { return x | 0; }\n"
                    + "function u32(x) { return x >>> 0; }\n"
                    + "for (var i = 0; i < 2000; i++) {\n"
                    + "  bool(i + 0.5); not(i + 0.5); num(i + 0.5); neg(i + 0.5);\n"
                    + "  inc(i + 0.5); i32(i + 0.5); u32(i + 0.5);\n"
                    + "}\n"
                    + "var values = [0.5, 0, -0, NaN, 3, '', '7', 'x', null, undefined, true, false,\n"
                    + "    {}, [], [2], {valueOf: function() { return 4.5; }}, -1.5, 4294967296.5];\n"
                    + "values.map(function(v) {\n"
                    + "  return [bool(v), not(v), num(v), neg(v), inc(v), i32(v), u32(v)].join(',');\n"
                    + "}).join(' | ')";

    private static final String EXPECTED =
            "t,false,0.5,-0.5,1.5,0,0 | f,true,0,0,1,0,0 | f,true,0,0,1,0,0"
                    + " | f,true,NaN,NaN,NaN,0,0 | t,false,3,-3,4,3,3 | f,true,0,0,1,0,0"
                    + " | t,false,7,-7,8,7,7 | t,false,NaN,NaN,NaN,0,0 | f,true,0,0,1,0,0"
                    + " | f,true,NaN,NaN,NaN,0,0 | t,false,1,-1,2,1,1 | f,true,0,0,1,0,0"
                    + " | t,false,NaN,NaN,NaN,0,0 | t,false,0,0,1,0,0 | t,false,2,-2,3,2,2"
                    + " | t,false,4.5,-4.5,5.5,4,4 | t,false,-1.5,1.5,-0.5,-1,4294967295"
                    + " | t,false,4294967296.5,-4294967296.5,4294967297.5,0,0";

    @Test
    public void otherValuesAfterDoubles() {
        Utils.assertWithAllModes_ES6(EXPECTED, SCRIPT);
        Utils.assertWithAllModes_ECMASCRIPT(EXPECTED, SCRIPT);
    }

    @Test
    public void bigIntAfterDoubles() {
        String script =
                "function neg(x) { return -x; }\n"
                        + "function inc(x) { return ++x; }\n"
                        + "function bool(x) { return x ? 't' : 'f'; }\n"
                        + "for (var i = 0; i < 2000; i++) { neg(i + 0.5); inc(i + 0.5); bool(i + 0.5); }\n"
                        + "[neg(5n), inc(5n), bool(0n), bool(1n)].join(',')";
        Utils.assertWithAllModes_ECMASCRIPT("-5,6,f,t", script);
    }
}
