/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debug;

import org.monflabs.mozilla.javascript.Context;

/**
 * Implemented by a {@link Debugger} that wants the instruction counts of the interpreter, to pause
 * or terminate a script that reaches no new line.
 */
public interface InstructionObserver {
    void observeInstructionCount(Context cx, int instructionCount);
}
