/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.test;

import static org.monflabs.mozilla.debugger.test.TestNgAsserts.assertEquals;
import static org.monflabs.mozilla.debugger.test.TestNgAsserts.assertNotNull;
import static org.monflabs.mozilla.debugger.test.TestNgAsserts.assertTrue;
import static org.monflabs.mozilla.debugger.test.TestNgAsserts.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.CdpServer;
import org.monflabs.mozilla.debugger.json.Json;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.LambdaFunction;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;

/**
 * The protocol, as a client speaks it: enable, breakpoints, pauses, frames, evaluation, properties,
 * stepping, exceptions, the console.
 */
@SuppressWarnings({
    "javadoc",
    "unchecked",
    "deprecation"
}) // the factory overloads stay tested for compatibility
class CdpProtocolTest {
    private ContextFactory factory;
    private TopLevel scope;
    private CdpServer.Handle server;
    private CdpClient client;
    private ExecutorService worker;

    @BeforeEach
    void setUp() throws Exception {
        factory = new ContextFactory();
        final Debugger debugger = Debugger.of(factory);
        scope =
                factory.call(
                        cx -> {
                            final TopLevel top = cx.initStandardObjects();
                            final Scriptable console = cx.newObject(top);
                            for (final String type :
                                    new String[] {"log", "error", "warn", "info", "debug"}) {
                                ScriptableObject.putProperty(
                                        console,
                                        type,
                                        new LambdaFunction(
                                                top,
                                                type,
                                                0,
                                                (c, s, thisObj, args) -> {
                                                    debugger.consoleCalled(
                                                            type, Arrays.asList(args));
                                                    return Undefined.instance;
                                                }));
                            }
                            ScriptableObject.putProperty(top, "console", console);
                            return top;
                        });
        debugger.registerContext(scope, "global");
        server = CdpServer.open(Debugger.of(factory), InspectOptions.parse("127.0.0.1:0", false));
        client = new CdpClient(server.webSocketUrl());
        worker = Executors.newSingleThreadExecutor();
        client.call("Runtime.enable");
        client.call("Debugger.enable");
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
        worker.shutdownNow();
        Debugger.of(factory).close();
    }

    private Future<Object> run(final String fileName, final String... lines) {
        return worker.submit(
                () ->
                        factory.call(
                                cx ->
                                        cx.evaluateString(
                                                scope,
                                                String.join("\n", lines),
                                                fileName,
                                                1,
                                                null)));
    }

    private static Map<String, Object> map(final Object o) {
        return (Map<String, Object>) o;
    }

    private static List<Object> list(final Object o) {
        return (List<Object>) o;
    }

    private static Map<String, Object> topFrame(final Map<String, Object> paused) {
        return map(list(paused.get("callFrames")).get(0));
    }

    private static long line(final Map<String, Object> frame) {
        return ((Number) map(frame.get("location")).get("lineNumber")).longValue();
    }

    @Test
    void enableReportsContextsAndScripts() throws Exception {
        final Map<String, Object> ctx =
                map(client.event("Runtime.executionContextCreated").get("context"));
        assertEquals(ctx.get("name"), "global");
        run("first.js", "1;").get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
        final Map<String, Object> script = client.event("Debugger.scriptParsed");
        assertTrue(
                String.valueOf(script.get("url")).endsWith("first.js"),
                String.valueOf(script.get("url")));
        assertEquals(script.get("startLine"), 0L);
        assertEquals(
                client.call("Debugger.getScriptSource", "scriptId", script.get("scriptId"))
                        .get("scriptSource"),
                "1;");
    }

    @Test
    void breakpointPauseFramesScopesEvaluateResume() throws Exception {
        final Map<String, Object> set =
                client.call(
                        "Debugger.setBreakpointByUrl",
                        "lineNumber",
                        2L,
                        "urlRegex",
                        "(.*/)?bp\\.js");
        assertEquals(list(set.get("locations")).size(), 0, "pending");
        final String breakpointId = (String) set.get("breakpointId");

        final Future<Object> result =
                run(
                        "bp.js",
                        "function f(a) {",
                        "  var b = a + 1;",
                        "  return b * 2;",
                        "}",
                        "f(20);");
        final Map<String, Object> resolved = client.event("Debugger.breakpointResolved");
        assertEquals(resolved.get("breakpointId"), breakpointId);
        assertEquals(map(resolved.get("location")).get("lineNumber"), 2L);

        final Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "other");
        assertEquals(list(paused.get("hitBreakpoints")), List.of(breakpointId));
        final List<Object> frames = list(paused.get("callFrames"));
        assertEquals(frames.size(), 2);
        final Map<String, Object> top = map(frames.get(0));
        assertEquals(top.get("functionName"), "f");
        assertEquals(line(top), 2L);
        final List<Object> scopes = list(top.get("scopeChain"));
        assertEquals(map(scopes.get(0)).get("type"), "local");
        assertEquals(map(scopes.get(scopes.size() - 1)).get("type"), "global");
        assertEquals(map(top.get("this")).get("type"), "object");

        // the local scope's properties
        final String localId = (String) map(map(scopes.get(0)).get("object")).get("objectId");
        final Map<String, Object> props =
                client.call("Runtime.getProperties", "objectId", localId, "ownProperties", true);
        boolean sawB = false;
        for (final Object p : list(props.get("result"))) {
            if ("b".equals(map(p).get("name"))) {
                sawB = true;
                assertEquals(map(map(p).get("value")).get("value"), 21L);
            }
        }
        assertTrue(sawB, props.toString());

        // evaluate on the frame
        final Map<String, Object> eval =
                client.call(
                        "Debugger.evaluateOnCallFrame",
                        "callFrameId",
                        top.get("callFrameId"),
                        "expression",
                        "b * 10");
        assertEquals(map(eval.get("result")).get("value"), 210L);
        final Map<String, Object> failing =
                client.call(
                        "Debugger.evaluateOnCallFrame",
                        "callFrameId",
                        top.get("callFrameId"),
                        "expression",
                        "nope.x");
        assertNotNull(failing.get("exceptionDetails"));
        assertEquals(
                map(map(failing.get("exceptionDetails")).get("exception")).get("subtype"), "error");

        // resuming past the last breakpoint runs the script to its end. That
        // finishes the (debugged) execution, which now closes the connection the
        // way a real inspector does when the process exits - so the resume's own
        // response races that close; send it without waiting for one.
        client.send("Debugger.resume");
        client.event("Debugger.resumed");
        assertEquals(((Number) result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS)).intValue(), 42);
        assertEquals(client.awaitClose(), "1000:execution finished");
    }

    @Test
    void lateAttachReplaysExistingPause() throws Exception {
        // No client is attached when the script freezes (the server allows only
        // one client at a time, so the setUp client goes away first).
        client.close();
        final Debugger dbg = Debugger.of(factory);
        dbg.pauseOnStart();
        final Future<Object> result =
                run("frozen.js", "function f() { return 42; }", "var r = f();", "r;");
        // wait for it to actually freeze at the first statement, with nobody watching
        final long deadline = System.currentTimeMillis() + CdpClient.TIMEOUT * 1000;
        while (dbg.currentPause() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull(dbg.currentPause(), "the script must freeze at start");

        // A client attaches now, after the pause already happened. Its
        // Debugger.enable must replay the pause - the fire-once event it missed -
        // so it sees the call stack, not an empty "running" view.
        final CdpClient late = new CdpClient(server.webSocketUrl());
        late.call("Runtime.enable");
        late.call("Debugger.enable");
        final Map<String, Object> paused = late.event("Debugger.paused");
        final List<Object> frames = list(paused.get("callFrames"));
        assertTrue(frames.size() >= 1, "the late client must see the paused call stack: " + paused);
        assertNotNull(map(frames.get(0)).get("location"));

        // resume through the late client so the frozen script finishes
        late.send("Debugger.resume");
        assertEquals(((Number) result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS)).intValue(), 42);
        assertEquals(late.awaitClose(), "1000:execution finished");
    }

    @Test
    void debuggerStatementReason() throws Exception {
        final Future<Object> result = run("dbg.js", "var a = 1;", "debugger;", "a + 1;");
        final Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "debuggerStatement");
        client.send("Debugger.resume");
        assertEquals(((Number) result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS)).intValue(), 2);
    }

    @Test
    void stepping() throws Exception {
        client.call("Debugger.setBreakpointByUrl", "lineNumber", 4L, "urlRegex", "(.*/)?step\\.js");
        final Future<Object> result =
                run(
                        "step.js",
                        "function inner(v) {", // 0
                        "  var r = v + 1;", // 1
                        "  return r;", // 2
                        "}", // 3
                        "var x = inner(1);", // 4
                        "x = inner(x);", // 5
                        "x;"); // 6
        Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(line(topFrame(paused)), 4L);
        client.call("Debugger.stepInto");
        paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "step");
        assertEquals(topFrame(paused).get("functionName"), "inner");
        assertEquals(line(topFrame(paused)), 1L);
        client.call("Debugger.stepOut");
        paused = client.event("Debugger.paused");
        assertEquals(line(topFrame(paused)), 5L);
        client.call("Debugger.stepOver");
        paused = client.event("Debugger.paused");
        assertEquals(line(topFrame(paused)), 6L);
        // the final resume runs the script to its end and closes the connection
        client.send("Debugger.resume");
        assertEquals(((Number) result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS)).intValue(), 3);
        assertEquals(client.awaitClose(), "1000:execution finished");
    }

    @Test
    void pauseOnCaughtExceptionAndResume() throws Exception {
        client.call("Debugger.setPauseOnExceptions", "state", "all");
        final Future<Object> result =
                run(
                        "exc.js",
                        "function thrower() { throw new TypeError('boom'); }",
                        "try { thrower(); } catch (e) { e.message; }");
        final Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "exception");
        assertEquals(map(paused.get("data")).get("subtype"), "error");
        assertTrue(
                String.valueOf(map(paused.get("data")).get("description"))
                        .startsWith("TypeError: boom"));
        // this run paused, so its completion closes the connection - see the
        // breakpoint test; send the resume without waiting for a racing response
        client.send("Debugger.resume");
        assertEquals(result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS), "boom");
        assertEquals(client.awaitClose(), "1000:execution finished");
    }

    @Test
    void uncaughtExceptionThrown() throws Exception {
        client.call("Debugger.setPauseOnExceptions", "state", "none");
        // pauseOnExceptions is none, so this run never pauses; it is not a
        // debug session that concluded, so the connection stays open
        final Future<Object> escaping =
                run("uncaught.js", "function bad() { throw new RangeError('out'); }", "bad();");
        final Map<String, Object> thrown = client.event("Runtime.exceptionThrown");
        final Map<String, Object> details = map(thrown.get("exceptionDetails"));
        assertEquals(details.get("text"), "Uncaught");
        assertTrue(
                String.valueOf(map(details.get("exception")).get("description"))
                        .startsWith("RangeError: out"));
        try {
            escaping.get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
            fail("the script throws");
        } catch (final java.util.concurrent.ExecutionException expected) {
            // as it should
        }
    }

    @Test
    void runtimeEvaluateAndConsole() throws Exception {
        run(
                        "console.js",
                        "var o = { a: 1, b: [1, 2, 3] };",
                        "console.log('hello', o);",
                        "console.error('bad');")
                .get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
        final Map<String, Object> log = client.event("Runtime.consoleAPICalled");
        assertEquals(log.get("type"), "log");
        final List<Object> args = list(log.get("args"));
        assertEquals(map(args.get(0)).get("value"), "hello");
        assertEquals(map(args.get(1)).get("type"), "object");
        assertNotNull(map(args.get(1)).get("preview"));
        assertEquals(client.event("Runtime.consoleAPICalled").get("type"), "error");

        final Map<String, Object> evaluated =
                client.call("Runtime.evaluate", "expression", "o.b", "generatePreview", true);
        final Map<String, Object> value = map(evaluated.get("result"));
        assertEquals(value.get("subtype"), "array");
        assertEquals(value.get("description"), "Array(3)");
        final Map<String, Object> props =
                client.call(
                        "Runtime.getProperties",
                        "objectId",
                        value.get("objectId"),
                        "ownProperties",
                        true);
        assertEquals(list(props.get("result")).size(), 4, "three elements and length");
        assertEquals(
                map(list(props.get("internalProperties")).get(0)).get("name"), "[[Prototype]]");

        final Map<String, Object> called =
                client.call(
                        "Runtime.callFunctionOn",
                        "functionDeclaration",
                        "function (n) { return this.a + n; }",
                        "objectId",
                        map(client.call("Runtime.evaluate", "expression", "o").get("result"))
                                .get("objectId"),
                        "arguments",
                        List.of(Json.object("value", 41L)));
        assertEquals(map(called.get("result")).get("value"), 42L);

        final Map<String, Object> nan =
                map(client.call("Runtime.evaluate", "expression", "NaN").get("result"));
        assertEquals(nan.get("unserializableValue"), "NaN");
        final Map<String, Object> byValue =
                client.call("Runtime.evaluate", "expression", "o", "returnByValue", true);
        assertEquals(map(map(byValue.get("result")).get("value")).get("a"), 1L);

        client.call("Runtime.releaseObjectGroup", "objectGroup", "console");
        assertTrue(map(client.call("Runtime.getHeapUsage")).containsKey("usedSize"));
        assertNotNull(client.call("Runtime.getIsolateId").get("id"));
    }

    @Test
    void terminateExecutionEndsARunningScript() throws Exception {
        final Future<Object> result = run("forever.js", "var i = 0; while (true) { i++; }");
        Thread.sleep(100);
        client.call("Runtime.terminateExecution");
        try {
            result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
            fail("the script must be terminated");
        } catch (final java.util.concurrent.ExecutionException expected) {
            assertTrue(
                    String.valueOf(expected.getCause()).contains("terminated"),
                    String.valueOf(expected.getCause()));
        }
    }

    @Test
    void unknownMethodsDoNotEndTheSession() throws Exception {
        try {
            client.call("Profiler.enable");
            fail("expected -32601");
        } catch (final CdpClient.CdpFailure e) {
            assertEquals(e.code, -32601);
        }
        assertEquals(
                client.call("Debugger.setAsyncCallStackDepth", "maxDepth", 32L),
                Map.of(),
                "accepted as a no-op");
        client.sendRaw("this is not json");
        client.sendRaw("{\"id\": 77}");
        final Map<String, Object> response = client.rawResponse();
        assertEquals(response.get("id"), 77L);
        assertEquals(map(response.get("error")).get("code"), -32600L);
        assertEquals(
                map(client.call("Runtime.evaluate", "expression", "6 * 7").get("result"))
                        .get("value"),
                42L,
                "still alive");
    }
}
