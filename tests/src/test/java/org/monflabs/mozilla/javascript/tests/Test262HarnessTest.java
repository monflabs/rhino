/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Context.EvaluationMethod;
import org.monflabs.mozilla.javascript.TopLevel;

class Test262HarnessTest {

    @Test
    void asyncTestThatCompleted() {
        assertNull(Test262SuiteTest.asyncFailure("Test262:AsyncTestComplete\n"));
    }

    @Test
    void asyncTestThatFailed() {
        assertEquals(
                "Test262Error: expected true",
                Test262SuiteTest.asyncFailure(
                        "something\nTest262:AsyncTestFailure:Test262Error: expected true\n"));
    }

    @Test
    void asyncTestThatNeverCompleted() {
        assertEquals(
                "async test did not complete ($DONE was never called)",
                Test262SuiteTest.asyncFailure(""));
    }

    @Test
    void unicodeDependentTests() {
        assertTrue(
                Test262SuiteTest.isUnicodeDependent(
                        new File(
                                "test262/test/built-ins/RegExp/property-escapes/generated/"
                                        + "Script_-_Garay.js")));
        assertTrue(
                Test262SuiteTest.isUnicodeDependent(
                        new File("test262/test/language/identifiers/start-unicode-16.0.0.js")));
        assertFalse(
                Test262SuiteTest.isUnicodeDependent(
                        new File("test262/test/language/identifiers/vals-rus-alpha-lower.js")));
        assertFalse(
                Test262SuiteTest.isUnicodeDependent(
                        new File("test262/test/built-ins/RegExp/property-escapes/grammar.js")));
    }

    @ParameterizedTest
    @EnumSource(EvaluationMethod.class)
    void runawayScriptTimesOutEvenInsideTryCatch(EvaluationMethod method) {
        try (Context cx = Test262SuiteTest.CTX_FACTORY.enterContext()) {
            cx.setEvaluationMethod(method);
            cx.setInstructionObserverThreshold(10_000);
            cx.putThreadLocal(Test262SuiteTest.DEADLINE_KEY, System.nanoTime() + 100_000_000L);
            TopLevel scope = cx.initStandardObjects();
            assertThrows(
                    Test262SuiteTest.TestTimeout.class,
                    () ->
                            cx.evaluateString(
                                    scope,
                                    "while (true) { try { for (;;) {} } catch (e) {} }",
                                    "runaway.js",
                                    1,
                                    null));
        }
    }
}
