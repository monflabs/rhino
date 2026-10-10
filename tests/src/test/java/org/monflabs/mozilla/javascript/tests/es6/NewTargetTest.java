package org.monflabs.mozilla.javascript.tests.es6;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.EcmaError;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Test for new.target. */
public class NewTargetTest {

    @Test
    public void global() {
        Utils.assertException(-1, EvaluatorException.class, "Invalid use of new.", "new.target");
    }

    @Test
    public void globalTypeof() {
        Utils.assertException(
                -1, EvaluatorException.class, "Invalid use of new.", "typeof new.target");
    }

    @Test
    public void eval() {
        Utils.assertException(
                -1, EcmaError.class, "SyntaxError: Invalid use of new.", "eval('new.target;')");
    }

    @Test
    public void evalInsideFunction() {
        Utils.assertWithAllModes("function", "function foo() { eval('new.target;') } typeof foo");
        // direct eval code in a function sees its new.target
        Utils.assertWithAllModes(
                "undefined,true",
                "var r; function foo() { r = eval('new.target;'); }"
                        + " foo(); var a = typeof r; new foo(); [a, r === foo].join()");
    }

    @Test
    public void evalInsideArrowFunction() {
        Utils.assertWithAllModes("function", "f = () => eval('new.target;'); typeof f");
        Utils.assertException(
                -1,
                EcmaError.class,
                "SyntaxError: Invalid use of new.",
                "f = () => eval('new.target;');  f()");
    }

    @Test
    public void typeofFromFunction() {
        Utils.assertWithAllModes("undefined", "function foo() { return typeof new.target } foo();");
    }
}
