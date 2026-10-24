/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.List;
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

    /** The other functions of the script found too large in the same compilation. */
    private final List<FunctionTooLargeException> others = new ArrayList<>();

    public FunctionTooLargeException(String message, int functionKey) {
        this(message, functionKey, false);
    }

    public FunctionTooLargeException(String message, int functionKey, boolean jumpOffset) {
        super(message, true, jumpOffset);
        this.functionKey = functionKey;
    }

    /** The source position that identifies the function (FunctionNode#getSplitKey). */
    public int getFunctionKey() {
        return functionKey;
    }

    /** This function and the others found too large with it. */
    public List<FunctionTooLargeException> getFunctions() {
        List<FunctionTooLargeException> all = new ArrayList<>();
        all.add(this);
        all.addAll(others);
        return all;
    }

    public void addOther(FunctionTooLargeException other) {
        others.add(other);
    }
}
