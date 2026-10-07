package org.monflabs.mozilla.javascript.interpreterv2.operand;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.monflabs.mozilla.javascript.interpreterv2.KnownType;

public final class IntOperand extends Operand {

    private final int value;

    public IntOperand(int value) {
        this.value = value;
    }

    @Override
    public Integer retrieve(Context cx, CallFrameV2 frame) {
        return value;
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        return value;
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return false;
    }

    @Override
    public KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.NUMBER;
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append(value);
    }

    @Override
    public boolean isValidJumpTableKey() {
        return true;
    }

    @Override
    public boolean coerceToBoolean(Context cx, CallFrameV2 frame) {
        return value != 0;
    }
}
