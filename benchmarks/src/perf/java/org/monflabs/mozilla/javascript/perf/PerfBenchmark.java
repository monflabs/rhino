package org.monflabs.mozilla.javascript.perf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.monflabs.mozilla.javascript.Callable;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Context.EvaluationMethod;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;

/**
 * The measurement side of the performance gate ({@code buildtools/perf-gate.sh}).
 *
 * <p>The gate builds the engine of a base revision and of the working tree, and runs this harness
 * (always from the working tree) against each engine in turn, so that the two measurements differ
 * only in the engine. Every metric is a duration in milliseconds, lower is better:
 *
 * <ul>
 *   <li>{@code startup.*}: 50 {@code initStandardObjects()} calls;
 *   <li>{@code compile.*}: compiling a large script ({@code earley-boyer.js});
 *   <li>{@code run.*}: steady-state execution of the scripts in {@code testsrc/perf} and of the V8
 *       benchmark functions.
 * </ul>
 *
 * <p>Each metric is measured in both evaluation modes ({@code int}: interpreter, {@code cmp}:
 * compiled) and both language versions ({@code es6}: {@code VERSION_ES6}, {@code ecma}: {@code
 * VERSION_ECMASCRIPT}), so that a cost added behind the {@code VERSION_ECMASCRIPT} gate shows up
 * and the absence of a cost for {@code VERSION_ES6} users is proven.
 *
 * <p>The harness is compiled against the working tree but also runs against the base revision's
 * engine, so it must only use API that both revisions have.
 *
 * <pre>
 *   PerfBenchmark record  &lt;out.json&gt;
 *   PerfBenchmark merge   &lt;out.json&gt; &lt;in.json&gt;...
 *   PerfBenchmark compare &lt;base.json&gt; &lt;head.json&gt;
 * </pre>
 *
 * <p>System properties: {@code perf.only} (regex on metric names), {@code perf.warmup}, {@code
 * perf.iterations}, {@code perf.tolerance} (overrides every band).
 */
public final class PerfBenchmark {

    /**
     * How much each metric may worsen before the comparison fails, as a fraction, read from {@code
     * testsrc/perf-tolerances.properties}. The bands are measured, not chosen: {@code
     * buildtools/perf-calibrate.py} derives them from repeated recordings of identical code (see
     * {@code benchmarks/README.md}). A metric without a band gets {@link #DEFAULT_TOLERANCE}.
     */
    private static final Path TOLERANCES = Path.of("testsrc/perf-tolerances.properties");

    private static final double DEFAULT_TOLERANCE = 0.15;

    private static final String TOLERANCE_OVERRIDE = System.getProperty("perf.tolerance");
    private static final Pattern ONLY = Pattern.compile(System.getProperty("perf.only", ".*"));
    private static final int WARMUP = Integer.getInteger("perf.warmup", 5);
    private static final int ITERATIONS = Integer.getInteger("perf.iterations", 10);

    /**
     * Smallest duration of one timed sample; short benchmark functions are repeated to reach it.
     */
    private static final double MIN_SAMPLE_MS = 20;

    private static final Path PERF_SCRIPTS = Path.of("testsrc/perf");
    private static final Path V8 = Path.of("testsrc/benchmarks/v8-benchmarks-v6");
    private static final Path FRAMEWORK = Path.of("testsrc/benchmarks/framework.js");

    private enum Mode {
        INT("int", EvaluationMethod.Interpreter, 0.4),
        CMP("cmp", EvaluationMethod.Compiler, 1.0);

        final String label;
        final EvaluationMethod method;

        /**
         * Exposed to the scripts as {@code PERF_SCALE}: the interpreter is several times slower
         * than compiled code, so the scripts do less work there to keep a run short.
         */
        final double scale;

        Mode(String label, EvaluationMethod method, double scale) {
            this.label = label;
            this.method = method;
            this.scale = scale;
        }
    }

    private enum Version {
        ES6("es6", Context.VERSION_ES6),
        ECMA("ecma", Context.VERSION_ECMASCRIPT);

        final String label;
        final int version;

        Version(String label, int version) {
            this.label = label;
            this.version = version;
        }
    }

    /** V8 benchmark files and the benchmark functions each one registers. */
    private static final Map<String, List<String>> V8_BENCHMARKS = new LinkedHashMap<>();

    static {
        V8_BENCHMARKS.put("richards", List.of("Richards"));
        V8_BENCHMARKS.put("deltablue", List.of("DeltaBlue"));
        V8_BENCHMARKS.put("crypto", List.of("Encrypt", "Decrypt"));
        V8_BENCHMARKS.put("raytrace", List.of("RayTrace"));
        V8_BENCHMARKS.put("splay", List.of("Splay"));
        V8_BENCHMARKS.put("earley-boyer", List.of("Earley", "Boyer"));
        V8_BENCHMARKS.put("regexp", List.of("RegExpBench"));
    }

    private PerfBenchmark() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && "record".equals(args[0])) {
            Map<String, Double> results = measure();
            write(Path.of(args[1]), results);
        } else if (args.length >= 3 && "merge".equals(args[0])) {
            List<Path> inputs = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                inputs.add(Path.of(args[i]));
            }
            write(Path.of(args[1]), merge(inputs));
        } else if (args.length == 3 && "compare".equals(args[0])) {
            System.exit(compare(read(Path.of(args[1])), read(Path.of(args[2]))));
        } else {
            System.err.println(
                    "usage: PerfBenchmark record <out.json>"
                            + " | merge <out.json> <in.json>..."
                            + " | compare <base.json> <head.json>");
            System.exit(2);
        }
    }

    private static Map<String, Double> measure() throws IOException {
        Map<String, Double> results = new LinkedHashMap<>();
        for (Version version : Version.values()) {
            String startup = "startup.50scopes." + version.label + ".ms";
            if (selected(startup)) {
                put(results, startup, startup(version));
            }
        }

        String large = Files.readString(V8.resolve("earley-boyer.js"));
        for (Mode mode : Mode.values()) {
            for (Version version : Version.values()) {
                String name = "compile.earley-boyer." + mode.label + "." + version.label + ".ms";
                if (selected(name)) {
                    put(results, name, compile(mode, version, large));
                }
            }
        }

        for (Path script : perfScripts()) {
            String base = script.getFileName().toString().replace(".js", "");
            String source = Files.readString(script);
            for (Mode mode : Mode.values()) {
                for (Version version : Version.values()) {
                    String name = "run." + base + "." + mode.label + "." + version.label + ".ms";
                    if (selected(name)) {
                        put(results, name, runScript(mode, version, base, source));
                    }
                }
            }
        }

        for (Map.Entry<String, List<String>> bench : V8_BENCHMARKS.entrySet()) {
            for (Mode mode : Mode.values()) {
                for (Version version : Version.values()) {
                    String name =
                            "run.v8-"
                                    + bench.getKey()
                                    + "."
                                    + mode.label
                                    + "."
                                    + version.label
                                    + ".ms";
                    if (selected(name)) {
                        put(results, name, runV8(mode, version, bench.getKey(), bench.getValue()));
                    }
                }
            }
        }
        return results;
    }

    private static boolean selected(String metric) {
        return ONLY.matcher(metric).find();
    }

    private static void put(Map<String, Double> results, String name, double value) {
        results.put(name, value);
        System.out.printf("  %-45s %10.3f%n", name, value);
    }

    private static Context enter(Mode mode, Version version) {
        Context cx = Context.enter();
        cx.setEvaluationMethod(mode.method);
        cx.setLanguageVersion(version.version);
        return cx;
    }

    private static double startup(Version version) {
        try (Context cx = enter(Mode.CMP, version)) {
            return best(
                    () -> {
                        long start = System.nanoTime();
                        for (int i = 0; i < 50; i++) {
                            cx.initStandardObjects();
                        }
                        return (System.nanoTime() - start) / 1e6;
                    });
        }
    }

    private static double compile(Mode mode, Version version, String source) {
        try (Context cx = enter(mode, version)) {
            return best(
                    () -> {
                        long start = System.nanoTime();
                        cx.compileString(source, "earley-boyer.js", 1, null);
                        return (System.nanoTime() - start) / 1e6;
                    });
        }
    }

    /**
     * Steady-state execution of one script: compiled once, then run repeatedly in the same scope,
     * so that the JIT reaches a steady state.
     */
    private static double runScript(Mode mode, Version version, String name, String source) {
        try (Context cx = enter(mode, version)) {
            TopLevel scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "PERF_SCALE", mode.scale);
            Script script = cx.compileString(source, name + ".js", 1, null);
            return best(
                    () -> {
                        long start = System.nanoTime();
                        script.exec(cx, scope, scope);
                        return (System.nanoTime() - start) / 1e6;
                    });
        }
    }

    /**
     * Steady-state execution of V8 benchmark functions, normalized to one call of each. Short
     * functions are called several times per sample to reach {@link #MIN_SAMPLE_MS}.
     */
    private static double runV8(Mode mode, Version version, String file, List<String> functions)
            throws IOException {
        try (Context cx = enter(mode, version)) {
            TopLevel scope = cx.initStandardObjects();
            cx.evaluateString(scope, Files.readString(FRAMEWORK), "framework.js", 1, null);
            cx.evaluateString(
                    scope, Files.readString(V8.resolve(file + ".js")), file + ".js", 1, null);
            Object global = scope.getGlobalThis();
            call(cx, scope, "setup");
            List<Callable> runs = new ArrayList<>();
            for (String function : functions) {
                runs.add((Callable) call(cx, scope, "getRunFunc", function));
            }
            Measurement once =
                    () -> {
                        long start = System.nanoTime();
                        for (Callable run : runs) {
                            run.call(cx, scope, global, new Object[0]);
                        }
                        return (System.nanoTime() - start) / 1e6;
                    };
            double first = Math.max(once.run(), 0.001);
            int reps = (int) Math.max(1, Math.ceil(MIN_SAMPLE_MS / first));
            double result =
                    best(
                            () -> {
                                double total = 0;
                                for (int i = 0; i < reps; i++) {
                                    total += once.run();
                                }
                                return total / reps;
                            });
            call(cx, scope, "cleanup");
            return result;
        }
    }

    private static Object call(Context cx, TopLevel scope, String name, Object... args) {
        Callable f = (Callable) ScriptableObject.getProperty(scope, name);
        return f.call(cx, scope, scope.getGlobalThis(), args);
    }

    private static List<Path> perfScripts() throws IOException {
        List<Path> scripts = new ArrayList<>();
        try (var stream = Files.list(PERF_SCRIPTS)) {
            stream.filter(p -> p.toString().endsWith(".js")).sorted().forEach(scripts::add);
        }
        return scripts;
    }

    /**
     * The fastest of several timed runs after warmup. Noise (GC, scheduling, other processes) only
     * ever makes a run slower, so the fastest one is the most reproducible.
     */
    private static double best(Measurement measurement) {
        for (int i = 0; i < WARMUP; i++) {
            measurement.run();
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < ITERATIONS; i++) {
            best = Math.min(best, measurement.run());
        }
        return best;
    }

    private interface Measurement {
        double run();
    }

    /**
     * The median of each metric across recordings. Within a JVM the minimum is the truest sample;
     * across JVMs a run can also come out spuriously fast (a CPU still boosted after a build), and
     * the median discards that outlier where a minimum would bake it into the comparison.
     */
    private static Map<String, Double> merge(List<Path> files) throws IOException {
        Map<String, List<Double>> samples = new LinkedHashMap<>();
        for (Path file : files) {
            read(file)
                    .forEach(
                            (metric, value) ->
                                    samples.computeIfAbsent(metric, k -> new ArrayList<>())
                                            .add(value));
        }
        Map<String, Double> merged = new LinkedHashMap<>();
        samples.forEach(
                (metric, values) -> {
                    Collections.sort(values);
                    merged.put(metric, values.get(values.size() / 2));
                });
        return merged;
    }

    /** Returns 0 when no metric regressed beyond its tolerance. */
    private static int compare(Map<String, Double> base, Map<String, Double> head)
            throws IOException {
        Properties bands = tolerances();
        int regressions = 0;
        System.out.printf("%-45s %10s %10s %8s%n", "metric", "base", "head", "change");
        for (Map.Entry<String, Double> entry : new TreeMap<>(base).entrySet()) {
            Double now = head.get(entry.getKey());
            if (now == null) {
                // A measurement that did not happen did not pass.
                System.out.printf(
                        "%-45s %10.3f %10s  MISSING%n", entry.getKey(), entry.getValue(), "-");
                regressions++;
                continue;
            }
            double before = entry.getValue();
            double ratio = (now - before) / before;
            double tolerance = toleranceFor(bands, entry.getKey());
            boolean regressed = ratio > tolerance;
            if (regressed) {
                regressions++;
            }
            System.out.printf(
                    "%-45s %10.3f %10.3f %+7.1f%%  (+/-%2.0f%%) %s%n",
                    entry.getKey(),
                    before,
                    now,
                    ratio * 100,
                    tolerance * 100,
                    regressed ? "REGRESSION" : "");
        }
        for (String metric : new TreeMap<>(head).keySet()) {
            if (!base.containsKey(metric)) {
                System.out.printf("%-45s %10s %10.3f  NEW%n", metric, "-", head.get(metric));
            }
        }
        if (regressions > 0) {
            System.out.printf("%n%d metric(s) regressed beyond tolerance%n", regressions);
        } else {
            System.out.printf("%nno regression%n");
        }
        return regressions == 0 ? 0 : 1;
    }

    private static double toleranceFor(Properties bands, String metric) {
        if (TOLERANCE_OVERRIDE != null) {
            return Double.parseDouble(TOLERANCE_OVERRIDE);
        }
        String band = bands.getProperty(metric);
        return band != null ? Double.parseDouble(band) : DEFAULT_TOLERANCE;
    }

    private static Properties tolerances() throws IOException {
        Properties bands = new Properties();
        if (Files.exists(TOLERANCES)) {
            try (var in = Files.newBufferedReader(TOLERANCES)) {
                bands.load(in);
            }
        }
        return bands;
    }

    // A flat name -> number map; not worth a JSON dependency.

    private static void write(Path file, Map<String, Double> results) throws IOException {
        List<String> entries = new ArrayList<>();
        results.forEach((k, v) -> entries.add(String.format(Locale.ROOT, "  \"%s\": %.4f", k, v)));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, "{\n" + String.join(",\n", entries) + "\n}\n");
    }

    private static Map<String, Double> read(Path file) throws IOException {
        Map<String, Double> results = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.strip().replaceAll(",$", "");
            int colon = trimmed.indexOf("\":");
            if (!trimmed.startsWith("\"") || colon < 0) {
                continue;
            }
            results.put(
                    trimmed.substring(1, colon),
                    Double.parseDouble(trimmed.substring(colon + 2).strip()));
        }
        return results;
    }
}
