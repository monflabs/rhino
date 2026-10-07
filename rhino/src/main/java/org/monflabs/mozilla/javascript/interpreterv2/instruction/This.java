package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;

public class This extends Instruction {
    public static final This instance = new This();

    private This() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push(frame.thisObj);
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this);
    }
}
