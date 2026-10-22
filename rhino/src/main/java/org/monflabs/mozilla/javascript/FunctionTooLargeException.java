/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import org.monflabs.mozilla.classfile.ClassFileWriter.ClassSizeException;

/**
 * A function whose method exceeds a class file limit: the compilation parses the script again with
 * the function split into smaller methods (Splitter).
 */
public class FunctionTooLargeException extends ClassSizeException {
    private static final long serialVersionUID = 1L;

    /** The key of the top-level code of a script. */
    public static final int SCRIPT = Splitter.SCRIPT_KEY;

    private final int functionKey;

    public FunctionTooLargeException(String message, int functionKey) {
        super(message, true);
        this.functionKey = functionKey;
    }

    /** The source position that identifies the function (FunctionNode#getSplitKey). */
    public int getFunctionKey() {
        return functionKey;
    }
}
