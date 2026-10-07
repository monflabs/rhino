package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import static org.monflabs.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Kit;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class SetPropSuper extends Instruction {
    private final Operand superObject;
    private final String property;
    private final Operand rhs;

    public SetPropSuper(Operand superObject, String property, Operand rhs) {
        this.superObject = superObject;
        this.property = property;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        var rhs = this.rhs.retrieveAndWrap(cx, frame);
        var superObject = this.superObject.retrieve(cx, frame);
        if (superObject == DOUBLE_MARK) Kit.codeBug();

        frame.push(
                ScriptRuntime.setSuperProp(
                        superObject, property, rhs, cx, frame.scope, frame.thisObj));
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1 + superObject.stackChange() + rhs.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(
                this, "superObject", superObject, "property", property, "rhs", rhs);
    }
}
