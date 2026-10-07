package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.JSFunction;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

class NestedFunctionStrictFlagTest {
    @Test
    void functionNestedInAStrictFunctionAreStrict() {
        String script = "(() => {\n  'use strict';\n  return function nested() {}\n})()";

        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    TopLevel topLevel = new TopLevel();
                    cx.initStandardObjects(topLevel);

                    Object res = cx.evaluateString(topLevel, script, "test", 1, null);
                    JSFunction function = assertInstanceOf(JSFunction.class, res);
                    assertTrue(
                            function.isStrict(),
                            () ->
                                    "should be strict in "
                                            + (cx.isInterpretedMode() ? "interpreted" : "compiled")
                                            + " mode");

                    return null;
                });
    }
}
