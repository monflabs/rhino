/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import java.util.List;
import org.monflabs.mozilla.api.debugger.DebugException;
import org.monflabs.mozilla.api.debugger.DebugFrame;
import org.monflabs.mozilla.api.debugger.DebugScope;
import org.monflabs.mozilla.api.debugger.Location;

final class FrameView implements DebugFrame {
    private final DebuggerImpl debugger;
    private final DebuggerImpl.FrameState frame;
    private final int index;

    FrameView(DebuggerImpl debugger, DebuggerImpl.FrameState frame, int index) {
        this.debugger = debugger;
        this.frame = frame;
        this.index = index;
    }

    @Override
    public String id() {
        return Integer.toString(index);
    }

    @Override
    public String functionName() {
        String name = frame.function.getFunctionName();
        return name == null ? "" : name;
    }

    @Override
    public Location location() {
        return frame.location();
    }

    @Override
    public Location functionLocation() {
        int[] lines = frame.function.getLineNumbers();
        int first = lines == null || lines.length == 0 ? 1 : lines[0];
        for (int l : lines == null ? new int[0] : lines) {
            first = Math.min(first, l);
        }
        return new Location(frame.script, Math.max(0, first - 1), 0);
    }

    @Override
    public List<DebugScope> scopes() {
        return DebuggerImpl.scopesOf(frame);
    }

    @Override
    public Object thisValue() {
        return frame.thisObj;
    }

    @Override
    public Object evaluate(String expression) throws DebugException {
        try {
            return debugger.run(
                    cx -> cx.evaluateString(frame.evalScope(), expression, "<debugger>", 1, null));
        } catch (Exception e) {
            throw DebuggerImpl.asDebugException(e);
        }
    }
}
