/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.monflabs.mozilla.javascript.Callable;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.JavaScriptException;
import org.monflabs.mozilla.javascript.LambdaFunction;
import org.monflabs.mozilla.javascript.RhinoException;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;

/**
 * Compiled code computes what the interpreter computes: every script of a corpus runs in both modes
 * under {@code VERSION_ECMASCRIPT}, and its prints, its completion value or exception, and the
 * globals it leaves are compared. The corpus is {@code testsrc/jstests/compiled-interpreted}, which
 * aims at the shapes the class compiler specializes, and the benchmark scripts. What differs
 * between two interpreted runs (dates, timings) is not compared.
 */
class CompiledInterpretedTest {
    private static final Path OWN = Path.of("testsrc/jstests/compiled-interpreted");
    private static final Path BENCHMARKS = Path.of("../benchmarks/testsrc");
    private static final Path FRAMEWORK = BENCHMARKS.resolve("benchmarks/framework.js");
    private static final Path V8 = BENCHMARKS.resolve("benchmarks/v8-benchmarks-v6");
    private static final List<String> V8_FILES =
            List.of(
                    "richards",
                    "deltablue",
                    "crypto",
                    "raytrace",
                    "splay",
                    "earley-boyer",
                    "regexp");

    /** Math.random and the current time are fixed, so that the runs compute the same values. */
    private static final String PRELUDE =
            "Math.random = (function () { var s = 42; return function () {"
                    + " s = (s * 16807) % 2147483647; return (s - 1) / 2147483646; }; })();"
                    + "Date = (function (D) { return class Date extends D {"
                    + " constructor(...a) { if (a.length) super(...a); else super(1700000000000); }"
                    + " static now() { return 1700000000000; } }; })(Date);";

    private static final int MAX_DEPTH = 3;
    private static final int MAX_IDS = 1000;

    static Stream<String> scripts() throws IOException {
        List<String> scripts = new ArrayList<>();
        scripts.addAll(list(OWN));
        scripts.addAll(list(BENCHMARKS.resolve("benchmarks/sunspider-1.0")));
        scripts.addAll(list(BENCHMARKS.resolve("perf")));
        for (String file : V8_FILES) {
            scripts.add(V8.resolve(file + ".js").toString());
        }
        return scripts.stream();
    }

    private static List<String> list(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(Path::toString).filter(f -> f.endsWith(".js")).sorted().toList();
        }
    }

    @ParameterizedTest
    @MethodSource("scripts")
    void compiledComputesWhatTheInterpreterComputes(String file) throws IOException {
        String source = Files.readString(Path.of(file));
        Map<String, String> interpreted = run(file, source, true);
        Map<String, String> compiled = run(file, source, false);
        Map<String, String> again = run(file, source, true);
        List<String> differences = new ArrayList<>();
        for (String key : interpreted.keySet()) {
            String expected = interpreted.get(key);
            if (expected.equals(again.get(key)) && !expected.equals(compiled.get(key))) {
                differences.add(
                        key
                                + "\n  interpreted: "
                                + expected
                                + "\n  compiled:    "
                                + compiled.get(key));
            }
        }
        for (String key : compiled.keySet()) {
            if (!interpreted.containsKey(key) && !again.containsKey(key)) {
                differences.add(key + "\n  compiled only: " + compiled.get(key));
            }
        }
        assertTrue(differences.isEmpty(), file + ":\n" + String.join("\n", differences));
    }

    private static Map<String, String> run(String file, String source, boolean interpreted) {
        Map<String, String> state = new LinkedHashMap<>();
        StringBuilder prints = new StringBuilder();
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInterpretedMode(interpreted);
            TopLevel scope = cx.initStandardObjects();
            ScriptableObject.defineProperty(
                    scope,
                    "print",
                    new LambdaFunction(
                            scope,
                            "print",
                            1,
                            (lcx, lscope, thisObj, args) -> {
                                for (int i = 0; i < args.length; i++) {
                                    prints.append(i == 0 ? "" : " ")
                                            .append(ScriptRuntime.toString(args[i]));
                                }
                                prints.append('\n');
                                return Undefined.instance;
                            }),
                    ScriptableObject.DONTENUM);
            ScriptableObject.putProperty(scope, "PERF_SCALE", 0.1);
            cx.evaluateString(scope, PRELUDE, "prelude.js", 1, null);
            ScriptableObject global = scope.getGlobalThis();
            Set<Object> standard = Set.of(global.getIds());
            String name = Path.of(file).getFileName().toString();
            Object result;
            try {
                if (file.startsWith(V8.toString())) {
                    cx.evaluateString(scope, read(FRAMEWORK), "framework.js", 1, null);
                    cx.evaluateString(scope, source, name, 1, null);
                    callGlobal(cx, scope, "setup");
                    List<Object> runs = new ArrayList<>();
                    for (Object id : ((Scriptable) global.get("Benchmarks", global)).getIds()) {
                        Object run = callGlobal(cx, scope, "getRunFunc", id);
                        runs.add(((Callable) run).call(cx, scope, global, new Object[0]));
                    }
                    result = cx.newArray(scope, runs.toArray());
                } else {
                    result = cx.evaluateString(scope, source, name, 1, null);
                }
                cx.processMicrotasks();
                state.put("result", describe(result, 0));
            } catch (RhinoException e) {
                state.put("throws", describe(e));
            }
            state.put("prints", prints.toString());
            for (Object id : global.getIds()) {
                if (!standard.contains(id)) {
                    state.put("global " + id, describe(property(global, id), 0));
                }
            }
        }
        return state;
    }

    private static Object callGlobal(Context cx, TopLevel scope, String name, Object... args) {
        ScriptableObject global = scope.getGlobalThis();
        return ((Callable) global.get(name, global)).call(cx, scope, global, args);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object property(Scriptable object, Object id) {
        try {
            return id instanceof Integer i
                    ? ScriptableObject.getProperty(object, i)
                    : ScriptableObject.getProperty(object, id.toString());
        } catch (RhinoException e) {
            return "<" + describe(e) + ">";
        }
    }

    private static String describe(RhinoException e) {
        if (e instanceof JavaScriptException js) {
            return "throw " + describe(js.getValue(), MAX_DEPTH);
        }
        return e.getClass().getSimpleName() + ": " + e.details();
    }

    private static String describe(Object value, int depth) {
        if (value == null || Undefined.isUndefined(value) || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof CharSequence) {
            return '"' + value.toString() + '"';
        }
        if (value instanceof BigInteger) {
            return value + "n";
        }
        if (value instanceof Number) {
            return ScriptRuntime.toString(value);
        }
        if (value instanceof Callable) {
            return "function";
        }
        if (!(value instanceof Scriptable object)) {
            return value.getClass().getSimpleName() + " " + value;
        }
        if (depth >= MAX_DEPTH) {
            return "[" + object.getClassName() + "]";
        }
        StringBuilder sb = new StringBuilder(object.getClassName());
        if (object.getClassName().equals("Date") || object.getClassName().equals("RegExp")) {
            sb.append(' ').append(ScriptRuntime.toString(object));
        }
        sb.append(" {");
        Object[] ids = object.getIds();
        for (int i = 0; i < ids.length && i < MAX_IDS; i++) {
            sb.append(i == 0 ? " " : ", ")
                    .append(ids[i])
                    .append(": ")
                    .append(describe(property(object, ids[i]), depth + 1));
        }
        if (ids.length > MAX_IDS) {
            sb.append(", ... ").append(ids.length).append(" ids");
        }
        return sb.append(" }").toString();
    }
}
