package org.monflabs.mozilla.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class SymbolHasInstanceTest {
    /** custom Symbol.hasInstance must be called */
    @Test
    public void testInstanceofUsesCustomSymbolHasInstance() {
        String script =
                "function MyClass() {}\n"
                        + "Object.defineProperty(MyClass, Symbol.hasInstance, {\n"
                        + "  value: function(obj) { return Array.isArray(obj); }\n"
                        + "});\n"
                        + "[] instanceof MyClass;";

        Utils.assertWithAllModes_ES6(true, script);
    }

    /** non-function objects with Symbol.hasInstance should receive correct argument */
    @Test
    public void testNonFunctionSymbolHasInstanceReceivesCorrectArgument() {
        String script =
                "var receivedArg;\n"
                        + "var obj = {\n"
                        + "  [Symbol.hasInstance]: function(instance) {\n"
                        + "    receivedArg = instance;\n"
                        + "    return true;\n"
                        + "  }\n"
                        + "};\n"
                        + "var testInstance = {marker: 'test'};\n"
                        + "Object.setPrototypeOf(testInstance, obj);\n"
                        + "testInstance instanceof obj;\n"
                        + "receivedArg === testInstance;";

        Utils.assertWithAllModes_ES6(true, script);
    }

    /** instanceof walks the prototype chain to find Symbol.hasInstance */
    @Test
    public void testSymbolHasInstanceInherited() {
        String script =
                "var called = false;\n"
                        + "function BaseFunc() {}\n"
                        + "Object.defineProperty(BaseFunc, Symbol.hasInstance, {\n"
                        + "  value: function() { called = true; return true; }\n"
                        + "});\n"
                        + "function SubFunc() {}\n"
                        + "Object.setPrototypeOf(SubFunc, BaseFunc);\n"
                        + "var result = ({}) instanceof SubFunc;\n"
                        + "called && result;";
        Utils.assertWithAllModes_ES6(true, script);
    }

    /** errors thrown by custom Symbol.hasInstance should be propagated */
    @Test
    public void testSymbolHasInstanceErrorPropagated() {
        String script =
                "function MyClass() {}\n"
                        + "Object.defineProperty(MyClass, Symbol.hasInstance, {\n"
                        + "  value: function(obj) { throw new TypeError('custom error'); }\n"
                        + "});\n"
                        + "try {\n"
                        + "  {} instanceof MyClass;\n"
                        + "} catch (e) {\n"
                        + "  if (e.message === 'custom error') {\n"
                        + "    throw e;\n"
                        + "  }\n"
                        + "  throw new Error('Wrong error caught: ' + e.message);\n"
                        + "}";
        // Rhino wraps JavaScript TypeErrors as EvaluatorException with "syntax error" message
        Utils.assertEvaluatorExceptionES6("syntax error", script);
    }

    /** non-callable Symbol.hasInstance should throw TypeError */
    @Test
    public void testNonCallableSymbolHasInstanceThrows() {
        String script = "o = { [Symbol.hasInstance]: 42 };\n" + "o instanceof o;";
        Utils.assertEcmaErrorES6(
                "TypeError: Target of ``instanceof`` must be callable or have ``[Symbol.hasInstance]`` method.",
                script);
    }

    /** instanceof with the intrinsic @@hasInstance gives OrdinaryHasInstance's results */
    @Test
    public void testOrdinaryHasInstance() {
        String script =
                "function F() {}\n"
                        + "function C() {}\n"
                        + "C.prototype = Object.create(F.prototype);\n"
                        + "var f = new F(), c = new C();\n"
                        + "var B = F.bind(null);\n"
                        + "var r = [f instanceof F, c instanceof F, c instanceof C, f instanceof C,\n"
                        + "  [] instanceof Array, [] instanceof Object, f instanceof B,\n"
                        + "  Object.create(null) instanceof F, 1 instanceof F,\n"
                        + "  Function.prototype[Symbol.hasInstance].call(F, c)];\n"
                        + "F.prototype = 1;\n"
                        + "try { f instanceof F; r.push('no error'); }\n"
                        + "catch (e) { r.push(e instanceof TypeError); }\n"
                        + "r.join();";
        Utils.assertWithAllModes_ES6(
                "true,true,true,false,true,true,true,false,false,true,true", script);
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,true,true,false,true,true,true,false,false,true,true", script);
    }

    /** a host function that overrides hasInstance keeps its override */
    @Test
    public void testHostHasInstanceOverride() {
        for (int version : new int[] {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT}) {
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(version);
                        TopLevel scope = cx.initStandardObjects();
                        BaseFunction host =
                                new BaseFunction(
                                        scope, ScriptableObject.getFunctionPrototype(scope)) {
                                    @Override
                                    public boolean hasInstance(Scriptable instance) {
                                        return ScriptableObject.hasProperty(instance, "host");
                                    }
                                };
                        scope.put("Host", scope, host);
                        Object result =
                                cx.evaluateString(
                                        scope,
                                        "[{host: 1} instanceof Host, {} instanceof Host].join()",
                                        "test",
                                        1,
                                        null);
                        assertEquals("true,false", result);
                        return null;
                    });
        }
    }
}
