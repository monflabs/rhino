package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import static org.monflabs.mozilla.javascript.InterpreterV2.doShallowEquals;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class ShallowNotEqual extends Instruction {
    private final Operand lhs;
    private final Operand rhs;

    public ShallowNotEqual(Operand lhs, Operand rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push(ScriptRuntime.wrapBoolean(!doShallowEquals(cx, frame, lhs, rhs)));

        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + rhs.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "lhs", lhs, "rhs", rhs);
    }
}
