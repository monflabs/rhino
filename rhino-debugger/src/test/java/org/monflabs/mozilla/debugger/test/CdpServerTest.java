/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.test;

import static org.monflabs.mozilla.debugger.test.TestNgAsserts.assertEquals;
import static org.monflabs.mozilla.debugger.test.TestNgAsserts.assertNotNull;
import static org.monflabs.mozilla.debugger.test.TestNgAsserts.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
 * The HTTP side: discovery, the Host check, the WebSocket handshake, and the frame codec through a
 * real client.
 */
@SuppressWarnings({"javadoc", "deprecation"}) // the factory overloads stay tested for compatibility
class CdpServerTest {
    private ContextFactory factory;
    private TopLevel scope;
    private CdpServer.Handle server;

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
    }

    @AfterEach
    void tearDown() {
        server.close();
        Debugger.of(factory).close();
    }

    private String get(final String path) throws Exception {
        final HttpResponse<String> r =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://127.0.0.1:" + server.port() + path))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        return r.statusCode() + " " + r.body();
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoveryDocuments() throws Exception {
        final String version = get("/json/version");
        assertTrue(version.startsWith("200 "), version);
        final Map<String, Object> v = (Map<String, Object>) Json.parse(version.substring(4));
        assertEquals(v.get("Protocol-Version"), "1.3");
        assertTrue(String.valueOf(v.get("Browser")).startsWith("Rhino/"));

        for (final String path : new String[] {"/json", "/json/list"}) {
            final String list = get(path);
            assertTrue(list.startsWith("200 "), list);
            final List<Object> targets = (List<Object>) Json.parse(list.substring(4));
            assertEquals(targets.size(), 1);
            final Map<String, Object> target = (Map<String, Object>) targets.get(0);
            assertEquals(target.get("type"), "node");
            assertEquals(target.get("webSocketDebuggerUrl"), server.webSocketUrl());
            assertTrue(
                    String.valueOf(target.get("devtoolsFrontendUrl"))
                            .startsWith("devtools://devtools/bundled/js_app.html?"));
        }
        assertTrue(get("/elsewhere").startsWith("404 "));
    }

    @Test
    void hostHeaderMustBeAnAddress() throws Exception {
        assertTrue(
                rawRequest("GET /json HTTP/1.1\r\nHost: evil.example.com\r\n\r\n")
                        .startsWith("HTTP/1.1 400 "));
        assertTrue(
                rawRequest("GET /json HTTP/1.1\r\nHost: localhost:" + server.port() + "\r\n\r\n")
                        .startsWith("HTTP/1.1 200 "));
        assertTrue(
                rawRequest("GET /json HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                        .startsWith("HTTP/1.1 200 "));
        assertTrue(
                rawRequest("GET /json HTTP/1.1\r\nHost: [::1]:9229\r\n\r\n")
                        .startsWith("HTTP/1.1 200 "));
        assertTrue(rawRequest("GET /json HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400 "));
    }

    @Test
    void handshakeComputesTheAcceptKey() throws Exception {
        // the example from RFC 6455 section 1.3
        final String response =
                rawRequest(
                        "GET "
                                + path()
                                + " HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 101 "), response);
        assertTrue(
                response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="), response);
    }

    @Test
    void framesRoundTripThroughARealClient() throws Exception {
        try (CdpClient client = new CdpClient(server.webSocketUrl())) {
            // a message larger than a 16-bit frame length, both ways
            final String big = "x".repeat(70000);
            final Map<String, Object> result =
                    client.call("Runtime.evaluate", "expression", "'" + big + "'.length");
            @SuppressWarnings("unchecked")
            final Map<String, Object> value = (Map<String, Object>) result.get("result");
            assertEquals(((Number) value.get("value")).intValue(), 70000);
            final Map<String, Object> echoed =
                    client.call("Runtime.evaluate", "expression", "'" + big + "'");
            @SuppressWarnings("unchecked")
            final Map<String, Object> str = (Map<String, Object>) echoed.get("result");
            assertEquals(str.get("value"), big);
        }
    }

    @Test
    void secondClientIsRefused() throws Exception {
        try (CdpClient first = new CdpClient(server.webSocketUrl())) {
            assertNotNull(
                    first.call("Runtime.getIsolateId").get("id"), "the first client is served");
            final String response =
                    rawRequest(
                            "GET "
                                    + path()
                                    + " HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 403 "), response);
        }
    }

    private String path() {
        final String url = server.webSocketUrl();
        return url.substring(url.lastIndexOf('/'));
    }

    private String rawRequest(final String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            final OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            final InputStream in = socket.getInputStream();
            final StringBuilder sb = new StringBuilder();
            final byte[] buf = new byte[4096];
            try {
                for (int n; (n = in.read(buf)) > 0; ) {
                    sb.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
                    if (sb.indexOf("\r\n\r\n") >= 0
                            && (sb.toString().startsWith("HTTP/1.1 101")
                                    || sb.indexOf("Content-Length") < 0
                                    || bodyComplete(sb))) {
                        break;
                    }
                }
            } catch (final java.net.SocketTimeoutException e) {
                // whatever arrived
            }
            return sb.toString();
        }
    }

    private static boolean bodyComplete(final StringBuilder sb) {
        final String s = sb.toString();
        final int headerEnd = s.indexOf("\r\n\r\n");
        final int lengthAt = s.indexOf("Content-Length: ");
        if (lengthAt < 0) {
            return true;
        }
        final int length =
                Integer.parseInt(s.substring(lengthAt + 16, s.indexOf("\r\n", lengthAt)).trim());
        return s.length() - headerEnd - 4 >= length;
    }
}
