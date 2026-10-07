package org.monflabs.mozilla.javascript.benchmarks;

import java.util.concurrent.TimeUnit;
import org.monflabs.mozilla.javascript.Context;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class StartupBenchmark {
    @Benchmark
    public Object startUpRhino() {
        try (Context cx = Context.enter()) {
            return cx.initStandardObjects();
        }
    }
}
