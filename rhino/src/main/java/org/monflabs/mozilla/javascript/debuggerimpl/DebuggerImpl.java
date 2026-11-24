/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.monflabs.mozilla.api.debugger.Breakpoint;
import org.monflabs.mozilla.api.debugger.BreakpointRequest;
import org.monflabs.mozilla.api.debugger.ConsoleEvent;
import org.monflabs.mozilla.api.debugger.DebugException;
import org.monflabs.mozilla.api.debugger.DebugFrame;
import org.monflabs.mozilla.api.debugger.DebugListener;
import org.monflabs.mozilla.api.debugger.DebugScope;
import org.monflabs.mozilla.api.debugger.DebugScript;
import org.monflabs.mozilla.api.debugger.DebugValues;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.api.debugger.ExceptionEvent;
import org.monflabs.mozilla.api.debugger.ExecutionContext;
import org.monflabs.mozilla.api.debugger.Location;
import org.monflabs.mozilla.api.debugger.PauseOnExceptions;
import org.monflabs.mozilla.api.debugger.PauseReason;
import org.monflabs.mozilla.api.debugger.PausedEvent;
import org.monflabs.mozilla.api.debugger.ScriptTerminated;
import org.monflabs.mozilla.api.debugger.TraceListener;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.JavaScriptException;
import org.monflabs.mozilla.javascript.RhinoException;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.VarScope;
import org.monflabs.mozilla.javascript.debug.DebuggableScript;

/**
 * The debugger of a {@link ContextFactory}, over the debug hooks of the interpreter: every context
 * of the factory runs interpreted, and the hooks report each line a script reaches.
 */
public final class DebuggerImpl
        implements Debugger,
                org.monflabs.mozilla.javascript.debug.Debugger,
                org.monflabs.mozilla.javascript.debug.InstructionObserver,
                ContextFactory.Listener {

    private static final Map<ContextFactory, DebuggerImpl> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static Debugger of(ContextFactory factory) {
        synchronized (INSTANCES) {
            DebuggerImpl debugger = INSTANCES.get(factory);
            if (debugger == null) {
                debugger = new DebuggerImpl(factory);
                factory.addListener(debugger);
                INSTANCES.put(factory, debugger);
            }
            return debugger;
        }
    }

    enum Step {
        NONE,
        INTO,
        OVER,
        OUT
    }

    final ContextFactory factory;
    private final List<DebugListener> listeners = new CopyOnWriteArrayList<>();
    private final List<TraceListener> traceListeners = new CopyOnWriteArrayList<>();
    private final List<ScriptImpl> scripts = new CopyOnWriteArrayList<>();
    private final List<ContextImpl> contexts = new CopyOnWriteArrayList<>();
    private final Map<Object, ContextImpl> contextByScope =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<BreakpointImpl> breakpoints = new CopyOnWriteArrayList<>();
    private final List<PausedImpl> pauses = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final ThreadLocal<ThreadState> threadState = ThreadLocal.withInitial(ThreadState::new);
    private final ThreadLocal<Boolean> evaluating = new ThreadLocal<>();
    private final ValuesImpl values = new ValuesImpl(this);

    private volatile boolean breakpointsActive = true;
    private volatile boolean skipAllPauses;
    private volatile PauseOnExceptions pauseOnExceptions = PauseOnExceptions.NONE;
    private volatile boolean pauseRequested;
    private volatile boolean terminateRequested;
    private volatile boolean pauseOnStart;
    private volatile PausedImpl currentPause;

    private DebuggerImpl(ContextFactory factory) {
        this.factory = factory;
    }

    // ---- engine side

    @Override
    public void contextCreated(Context cx) {
        cx.setOptimizationLevel(-1);
        cx.setDebugger(this, null);
        if (cx.getInstructionObserverThreshold() == 0) {
            cx.setInstructionObserverThreshold(10_000);
        }
    }

    @Override
    public void contextReleased(Context cx) {}

    @Override
    public void handleCompilationDone(Context cx, DebuggableScript fnOrScript, String source) {
        if (fnOrScript.getParent() != null || Boolean.TRUE.equals(evaluating.get())) {
            return;
        }
        ScriptImpl script =
                new ScriptImpl(String.valueOf(ids.incrementAndGet()), fnOrScript, source);
        scripts.add(script);
        for (DebugListener l : listeners) {
            l.scriptParsed(script);
        }
        for (BreakpointImpl bp : breakpoints) {
            bp.resolveAgainst(script, this);
        }
    }

    @Override
    public org.monflabs.mozilla.javascript.debug.DebugFrame getFrame(
            Context cx, DebuggableScript fnOrScript) {
        if (Boolean.TRUE.equals(evaluating.get())) {
            return null;
        }
        DebuggableScript root = fnOrScript;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        for (ScriptImpl script : scripts) {
            if (script.root == root) {
                return new HookFrame(this, script, fnOrScript);
            }
        }
        return null;
    }

    @Override
    public void observeInstructionCount(Context cx, int instructionCount) {
        if (Boolean.TRUE.equals(evaluating.get())) {
            return;
        }
        ThreadState ts = threadState.get();
        if (ts.stack.isEmpty()) {
            return;
        }
        if (ts.terminating) {
            throw new ScriptTerminated();
        }
        if (pauseRequested && !skipAllPauses) {
            pauseRequested = false;
            pause(ts, PauseReason.DEBUG_COMMAND, List.of(), null);
        }
    }

    @Override
    public ExecutionContext registerContext(Object global, String name) {
        ContextImpl context = contextOf((VarScope) global);
        context.name = name;
        return context;
    }

    @Override
    public void consoleCalled(String type, List<Object> arguments) {
        ThreadState ts = threadState.get();
        FrameState top = ts.stack.peek();
        ConsoleEvent event =
                new ConsoleEvent(
                        type,
                        arguments,
                        top == null ? null : top.location(),
                        top == null ? null : top.script.context());
        for (DebugListener l : listeners) {
            l.consoleCalled(event);
        }
    }

    ThreadState state() {
        return threadState.get();
    }

    ContextImpl contextOf(VarScope scope) {
        Object top = ScriptableObject.getTopLevelScope(scope);
        ContextImpl context = contextByScope.get(top);
        if (context == null) {
            synchronized (contextByScope) {
                context = contextByScope.get(top);
                if (context == null) {
                    context = new ContextImpl(contexts.size() + 1, top);
                    contextByScope.put(top, context);
                    contexts.add(context);
                    for (DebugListener l : listeners) {
                        l.executionContextCreated(context);
                    }
                }
            }
        }
        return context;
    }

    void frameEntered(ThreadState ts, FrameState frame) {
        if (ts.stack.isEmpty() && pauseOnStart) {
            ts.startPending = true;
            pauseOnStart = false;
        }
        ts.stack.push(frame);
    }

    void frameExited(ThreadState ts, FrameState frame, boolean byThrow, Object result) {
        if (byThrow
                && ts.pendingFrames != null
                && result == ts.reportedException
                && ts.stack.size() == 1) {
            List<FrameState> frames = ts.pendingFrames;
            ts.pendingFrames = null;
            pause(ts, frames, PauseReason.EXCEPTION, List.of(), thrownValue((Throwable) result));
        }
        ts.stack.remove(frame);
        if (ts.stack.isEmpty()) {
            ts.pendingFrames = null;
            if (byThrow && result instanceof Throwable t && !(t instanceof ScriptTerminated)) {
                ExceptionEvent event =
                        new ExceptionEvent(
                                thrownValue(t),
                                String.valueOf(t.getMessage()),
                                frame.location(),
                                List.of(),
                                frame.script.context());
                for (DebugListener l : listeners) {
                    l.exceptionThrown(event);
                }
            }
            ts.reportedException = null;
            ts.terminating = false;
            boolean paused = ts.paused;
            ts.paused = false;
            if (paused) {
                for (DebugListener l : listeners) {
                    l.executionFinished();
                }
            }
        }
    }

    void lineReached(ThreadState ts, FrameState frame, int line) {
        if (!frame.entered) {
            frame.entered = true;
            if (frame.function.isFunction() && line == frame.firstLine()) {
                int[] lines = frame.function.getLineNumbers();
                if (lines != null && lines.length > 1) {
                    return;
                }
            }
        }
        if (ts.terminating) {
            throw new ScriptTerminated();
        }
        int line0 = line - 1;
        for (TraceListener t : traceListeners) {
            t.statementReached(frame.script, line0, 0, ts.stack.size());
        }
        if (skipAllPauses) {
            return;
        }
        PauseReason reason = null;
        List<String> hits = new ArrayList<>();
        if (breakpointsActive) {
            for (BreakpointImpl bp : breakpoints) {
                if (bp.hits(frame.script, line0) && conditionHolds(bp, frame)) {
                    hits.add(bp.id());
                }
            }
            if (!hits.isEmpty()) {
                reason = PauseReason.BREAKPOINT;
            }
        }
        if (reason == null && ts.startPending) {
            reason = PauseReason.START;
        }
        if (reason == null && pauseRequested) {
            pauseRequested = false;
            reason = PauseReason.DEBUG_COMMAND;
        }
        if (reason == null && ts.step != Step.NONE) {
            int depth = ts.stack.size();
            boolean stop =
                    switch (ts.step) {
                        case INTO -> true;
                        case OVER -> depth <= ts.stepDepth;
                        case OUT -> depth < ts.stepDepth;
                        default -> false;
                    };
            if (stop) {
                reason = PauseReason.STEP;
            }
        }
        if (reason != null) {
            pause(ts, reason, hits, null);
        }
    }

    void debuggerStatement(ThreadState ts) {
        if (!skipAllPauses) {
            pause(ts, PauseReason.DEBUGGER_STATEMENT, List.of(), null);
        }
    }

    void exceptionThrown(ThreadState ts, FrameState frame, Throwable ex) {
        if (ts.reportedException == ex) {
            return;
        }
        ts.reportedException = ex;
        if (skipAllPauses || pauseOnExceptions == PauseOnExceptions.NONE) {
            return;
        }
        boolean uncaughtOnly = pauseOnExceptions == PauseOnExceptions.UNCAUGHT;
        if (uncaughtOnly) {
            ts.pendingFrames = new ArrayList<>(ts.stack);
            return;
        }
        pause(ts, PauseReason.EXCEPTION, List.of(), thrownValue(ex));
    }

    private boolean conditionHolds(BreakpointImpl bp, FrameState frame) {
        String condition = bp.request().condition();
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            Object value = new FrameView(this, frame, -1).evaluate(condition);
            return org.monflabs.mozilla.javascript.ScriptRuntime.toBoolean(value);
        } catch (DebugException e) {
            return false;
        }
    }

    private void pause(ThreadState ts, PauseReason reason, List<String> hits, Object exception) {
        pause(ts, new ArrayList<>(ts.stack), reason, hits, exception);
    }

    private void pause(
            ThreadState ts,
            List<FrameState> stackFrames,
            PauseReason reason,
            List<String> hits,
            Object exception) {
        ts.startPending = false;
        ts.paused = true;
        List<DebugFrame> frames = new ArrayList<>();
        int index = 0;
        for (FrameState f : stackFrames) {
            frames.add(new FrameView(this, f, index++));
        }
        FrameState top = stackFrames.isEmpty() ? null : stackFrames.get(0);
        PausedImpl event =
                new PausedImpl(
                        this,
                        Thread.currentThread(),
                        reason,
                        frames,
                        hits,
                        exception,
                        top == null ? null : top.script.context());
        pauses.add(event);
        currentPause = event;
        for (DebugListener l : listeners) {
            l.paused(event);
        }
        event.await();
        pauses.remove(event);
        if (currentPause == event) {
            currentPause = pauses.isEmpty() ? null : pauses.get(pauses.size() - 1);
        }
        for (DebugListener l : listeners) {
            l.resumed(event);
        }
        ts.step = event.step;
        ts.stepDepth = ts.stack.size();
        if (event.terminated) {
            ts.terminating = true;
            throw new ScriptTerminated();
        }
    }

    static Object thrownValue(Throwable t) {
        if (t instanceof JavaScriptException jse) {
            return jse.getValue();
        }
        if (t instanceof RhinoException re) {
            return re.details();
        }
        return String.valueOf(t.getMessage());
    }

    // ---- API side

    @Override
    public void addListener(org.monflabs.mozilla.api.debugger.DebugListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(org.monflabs.mozilla.api.debugger.DebugListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void addTraceListener(TraceListener listener) {
        traceListeners.add(listener);
    }

    @Override
    public void removeTraceListener(TraceListener listener) {
        traceListeners.remove(listener);
    }

    @Override
    public List<ExecutionContext> executionContexts() {
        return List.copyOf(contexts);
    }

    @Override
    public List<DebugScript> scripts() {
        return List.copyOf(scripts);
    }

    @Override
    public void clearScripts() {
        scripts.clear();
        contexts.clear();
        contextByScope.clear();
        for (DebugListener l : listeners) {
            l.executionContextsCleared();
        }
    }

    @Override
    public Breakpoint setBreakpoint(BreakpointRequest request) {
        BreakpointImpl bp = new BreakpointImpl("bp" + ids.incrementAndGet(), request);
        breakpoints.add(bp);
        for (ScriptImpl script : scripts) {
            bp.resolveAgainst(script, this);
        }
        return bp;
    }

    @Override
    public void removeBreakpoint(String breakpointId) {
        breakpoints.removeIf(bp -> bp.id().equals(breakpointId));
    }

    @Override
    public void setBreakpointsActive(boolean active) {
        breakpointsActive = active;
    }

    @Override
    public void setSkipAllPauses(boolean skip) {
        skipAllPauses = skip;
    }

    @Override
    public void setPauseOnExceptions(PauseOnExceptions mode) {
        pauseOnExceptions = mode;
    }

    @Override
    public void pause() {
        pauseRequested = true;
    }

    @Override
    public void pauseOnStart() {
        pauseOnStart = true;
    }

    @Override
    public PausedEvent currentPause() {
        return currentPause;
    }

    @Override
    public Object evaluate(ExecutionContext context, String expression) throws DebugException {
        Object top = ((ContextImpl) context).top;
        try {
            return run(cx -> cx.evaluateString((VarScope) top, expression, "<debugger>", 1, null));
        } catch (Exception e) {
            throw asDebugException(e);
        }
    }

    @Override
    public <T> T call(ExecutionContext context, Callable<T> operation) throws Exception {
        return run(cx -> operation.call());
    }

    @Override
    public DebugValues values() {
        return values;
    }

    @Override
    public void close() {
        breakpoints.clear();
        listeners.clear();
        traceListeners.clear();
        skipAllPauses = false;
        pauseRequested = false;
        pauseOnStart = false;
        for (PausedImpl p : pauses) {
            p.resume();
        }
    }

    void breakpointResolved(Breakpoint bp, Location location) {
        for (DebugListener l : listeners) {
            l.breakpointResolved(bp, location);
        }
    }

    // ---- helpers

    interface Action<T> {
        T run(Context cx) throws Exception;
    }

    /** Runs an action in a context of the factory, without the debug hooks. */
    <T> T run(Action<T> action) throws Exception {
        Boolean saved = evaluating.get();
        evaluating.set(Boolean.TRUE);
        Object[] failure = new Object[1];
        try {
            T result =
                    factory.call(
                            cx -> {
                                try {
                                    return action.run(cx);
                                } catch (org.monflabs.mozilla.javascript.EcmaError e) {
                                    failure[0] =
                                            new DebugException(
                                                    String.valueOf(e.getMessage()),
                                                    errorObject(cx, e),
                                                    e);
                                    return null;
                                } catch (RuntimeException | Error e) {
                                    throw e;
                                } catch (Exception e) {
                                    failure[0] = e;
                                    return null;
                                }
                            });
            if (failure[0] != null) {
                throw (Exception) failure[0];
            }
            return result;
        } finally {
            if (saved == null) {
                evaluating.remove();
            } else {
                evaluating.set(saved);
            }
        }
    }

    private Object errorObject(Context cx, org.monflabs.mozilla.javascript.EcmaError e) {
        try {
            VarScope scope = contexts.isEmpty() ? null : (VarScope) contexts.get(0).top;
            return scope == null
                    ? e.details()
                    : cx.newObject(scope, e.getName(), new Object[] {e.getErrorMessage()});
        } catch (RuntimeException ex) {
            return e.details();
        }
    }

    static DebugException asDebugException(Exception e) {
        if (e instanceof DebugException d) {
            return d;
        }
        Object thrown = e instanceof RuntimeException r ? thrownValue(r) : null;
        return new DebugException(String.valueOf(e.getMessage()), thrown, e);
    }

    static boolean urlMatches(BreakpointRequest r, ScriptImpl script) {
        if (r.scriptId() != null) {
            return r.scriptId().equals(script.id());
        }
        if (r.url() != null) {
            return r.url().equals(script.url());
        }
        return r.urlRegex() != null
                && script.url() != null
                && Pattern.compile(r.urlRegex()).matcher(script.url()).matches();
    }

    // ---- state

    static final class ThreadState {
        final Deque<FrameState> stack = new ArrayDeque<>();
        Step step = Step.NONE;
        int stepDepth;
        boolean startPending;
        boolean terminating;
        boolean paused;
        Throwable reportedException;
        List<FrameState> pendingFrames;
    }

    static final class FrameState {
        final ScriptImpl script;
        final DebuggableScript function;
        VarScope activation;
        VarScope top;
        boolean entered;
        Object thisObj;
        int line;

        FrameState(ScriptImpl script, DebuggableScript function) {
            this.script = script;
            this.function = function;
        }

        int firstLine() {
            int first = Integer.MAX_VALUE;
            int[] lines = function.getLineNumbers();
            if (lines != null) {
                for (int l : lines) {
                    first = Math.min(first, l);
                }
            }
            return first;
        }

        VarScope evalScope() {
            return function.isTopLevel() && top != null ? top : activation;
        }

        Location location() {
            return new Location(script, Math.max(0, line - 1), 0);
        }
    }

    static final class ContextImpl implements ExecutionContext {
        private final int id;
        volatile String name = "global";
        final Object top;

        ContextImpl(int id, Object top) {
            this.id = id;
            this.top = top;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Object global() {
            return top;
        }
    }

    static Scriptable asScriptable(Object o) {
        return o instanceof Scriptable s ? s : null;
    }

    static List<DebugScope> scopesOf(FrameState frame) {
        List<DebugScope> scopes = new ArrayList<>();
        VarScope scope = frame.evalScope();
        boolean first = true;
        while (scope != null) {
            boolean top = scope.getParentScope() == null;
            DebugScope.ScopeType type =
                    top
                            ? DebugScope.ScopeType.GLOBAL
                            : first ? DebugScope.ScopeType.LOCAL : DebugScope.ScopeType.CLOSURE;
            scopes.add(
                    new DebugScope(type, scope, top ? "global" : frame.function.getFunctionName()));
            first = false;
            scope = scope.getParentScope();
        }
        return scopes;
    }

    static NavigableSet<Integer> allLines(DebuggableScript script) {
        TreeSet<Integer> lines = new TreeSet<>();
        collect(script, lines);
        return lines;
    }

    private static void collect(DebuggableScript script, NavigableSet<Integer> lines) {
        int[] own = script.getLineNumbers();
        if (own != null) {
            for (int l : own) {
                lines.add(l);
            }
        }
        for (int i = 0; i < script.getFunctionCount(); i++) {
            collect(script.getFunction(i), lines);
        }
    }
}
