package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import java.math.BigInteger;
import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class BitNot extends Instruction {
    private final Operand obj;

    public BitNot(Operand obj) {
        this.obj = obj;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        var value = this.obj.retrieveNumber(cx, frame);

        var result = ScriptRuntime.bitwiseNOT(value);

        if (result instanceof BigInteger) {
            frame.push(result);
        } else {
            frame.push(result.doubleValue());
        }
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
