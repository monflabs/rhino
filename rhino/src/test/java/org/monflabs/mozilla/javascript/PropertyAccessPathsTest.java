package org.monflabs.mozilla.javascript;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The named property gets, sets and method calls, on objects and on the other values. */
public class PropertyAccessPathsTest {
    @Test
    public void getsOnObjectsAndPrimitives() {
        String script =
                "var o = {a: 1}, n = 0;\n"
                        + "Object.defineProperty(o, 'g', {get: function() { n++; return 2; }});\n"
                        + "String.prototype.p = 3;\n"
                        + "var r = [o.a, o.g, o.missing, 'abc'.length, 'abc'.p, (5).toFixed, true.p];\n"
                        + "r[5] = typeof r[5];\n"
                        + "try { undefined.a; } catch (e) { r.push(e instanceof TypeError); }\n"
                        + "r.push(n);\n"
                        + "r.join();";
        String expected = "1,2,,3,3,function,,true,1";
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    public void setsOnObjectsAndPrimitives() {
        String script =
                "var o = {};\n"
                        + "o.a = 1;\n"
                        + "var s = 'abc';\n"
                        + "s.p = 2;\n"
                        + "var r = [o.a, s.p];\n"
                        + "try { null.a = 1; } catch (e) { r.push(e instanceof TypeError); }\n"
                        + "r.join();";
        Utils.assertWithAllModes_ES6("1,,true", script);
        Utils.assertWithAllModes_ECMASCRIPT("1,,true", script);
    }

    @Test
    public void methodCalls() {
        String script =
                "var n = 0;\n"
                        + "var o = {m: function() { return this === o; }, v: 1};\n"
                        + "Object.defineProperty(o, 'g', {get: function() { n++;"
                        + " return function() { return 'g'; }; }});\n"
                        + "var p = {__noSuchMethod__: function(name, args) { return name + args[0]; }};\n"
                        + "var r = [o.m(), o.g(), 'abc'.charAt(1), (1.5).toFixed(0), p.x(7),\n"
                        + "  o.missing?.(), n];\n"
                        + "try { o.v(); } catch (e) { r.push(e instanceof TypeError); }\n"
                        + "try { o.missing(); } catch (e) { r.push(e instanceof TypeError); }\n"
                        + "try { undefined.m(); } catch (e) { r.push(e instanceof TypeError); }\n"
                        + "r.join();";
        String expected = "true,g,b,2,x7,,1,true,true,true";
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }
}
