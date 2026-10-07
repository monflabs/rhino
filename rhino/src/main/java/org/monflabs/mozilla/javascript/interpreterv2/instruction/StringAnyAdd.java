package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import static org.monflabs.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.ConsString;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.monflabs.mozilla.javascript.interpreterv2.KnownType;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class StringAnyAdd extends Instruction {
    private final Operand lhs;
    private final Operand rhs;

    public StringAnyAdd(Operand lhs, Operand rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        double doubleValue = 0.0;
        Object value;
        if (rhs.isDouble(frame)) {
            doubleValue = rhs.retrieveDouble(frame);
            value = DOUBLE_MARK;
        } else {
            value = rhs.retrieve(cx, frame);
        }

        CharSequence rhsValue;
        if (value == DOUBLE_MARK) {
            rhsValue = ScriptRuntime.numberToString(doubleValue, 10);
        } else if (value instanceof Scriptable) {
            Object primitive = ScriptRuntime.toPrimitive(value);
            rhsValue =
                    (primitive instanceof CharSequence)
                            ? (CharSequence) primitive
                            : ScriptRuntime.toString(primitive);
        } else if (value instanceof CharSequence) {
            rhsValue = (CharSequence) value;
        } else {
            rhsValue = ScriptRuntime.toCharSequence(value);
        }

        CharSequence lhsValue = (CharSequence) lhs.retrieve(cx, frame);

        frame.push(new ConsString(lhsValue, rhsValue));
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + rhs.stackChange();
    }

    @Override
    public KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.STRING;
    }
}
