package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import static org.monflabs.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Kit;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.monflabs.mozilla.javascript.interpreterv2.operand.Operand;

public class GetPropSuper extends Instruction {
    private final Operand superObject;
    private final String property;
    private final boolean noWarn;

    public GetPropSuper(Operand superObject, String property, boolean noWarn) {
        this.superObject = superObject;
        this.property = property;
        this.noWarn = noWarn;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        var superObject = this.superObject.retrieve(cx, frame);
        if (superObject == DOUBLE_MARK) Kit.codeBug();
        frame.push(
                ScriptRuntime.getSuperProp(
                        superObject, property, cx, frame.scope, frame.thisObj, noWarn));
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1 + superObject.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(
                this, "superObject", superObject, "property", property, "noWarn", noWarn);
    }
}
