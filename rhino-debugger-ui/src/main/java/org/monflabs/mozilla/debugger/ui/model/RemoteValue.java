/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

import java.util.Map;

/**
 * A value living in the engine, as the protocol describes it (a CDP {@code RemoteObject}). A
 * primitive carries its {@link #value}; an object carries an {@link #objectId} its properties can
 * be fetched with - but only until the next resume, when the server releases the whole pause's
 * objects.
 *
 * @param type the CDP type: {@code "object"}, {@code "function"}, {@code "string"}, {@code
 *     "number"}, {@code "boolean"}, {@code "symbol"}, {@code "undefined"}, {@code "bigint"}
 * @param subtype the subtype for an object: {@code "array"}, {@code "null"}, {@code "error"}, ...,
 *     or null
 * @param className the object's class name, or null
 * @param description a human-readable rendering the server supplies for objects
 * @param value the value itself, for a primitive returned by value
 * @param unserializable a token for a value JSON cannot carry ({@code NaN}, {@code Infinity}, ...),
 *     or null
 * @param objectId the id to fetch properties with, for an object, or null
 */
public record RemoteValue(
        String type,
        String subtype,
        String className,
        String description,
        Object value,
        String unserializable,
        String objectId) {

    /**
     * Reads a value from a CDP {@code RemoteObject} map.
     *
     * @param map the map, or null
     * @return the value, or null when the map was null
     */
    public static RemoteValue of(final Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new RemoteValue(
                str(map.get("type")),
                str(map.get("subtype")),
                str(map.get("className")),
                str(map.get("description")),
                map.get("value"),
                str(map.get("unserializableValue")),
                str(map.get("objectId")));
    }

    private static String str(final Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Whether this value has properties worth expanding. */
    public boolean expandable() {
        return objectId != null
                && ("object".equals(type) || "function".equals(type))
                && !"null".equals(subtype);
    }

    /**
     * A one-line rendering, as a console or a tree cell shows it: a string in quotes, a function by
     * its description's first line, an object by its description, a primitive by its value.
     *
     * @return the text
     */
    public String display() {
        if (type == null) {
            return "undefined";
        }
        switch (type) {
            case "string":
                return '"' + String.valueOf(value) + '"';
            case "undefined":
                return "undefined";
            case "function":
                {
                    final String d = description != null ? description : "function";
                    final int newline = d.indexOf('\n');
                    return newline < 0 ? d : d.substring(0, newline) + " …";
                }
            case "object":
                if ("null".equals(subtype)) {
                    return "null";
                }
                return description != null
                        ? description
                        : (className != null ? className : "Object");
            default:
                if (unserializable != null) {
                    return unserializable;
                }
                if (value != null) {
                    return String.valueOf(value);
                }
                return description != null ? description : type;
        }
    }
}
