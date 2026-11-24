/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.NavigableSet;
import org.monflabs.mozilla.api.debugger.DebugScript;
import org.monflabs.mozilla.api.debugger.ExecutionContext;
import org.monflabs.mozilla.api.debugger.Location;
import org.monflabs.mozilla.javascript.debug.DebuggableScript;

final class ScriptImpl implements DebugScript {
    private final String id;
    final DebuggableScript root;
    private final String source;
    private final NavigableSet<Integer> lines;
    private volatile ExecutionContext context;

    ScriptImpl(String id, DebuggableScript root, String source) {
        this.id = id;
        this.root = root;
        this.source = source == null ? "" : source;
        this.lines = DebuggerImpl.allLines(root);
    }

    void setContext(ExecutionContext context) {
        if (this.context == null) {
            this.context = context;
        }
    }

    NavigableSet<Integer> lines() {
        return lines;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String url() {
        return root.getSourceName();
    }

    @Override
    public String name() {
        return root.getSourceName();
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public String hash() {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-1")
                                    .digest(
                                            source.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int endLine() {
        int n = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    @Override
    public int endColumn() {
        return source.length() - (source.lastIndexOf('\n') + 1);
    }

    @Override
    public int length() {
        return source.length();
    }

    @Override
    public boolean isEval() {
        return false;
    }

    @Override
    public boolean isModule() {
        return false;
    }

    @Override
    public ExecutionContext context() {
        return context;
    }

    @Override
    public List<Location> possibleBreakpoints(
            int startLine, int startColumn, int endLine, int endColumn) {
        List<Location> result = new ArrayList<>();
        for (int line : lines) {
            int l = line - 1;
            if (l >= startLine && l <= endLine) {
                result.add(new Location(this, l, 0));
            }
        }
        return result;
    }
}
