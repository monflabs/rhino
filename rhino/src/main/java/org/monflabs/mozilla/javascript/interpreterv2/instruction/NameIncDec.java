package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;

public class NameIncDec extends Instruction {
    private final String name;
    private final int incrDecrMask;

    public NameIncDec(String name, int incrDecrMask) {
        this.name = name;
        this.incrDecrMask = incrDecrMask;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push(ScriptRuntime.nameIncrDecr(frame.scope, name, cx, incrDecrMask));

        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "name", name, "mask", incrDecrMask);
    }
}
