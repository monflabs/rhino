package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class Pos extends Instruction {
    private final Operand obj;

    public Pos(Operand obj) {
        this.obj = obj;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        var value = this.obj.coerceToDouble(cx, frame);

        frame.push(value);
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1 + obj.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "obj", obj);
    }
}
