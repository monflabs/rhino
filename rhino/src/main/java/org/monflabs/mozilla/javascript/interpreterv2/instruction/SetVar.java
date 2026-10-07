package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.monflabs.mozilla.javascript.interpreterv2.KnownType;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class SetVar extends Instruction {
    private final int index;
    private final Operand value;

    public SetVar(int index, Operand value) {
        this.index = index;
        this.value = value;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        if ((frame.getVarAttribute(index) & ScriptableObject.READONLY) == 0) {
            if (value.isDouble(frame)) {
                var doubleValue = value.retrieveDouble(frame);
                frame.setVar(index, doubleValue);
                frame.push(doubleValue);
            } else {
                var value = this.value.retrieve(cx, frame);
                frame.setVar(index, value);
                frame.push(value);
            }
        } else {
            // If it is a constant, we need to consume the value operand
            // but return the result of the operation for compound assignments
            Object newValue;
            if (value.isDouble(frame)) {
                newValue = value.retrieveDouble(frame);
            } else {
                newValue = value.retrieve(cx, frame);
            }
            // For compound assignments like +=, the result is the computed value
            // even though the const variable doesn't change
            frame.push(newValue);
        }
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1 + value.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "index", index, "value", value);
    }

    @Override
    public KnownType getKnownType(InstructionSimplification simplifier) {
        return value.getKnownType(simplifier);
    }
}
