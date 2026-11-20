/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IcodeSpaceTest {

    /**
     * An icode is one signed byte. The variants of an operation take an operand byte (see
     * Icode#ITER_OP), not an icode each, so that the space does not run out.
     */
    @Test
    void icodesAreLeft() {
        int free = Icode.MIN_ICODE - Byte.MIN_VALUE;
        assertTrue(free >= 12, free + " icodes left: give the variants of an operation an operand");
    }
}
