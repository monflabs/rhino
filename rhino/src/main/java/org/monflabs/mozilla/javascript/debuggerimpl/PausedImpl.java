/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import java.util.List;
import java.util.concurrent.Callable;
import org.monflabs.mozilla.api.debugger.DebugFrame;
import org.monflabs.mozilla.api.debugger.ExecutionContext;
import org.monflabs.mozilla.api.debugger.PauseReason;
import org.monflabs.mozilla.api.debugger.PausedEvent;

final class PausedImpl implements PausedEvent {
    private final DebuggerImpl debugger;
    private final Thread thread;
    private final PauseReason reason;
    private final List<DebugFrame> frames;
    private final List<String> hits;
    private final Object exception;
    private final ExecutionContext context;
    private boolean resumed;

    volatile DebuggerImpl.Step step = DebuggerImpl.Step.NONE;
    volatile boolean terminated;

    PausedImpl(
            DebuggerImpl debugger,
            Thread thread,
            PauseReason reason,
            List<DebugFrame> frames,
            List<String> hits,
            Object exception,
            ExecutionContext context) {
        this.debugger = debugger;
        this.thread = thread;
        this.reason = reason;
        this.frames = frames;
        this.hits = hits;
        this.exception = exception;
        this.context = context;
    }

    synchronized void await() {
        while (!resumed) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminated = true;
                resumed = true;
            }
        }
    }

    private synchronized void release() {
        resumed = true;
        notifyAll();
    }

    @Override
    public Thread thread() {
        return thread;
    }

    @Override
    public PauseReason reason() {
        return reason;
    }

    @Override
    public List<DebugFrame> frames() {
        return frames;
    }

    @Override
    public List<String> hitBreakpoints() {
        return hits;
    }

    @Override
    public Object exception() {
        return exception;
    }

    @Override
    public ExecutionContext context() {
        return context;
    }

    @Override
    public synchronized boolean isResumed() {
        return resumed;
    }

    @Override
    public void resume() {
        step = DebuggerImpl.Step.NONE;
        release();
    }

    @Override
    public void stepInto() {
        step = DebuggerImpl.Step.INTO;
        release();
    }

    @Override
    public void stepOver() {
        step = DebuggerImpl.Step.OVER;
        release();
    }

    @Override
    public void stepOut() {
        step = DebuggerImpl.Step.OUT;
        release();
    }

    @Override
    public void terminate() {
        terminated = true;
        release();
    }

    @Override
    public <T> T call(Callable<T> operation) throws Exception {
        return debugger.run(cx -> operation.call());
    }
}
