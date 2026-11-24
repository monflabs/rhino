package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.EcmaError;
import org.monflabs.mozilla.javascript.ResourceLimits;
import org.monflabs.mozilla.javascript.ResourceLimits.Kind;
import org.monflabs.mozilla.javascript.RhinoException;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class M19HostControlsTest {

    public static class Host {
        public String hello(String name) {
            return "hello " + name;
        }

        public static int twice(int x) {
            return 2 * x;
        }

        public void fail() {
            throw new IllegalStateException("boom");
        }

        public static class Thing {
            public final String label;

            public Thing(String label) {
                this.label = label;
            }
        }
    }

    private static Object run(ContextFactory factory, ResourceLimits limits, String script) {
        Object[] result = new Object[1];
        Utils.runWithAllModes(
                factory,
                cx -> {
                    cx.setResourceLimits(limits);
                    var scope = cx.initStandardObjects();
                    ScriptableObject.putProperty(
                            scope, "host", Context.javaToJS(new Host(), scope));
                    ScriptableObject.putProperty(
                            scope,
                            "Thing",
                            cx.getWrapFactory().wrapJavaClass(cx, scope, Host.Thing.class));
                    result[0] = cx.evaluateString(scope, script, "t", 1, null);
                    return null;
                });
        return result[0];
    }

    @Test
    public void interceptorSeesMethodsAndConstructors() {
        List<String> seen = new ArrayList<>();
        ContextFactory factory =
                new ContextFactory() {
                    {
                        setInvocationInterceptor(
                                (cx, member, target, args, next) -> {
                                    seen.add(member.getName());
                                    Object r = next.proceed(target, args);
                                    return r instanceof String ? r + "!" : r;
                                });
                    }
                };
        assertEquals("hello x!", String.valueOf(run(factory, null, "String(host.hello('x'))")));
        assertEquals(4, ((Number) run(factory, null, "host.twice(2)")).intValue());
        assertEquals("L", String.valueOf(run(factory, null, "String(new Thing('L').label)")));
        assertTrue(seen.contains("hello"));
        assertTrue(seen.contains("twice"));
        assertTrue(seen.stream().anyMatch(n -> n.endsWith("Thing")));
    }

    @Test
    public void noInterceptorByDefault() {
        assertEquals(null, new ContextFactory().getInvocationInterceptor());
    }

    @Test
    public void mapThrowableTranslatesJavaExceptions() {
        ContextFactory factory =
                new ContextFactory() {
                    @Override
                    protected Throwable mapThrowable(Context cx, Throwable t) {
                        if (t instanceof IllegalStateException) {
                            return ScriptRuntime.constructError(
                                    "TypeError", "mapped " + t.getMessage());
                        }
                        return t;
                    }
                };
        assertEquals(
                "TypeError: mapped boom",
                String.valueOf(run(factory, null, "try { host.fail() } catch (e) { String(e) }")));
        assertThrows(EcmaError.class, () -> run(factory, null, "host.fail()"));
    }

    @Test
    public void unmappedThrowableIsWrappedAsBefore() {
        assertThrows(RhinoException.class, () -> run(new ContextFactory(), null, "host.fail()"));
    }

    @Test
    public void limitsSeeEachKind() {
        Map<Kind, Long> max = new EnumMap<>(Kind.class);
        ResourceLimits recorder = (cx, kind, size) -> max.merge(kind, size, Math::max);
        run(
                new ContextFactory(),
                recorder,
                "var s = 'ab'.repeat(5000); var a = []; for (var i = 0; i < 100; i++) a[i] = i;"
                        + "new ArrayBuffer(100); var m = new Map(); m.set(1, 2); Symbol.for('q');"
                        + "eval('1+1');"
                        + "var o = {}; for (var i = 0; i < 2000; i++) o['p' + i] = i;");
        assertEquals(10000L, max.get(Kind.STRING));
        assertTrue(max.get(Kind.ARRAY) >= 100);
        assertEquals(100L, max.get(Kind.BUFFER));
        assertEquals(1L, max.get(Kind.COLLECTION));
        assertTrue(max.get(Kind.SYMBOLS) >= 1);
        assertEquals(3L, max.get(Kind.EVAL));
        assertTrue(max.get(Kind.OBJECT) > 1000);
    }

    @Test
    public void limitsCanRefuse() {
        ResourceLimits refuse =
                (cx, kind, size) -> {
                    if (kind == Kind.STRING && size > 20000) {
                        throw Context.reportRuntimeError("string quota");
                    }
                    if (kind == Kind.BUFFER && size > 1000) {
                        throw Context.reportRuntimeError("buffer quota");
                    }
                };
        assertEquals(
                10000,
                ((Number) run(new ContextFactory(), refuse, "'ab'.repeat(5000).length"))
                        .intValue());
        assertThrows(
                RhinoException.class,
                () -> run(new ContextFactory(), refuse, "'ab'.repeat(20000)"));
        assertThrows(
                RhinoException.class,
                () -> run(new ContextFactory(), refuse, "new ArrayBuffer(5000)"));
    }

    @Test
    public void noLimitsByDefault() {
        assertEquals(
                40000,
                ((Number) run(new ContextFactory(), null, "'ab'.repeat(20000).length")).intValue());
    }
}
