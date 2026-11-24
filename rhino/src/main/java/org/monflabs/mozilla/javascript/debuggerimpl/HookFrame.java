/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.VarScope;
import org.monflabs.mozilla.javascript.debug.DebugFrame;
import org.monflabs.mozilla.javascript.debug.DebuggableScript;

final class HookFrame implements DebugFrame {
    private final DebuggerImpl debugger;
    private final DebuggerImpl.FrameState frame;

    HookFrame(DebuggerImpl debugger, ScriptImpl script, DebuggableScript function) {
        this.debugger = debugger;
        this.frame = new DebuggerImpl.FrameState(script, function);
    }

    @Override
    public void onEnter(Context cx, VarScope activation, Object thisObj, Object[] args) {
        frame.activation = activation;
        frame.thisObj = thisObj;
        DebuggerImpl.ContextImpl context = debugger.contextOf(activation);
        frame.top = (VarScope) context.top;
        script().setContext(context);
        debugger.frameEntered(debugger.state(), frame);
    }

    @Override
    public void onLineChange(Context cx, int lineNumber) {
        frame.line = lineNumber;
        debugger.lineReached(debugger.state(), frame, lineNumber);
    }

    @Override
    public void onExceptionThrown(Context cx, Throwable ex) {
        debugger.exceptionThrown(debugger.state(), frame, ex);
    }

    @Override
    public void onExit(Context cx, boolean byThrow, Object resultOrException) {
        debugger.frameExited(debugger.state(), frame, byThrow, resultOrException);
    }

    @Override
    public void onDebuggerStatement(Context cx) {
        debugger.debuggerStatement(debugger.state());
    }

    private ScriptImpl script() {
        return frame.script;
    }
}
