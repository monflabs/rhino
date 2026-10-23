/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import java.io.Serial;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.EventLoop;
import org.monflabs.mozilla.javascript.Function;
import org.monflabs.mozilla.javascript.HostJobScheduler;
import org.monflabs.mozilla.javascript.ImporterTopLevel;
import org.monflabs.mozilla.javascript.LambdaFunction;
import org.monflabs.mozilla.javascript.NativeConsole;
import org.monflabs.mozilla.javascript.RhinoException;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.ScriptStackElement;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.Synchronizer;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;
import org.monflabs.mozilla.javascript.modules.Module;
import org.monflabs.mozilla.javascript.modules.ModuleLoader;

/**
 * Runs samples on a worker thread, one at a time. Every run gets a new {@link Context} and a new
 * global, with the language version and the execution mode of its {@link Settings}. Instruction
 * counting is always on: it is what lets Stop end a script that would otherwise never return.
 */
public final class ScriptRunner {

    /** Where a run's output goes. Called on the worker thread. */
    public interface Console {
        /** Standard output. */
        void out(String text);

        /** Error output. */
        void err(String text);
    }

    /** What happened to a run. Called on the worker thread. */
    public interface Listener {
        /** A run started. */
        void started();

        /** A run ended. */
        void finished(Result result);
    }

    /**
     * How a run ended.
     *
     * @param millis how long it took
     * @param failure what it threw, or null
     * @param terminated whether Stop ended it
     */
    public record Result(long millis, Throwable failure, boolean terminated) {
        /** Whether the run completed normally. */
        public boolean ok() {
            return failure == null && !terminated;
        }
    }

    /** The language versions offered. */
    public enum Version {
        ES2026("ES2026", Context.VERSION_ECMASCRIPT),
        ES6("ES6", Context.VERSION_ES6);

        private final String label;
        private final int value;

        Version(String label, int value) {
            this.label = label;
            this.value = value;
        }

        /** The name shown. */
        public String label() {
            return label;
        }

        /** The {@code Context.VERSION_*} value. */
        public int value() {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * How a script runs.
     *
     * @param version the language version
     * @param interpreted whether it runs in the interpreter rather than as compiled classes
     */
    public record Settings(Version version, boolean interpreted) {
        /** The default: the latest version, compiled. */
        public static final Settings DEFAULT = new Settings(Version.ES2026, false);

        /** A short description, for the status line. */
        public String describe() {
            return version.label() + ", " + (interpreted ? "interpreted" : "compiled");
        }
    }

    /** Thrown from the instruction observer to end a script; scripts cannot catch it. */
    static final class Stopped extends Error {
        @Serial private static final long serialVersionUID = 1L;

        Stopped() {
            super("stopped", null, false, false);
        }
    }

    /** Imports and exports of a module, at the start of a line. */
    private static final Pattern MODULE =
            Pattern.compile("(?m)^\\s*(import\\s*[{*'\"]|import\\s+[\\w$]|export\\s)");

    private static final Object ORIGIN = new Object();

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "playground-script");
                        t.setDaemon(true);
                        return t;
                    });
    private final AtomicReference<Run> current = new AtomicReference<>();
    private final ContextFactory factory = new PlaygroundContextFactory();

    private static final class Run {
        final Thread thread = Thread.currentThread();
        volatile boolean stopRequested;
    }

    private final class PlaygroundContextFactory extends ContextFactory {
        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Run run = current.get();
            if (run != null && run.stopRequested && run.thread == Thread.currentThread()) {
                throw new Stopped();
            }
        }
    }

    /**
     * Runs a script; a run already queued and not started is dropped by cancelling its future.
     *
     * @param sample the sample the script comes from (its files)
     * @param source the script text, as edited
     * @param settings the language version and the execution mode
     * @param console where output goes
     * @param listener who to tell
     * @return the run, cancellable while it waits
     */
    public Future<?> run(
            Sample sample, String source, Settings settings, Console console, Listener listener) {
        return worker.submit(() -> execute(sample, source, settings, console, listener));
    }

    /**
     * Stops the running script, if any: it ends at its next instruction check, or at once if it
     * waits on a timer, on an {@code Atomics.waitAsync}, or in Java.
     */
    public void stop() {
        Run run = current.get();
        if (run == null) {
            return;
        }
        run.stopRequested = true;
        run.thread.interrupt();
    }

    /** Whether a script is running. */
    public boolean isRunning() {
        return current.get() != null;
    }

    /** Shuts the worker down. */
    public void close() {
        stop();
        worker.shutdownNow();
    }

    private void execute(
            Sample sample, String source, Settings settings, Console console, Listener listener) {
        Run run = new Run();
        current.set(run);
        listener.started();
        long start = System.nanoTime();
        Throwable failure = null;
        Context cx = factory.enterContext();
        try {
            cx.setLanguageVersion(settings.version().value());
            cx.setInterpretedMode(settings.interpreted());
            cx.setInstructionObserverThreshold(10_000);
            EventLoop loop = new EventLoop();
            cx.setHostJobScheduler(loop);
            TopLevel scope = new ImporterTopLevel(cx);
            install(scope, console, sample.files());
            installTimers(scope, loop);
            String name = sample.fileName();
            boolean module = cx.isLatestEcmaScript() && MODULE.matcher(source).find();
            Script script = null;
            if (!module) {
                try {
                    script = cx.compileString(source, name, 1, null);
                } catch (EvaluatorException e) {
                    // top-level await makes a script a module
                    if (!cx.isLatestEcmaScript() || !source.contains("await")) {
                        throw e;
                    }
                    module = true;
                }
            }
            if (module) {
                cx.setModuleLoaders(loader(sample.files()));
                cx.evaluateModule(scope, Module.source(name, source, ORIGIN));
            } else {
                script.exec(cx, scope, scope.getGlobalThis());
            }
            loop.run(cx);
        } catch (Throwable t) {
            failure = t;
        } finally {
            Context.exit();
        }
        Thread.interrupted();
        current.set(null);
        boolean terminated = run.stopRequested || isStopped(failure);
        listener.finished(
                new Result(
                        (System.nanoTime() - start) / 1_000_000,
                        terminated ? null : failure,
                        terminated));
    }

    /** The globals the playground adds: print, sync, console, and the sample's other files. */
    private static void install(TopLevel scope, Console console, Map<String, String> files) {
        LambdaFunction print =
                new LambdaFunction(
                        scope,
                        "print",
                        1,
                        (lcx, lscope, thisObj, args) -> {
                            StringBuilder text = new StringBuilder();
                            for (int i = 0; i < args.length; i++) {
                                if (i > 0) {
                                    text.append(' ');
                                }
                                text.append(Context.toString(args[i]));
                            }
                            console.out(text.append('\n').toString());
                            return Undefined.instance;
                        });
        ScriptableObject.defineProperty(scope, "print", print, ScriptableObject.DONTENUM);
        // sync(f, lock), as in the Rhino shell: f synchronized on lock, else on its this
        LambdaFunction sync =
                new LambdaFunction(
                        scope,
                        "sync",
                        1,
                        (lcx, lscope, thisObj, args) -> {
                            if (args.length == 0 || !(args[0] instanceof Function f)) {
                                throw ScriptRuntime.typeError("sync needs a function");
                            }
                            return args.length > 1 && args[1] != Undefined.instance
                                    ? new Synchronizer(f, args[1])
                                    : new Synchronizer(f);
                        });
        ScriptableObject.defineProperty(scope, "sync", sync, ScriptableObject.DONTENUM);
        NativeConsole.init(scope, false, new ConsolePrinter(console));
        ScriptableObject.defineProperty(
                scope,
                "snippet",
                Context.javaToJS(new SnippetFiles(files), scope),
                ScriptableObject.DONTENUM);
    }

    /**
     * {@code setTimeout}, {@code setInterval} and their {@code clear} functions, as jobs of the
     * event loop that runs after the script.
     */
    private static void installTimers(TopLevel scope, EventLoop loop) {
        Map<Integer, HostJobScheduler.Cancelable> timers = new HashMap<>();
        int[] lastId = {0};
        for (boolean repeat : new boolean[] {false, true}) {
            String name = repeat ? "setInterval" : "setTimeout";
            LambdaFunction set =
                    new LambdaFunction(
                            scope,
                            name,
                            1,
                            (lcx, lscope, thisObj, args) -> {
                                if (args.length == 0 || !(args[0] instanceof Function f)) {
                                    throw ScriptRuntime.typeError(name + " needs a function");
                                }
                                long delay =
                                        args.length > 1
                                                ? Math.max(0, ScriptRuntime.toInt32(args[1]))
                                                : 0;
                                Object[] rest =
                                        args.length > 2
                                                ? Arrays.copyOfRange(args, 2, args.length)
                                                : ScriptRuntime.emptyArgs;
                                int id = ++lastId[0];
                                schedule(loop, timers, id, f, scope, rest, delay, repeat);
                                return id;
                            });
            ScriptableObject.defineProperty(scope, name, set, ScriptableObject.DONTENUM);
            String clearName = repeat ? "clearInterval" : "clearTimeout";
            LambdaFunction clear =
                    new LambdaFunction(
                            scope,
                            clearName,
                            1,
                            (lcx, lscope, thisObj, args) -> {
                                if (args.length > 0) {
                                    HostJobScheduler.Cancelable timer =
                                            timers.remove(ScriptRuntime.toInt32(args[0]));
                                    if (timer != null) {
                                        timer.cancel();
                                    }
                                }
                                return Undefined.instance;
                            });
            ScriptableObject.defineProperty(scope, clearName, clear, ScriptableObject.DONTENUM);
        }
    }

    private static void schedule(
            EventLoop loop,
            Map<Integer, HostJobScheduler.Cancelable> timers,
            int id,
            Function f,
            TopLevel scope,
            Object[] args,
            long delay,
            boolean repeat) {
        timers.put(
                id,
                loop.schedule(
                        jcx -> {
                            if (repeat) {
                                schedule(loop, timers, id, f, scope, args, delay, true);
                            } else {
                                timers.remove(id);
                            }
                            f.call(jcx, scope, scope.getGlobalThis(), args);
                            jcx.processMicrotasks();
                        },
                        delay));
    }

    /** Imports resolve to the sample's other files. */
    private static ModuleLoader loader(Map<String, String> files) {
        return (specifier, referrer) -> {
            if (referrer != null && referrer.origin() != ORIGIN) {
                return null;
            }
            String clean = specifier.startsWith("./") ? specifier.substring(2) : specifier;
            String text = files.get(clean);
            return text == null ? null : Module.source(clean, text, ORIGIN);
        };
    }

    private static boolean isStopped(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof Stopped || c instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The message a failure deserves: the script's message and stack for a script error, the
     * exception otherwise.
     */
    public static String describe(Throwable failure) {
        if (failure instanceof RhinoException re) {
            String stack = re.getScriptStackTrace().strip();
            return re.getMessage() + (stack.isEmpty() ? "" : "\n" + stack);
        }
        return String.valueOf(failure);
    }

    /** What the {@code snippet} global offers a script: the sample's other files. */
    public static final class SnippetFiles {
        private final Map<String, String> files;

        SnippetFiles(Map<String, String> files) {
            this.files = files;
        }

        /** The text of a sibling file of the sample. */
        public String text(String name) {
            String text = files.get(name);
            if (text == null) {
                throw new IllegalArgumentException(
                        "no file named " + name + " in this sample; there are " + files.keySet());
            }
            return text;
        }

        /** The names of the sample's other files. */
        public String[] names() {
            return files.keySet().toArray(new String[0]);
        }
    }

    /** {@code console.log} and its family: errors go to the error output. */
    private static final class ConsolePrinter implements NativeConsole.ConsolePrinter {
        @Serial private static final long serialVersionUID = 1L;
        private final transient Console console;

        ConsolePrinter(Console console) {
            this.console = console;
        }

        @Override
        public void print(
                Context cx,
                VarScope scope,
                NativeConsole.Level level,
                Object[] args,
                ScriptStackElement[] stack) {
            StringBuilder text = new StringBuilder(NativeConsole.format(cx, scope, args));
            text.append('\n');
            if (stack != null) {
                for (ScriptStackElement element : stack) {
                    text.append("    ").append(element).append('\n');
                }
            }
            if (level == NativeConsole.Level.ERROR) {
                console.err(text.toString());
            } else {
                console.out(text.toString());
            }
        }
    }
}
