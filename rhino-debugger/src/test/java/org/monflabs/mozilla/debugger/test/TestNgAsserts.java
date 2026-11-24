/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.test;

import org.junit.jupiter.api.Assertions;

/** The assertions of the ported tests, which pass the actual value first. */
final class TestNgAsserts {
    private TestNgAsserts() {}

    static void assertEquals(Object actual, Object expected) {
        Assertions.assertEquals(expected, actual);
    }

    static void assertEquals(Object actual, Object expected, String message) {
        Assertions.assertEquals(expected, actual, message);
    }

    static void assertEquals(int actual, int expected) {
        Assertions.assertEquals(expected, actual);
    }

    static void assertEquals(long actual, long expected) {
        Assertions.assertEquals(expected, actual);
    }

    static void assertTrue(boolean condition) {
        Assertions.assertTrue(condition);
    }

    static void assertTrue(boolean condition, String message) {
        Assertions.assertTrue(condition, message);
    }

    static void assertNotNull(Object o) {
        Assertions.assertNotNull(o);
    }

    static void assertNotNull(Object o, String message) {
        Assertions.assertNotNull(o, message);
    }

    static void fail(String message) {
        Assertions.fail(message);
    }
}
