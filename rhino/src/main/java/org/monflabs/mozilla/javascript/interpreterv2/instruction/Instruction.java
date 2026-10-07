package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.monflabs.mozilla.javascript.interpreterv2.KnownType;

public abstract class Instruction {
    public abstract void interpret(Context cx, CallFrameV2 frame);

    public abstract int stackChange();

    public Instruction simplify(InstructionSimplification simplifier) {
        return this;
    }

    public KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.UNKNOWN;
    }

    public String toDebugString() {
        return getClass().getSimpleName();
    }
}
