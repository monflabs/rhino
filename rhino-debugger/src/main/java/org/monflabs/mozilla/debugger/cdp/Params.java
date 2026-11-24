/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.cdp;

import java.util.List;
import java.util.Map;

/** Typed access to a request's parameters. */
final class Params {
    private final Map<String, Object> map;

    Params(final Object params) {
        this.map = params instanceof Map<?, ?> m ? castMap(m) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    boolean has(final String name) {
        return map.get(name) != null;
    }

    Object raw(final String name) {
        return map.get(name);
    }

    String string(final String name) throws CdpError {
        final Object v = map.get(name);
        if (v == null) {
            throw CdpError.invalidParams(name + " is required");
        }
        return v.toString();
    }

    String string(final String name, final String fallback) {
        final Object v = map.get(name);
        return v == null ? fallback : v.toString();
    }

    int integer(final String name) throws CdpError {
        final Object v = map.get(name);
        if (!(v instanceof Number n)) {
            throw CdpError.invalidParams(name + " must be an integer");
        }
        return n.intValue();
    }

    int integer(final String name, final int fallback) {
        final Object v = map.get(name);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    boolean bool(final String name, final boolean fallback) {
        final Object v = map.get(name);
        return v instanceof Boolean b ? b : fallback;
    }

    Params object(final String name) throws CdpError {
        final Object v = map.get(name);
        if (!(v instanceof Map)) {
            throw CdpError.invalidParams(name + " must be an object");
        }
        return new Params(v);
    }

    Params objectOrNull(final String name) {
        final Object v = map.get(name);
        return v instanceof Map ? new Params(v) : null;
    }

    List<?> list(final String name) {
        final Object v = map.get(name);
        return v instanceof List<?> l ? l : List.of();
    }
}
