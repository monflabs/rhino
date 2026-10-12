/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A module a {@link ModuleLoader} hands to the engine: either JavaScript source ({@link #source})
 * or a set of Java values ({@link #values}). Its {@link #name()} identifies it in its realm: two
 * imports resolving to the same name get the same module instance.
 */
public final class Module {
    private final String name;
    private final String text;
    private final Map<String, Object> exports;
    private final Object origin;

    private Module(String name, String text, Map<String, Object> exports, Object origin) {
        this.name = Objects.requireNonNull(name, "name");
        this.text = text;
        this.exports = exports;
        this.origin = origin;
    }

    /** A module whose source is {@code text}. */
    public static Module source(String name, String text) {
        return source(name, text, null);
    }

    /**
     * A module whose source is {@code text}; {@code origin} is opaque to the engine and lets the
     * loader resolve the module's relative imports (a {@code Path}, a resource, ...).
     */
    public static Module source(String name, String text, Object origin) {
        return new Module(name, Objects.requireNonNull(text, "text"), null, origin);
    }

    /** A module whose exports are Java values (the key {@code default} is the default export). */
    public static Module values(String name, Map<String, Object> exports) {
        return new Module(
                name, null, new LinkedHashMap<>(Objects.requireNonNull(exports, "exports")), null);
    }

    /** The importing side of an entry module, to resolve its relative imports. */
    public static Module referrer(String name, Object origin) {
        return new Module(name, null, null, origin);
    }

    public String name() {
        return name;
    }

    /** The source, or null for a module of Java values. */
    public String text() {
        return text;
    }

    /** The Java values, or null for a source module. */
    public Map<String, Object> exports() {
        return exports;
    }

    public Object origin() {
        return origin;
    }

    @Override
    public String toString() {
        return "Module " + name;
    }
}
