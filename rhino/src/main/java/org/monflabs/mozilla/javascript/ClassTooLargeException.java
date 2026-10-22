/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import org.monflabs.mozilla.classfile.ClassFileWriter.ClassSizeException;

/**
 * A script whose class exceeds a class file limit (its constant pool, its invokedynamic sites): the
 * compilation spreads its functions over more classes.
 */
public class ClassTooLargeException extends ClassSizeException {
    private static final long serialVersionUID = 1L;

    private final int generated;
    private final int total;

    /**
     * @param generated the number of functions generated when the class exceeded the limit
     * @param total the number of functions of the class
     */
    public ClassTooLargeException(String message, int generated, int total) {
        super(message);
        this.generated = generated;
        this.total = total;
    }

    public int getGenerated() {
        return generated;
    }

    public int getTotal() {
        return total;
    }
}
