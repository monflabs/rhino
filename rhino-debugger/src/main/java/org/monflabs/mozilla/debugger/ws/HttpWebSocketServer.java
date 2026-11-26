/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ws;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The little HTTP server a DevTools client expects: the discovery documents under {@code /json},
 * and one path that upgrades to a WebSocket. One client at a time; the {@code Host} header must
 * name an IP address or localhost, which is what keeps a web page from reaching the debugger by DNS
 * rebinding. A client that attaches while the previous one is closing waits for its session to end,
 * up to {@link #CLOSING_GRACE_MILLIS}.
 */
public final class HttpWebSocketServer implements AutoCloseable {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** How long a new client waits for the session of the previous one to end. */
    static final long CLOSING_GRACE_MILLIS = 2000;

    private final String host;
    private final int requestedPort;
    private final String path;
    private final Supplier<String> discovery;
    private final Supplier<String> version;
    private final Consumer<WebSocketConnection> onSession;
    private ServerSocket serverSocket;
    private Thread acceptor;
    private volatile Session active;

    private record Session(WebSocketConnection connection, CountDownLatch ended) {
        boolean running() {
            return ended.getCount() > 0;
        }
    }

    /**
     * Creates a server.
     *
     * @param host the host to bind
     * @param port the port to bind, 0 for any
     * @param path the path that upgrades to a WebSocket, {@code /} followed by an id
     * @param discovery produces the JSON text of {@code /json/list}
     * @param version produces the JSON text of {@code /json/version}
     * @param onSession runs a WebSocket session on its connection; called on the connection's
     *     thread
     */
    public HttpWebSocketServer(
            final String host,
            final int port,
            final String path,
            final Supplier<String> discovery,
            final Supplier<String> version,
            final Consumer<WebSocketConnection> onSession) {
        this.host = host;
        this.requestedPort = port;
        this.path = path;
        this.discovery = discovery;
        this.version = version;
        this.onSession = onSession;
    }

    /**
     * Binds and starts accepting.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, requestedPort));
        acceptor = new Thread(this::acceptLoop, "rhino-inspector");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * The port bound.
     *
     * @return the port
     */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * The host bound.
     *
     * @return the host
     */
    public String host() {
        return host;
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            final Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (final SocketException e) {
                return; // closed
            } catch (final IOException e) {
                continue;
            }
            Thread.ofVirtual().name("rhino-inspector-connection").start(() -> handle(socket));
        }
    }

    private void handle(final Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            final InputStream in = new BufferedInputStream(socket.getInputStream());
            final OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            final Request request = Request.read(in);
            if (request == null) {
                socket.close();
                return;
            }
            if (!hostAllowed(request.header("host"))) {
                respond(
                        out,
                        400,
                        "Bad Request",
                        "text/plain",
                        "Host header must be an IP address or localhost");
                socket.close();
                return;
            }
            final String target = request.target;
            if ("GET".equals(request.method)
                    && (target.equals("/json")
                            || target.equals("/json/list")
                            || target.startsWith("/json/list?"))) {
                respond(out, 200, "OK", "application/json; charset=UTF-8", discovery.get());
                socket.close();
            } else if ("GET".equals(request.method)
                    && (target.equals("/json/version") || target.startsWith("/json/version?"))) {
                respond(out, 200, "OK", "application/json; charset=UTF-8", version.get());
                socket.close();
            } else if ("GET".equals(request.method)
                    && target.equals(path)
                    && "websocket".equalsIgnoreCase(request.header("upgrade"))) {
                final String key = request.header("sec-websocket-key");
                if (key == null) {
                    respond(out, 400, "Bad Request", "text/plain", "missing Sec-WebSocket-Key");
                    socket.close();
                    return;
                }
                final Session previous = active;
                if (previous != null && previous.running()) {
                    try {
                        previous.ended.await(CLOSING_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                final Session session;
                synchronized (this) {
                    if (active != null && active.running()) {
                        respond(
                                out,
                                403,
                                "Forbidden",
                                "text/plain",
                                "Another client is already attached");
                        socket.close();
                        return;
                    }
                    session =
                            new Session(
                                    new WebSocketConnection(socket, in, out),
                                    new CountDownLatch(1));
                    active = session;
                }
                try {
                    out.write(
                            ("HTTP/1.1 101 Switching Protocols\r\n"
                                            + "Upgrade: websocket\r\n"
                                            + "Connection: Upgrade\r\n"
                                            + "Sec-WebSocket-Accept: "
                                            + accept(key)
                                            + "\r\n\r\n")
                                    .getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    onSession.accept(session.connection);
                } finally {
                    session.connection.close(1000, "");
                    session.ended.countDown();
                }
            } else {
                respond(out, 404, "Not Found", "text/plain", "");
                socket.close();
            }
        } catch (final IOException e) {
            try {
                socket.close();
            } catch (final IOException ignored) {
                // gone
            }
        }
    }

    /**
     * Whether a Host header is one we answer to: an IP address, or localhost, with or without a
     * port.
     *
     * @param hostHeader the header, or null
     * @return true if allowed
     */
    static boolean hostAllowed(final String hostHeader) {
        if (hostHeader == null) {
            return false;
        }
        String h = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            final int end = h.indexOf(']');
            if (end < 0) {
                return false;
            }
            final String ip6 = h.substring(1, end);
            return ip6.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.');
        }
        final int colon = h.lastIndexOf(':');
        if (colon >= 0) {
            final String port = h.substring(colon + 1);
            if (!port.isEmpty() && !port.chars().allMatch(Character::isDigit)) {
                return false;
            }
            h = h.substring(0, colon);
        }
        if (h.equals("localhost")) {
            return true;
        }
        return !h.isEmpty() && h.chars().allMatch(c -> Character.isDigit(c) || c == '.');
    }

    /**
     * The Sec-WebSocket-Accept value for a key.
     *
     * @param key the client's key
     * @return the accept value
     */
    static String accept(final String key) {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return Base64.getEncoder()
                    .encodeToString(
                            sha1.digest((key + GUID).getBytes(StandardCharsets.ISO_8859_1)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(
            final OutputStream out,
            final int status,
            final String reason,
            final String type,
            final String body)
            throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(
                ("HTTP/1.1 "
                                + status
                                + " "
                                + reason
                                + "\r\n"
                                + "Content-Type: "
                                + type
                                + "\r\n"
                                + "Content-Length: "
                                + bytes.length
                                + "\r\n"
                                + "Cache-Control: no-cache\r\n"
                                + "Connection: close\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    @Override
    public void close() {
        final Session session = active;
        if (session != null) {
            session.connection.close(1001, "server closing");
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (final IOException ignored) {
            // closing anyway
        }
    }

    /** An HTTP request line and its headers. */
    private static final class Request {
        final String method;
        final String target;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Request(final String method, final String target) {
            this.method = method;
            this.target = target;
        }

        String header(final String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        static Request read(final InputStream in) throws IOException {
            final String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }
            final String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return null;
            }
            final Request request = new Request(parts[0], parts[1]);
            for (String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
                final int colon = line.indexOf(':');
                if (colon > 0) {
                    request.headers.put(
                            line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).trim());
                }
            }
            return request;
        }

        private static String readLine(final InputStream in) throws IOException {
            final ByteArrayOutputStream line = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) >= 0) {
                if (c == '\n') {
                    break;
                }
                if (c != '\r') {
                    line.write(c);
                }
                if (line.size() > 16384) {
                    throw new IOException("header line too long");
                }
            }
            if (c < 0 && line.size() == 0) {
                return null;
            }
            return line.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
