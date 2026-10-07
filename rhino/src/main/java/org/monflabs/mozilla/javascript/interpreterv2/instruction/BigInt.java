package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import java.math.BigInteger;
import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;

public class BigInt extends Instruction {
    private final BigInteger value;

    public BigInt(BigInteger value) {
        this.value = value;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push(value);
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "value", value);
    }
}
