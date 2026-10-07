package org.monflabs.mozilla.javascript.interpreterv2.instruction;

import org.monflabs.mozilla.javascript.CallFrameV2;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.interpreterv2.InstructionFormatter;

public class Regexp extends Instruction {
    private final Object regexLiteral;

    public Regexp(Object regexLiteral) {
        this.regexLiteral = regexLiteral;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push(ScriptRuntime.wrapRegExp(cx, frame.scope, regexLiteral));
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "regexLiteral", regexLiteral);
    }
}
