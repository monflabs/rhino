/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

/**
 * One scope in a frame's scope chain.
 *
 * @param type the kind: {@code "local"}, {@code "closure"}, {@code "global"}, ...
 * @param name a name for a named scope (a closure's function), or null
 * @param object the scope object, whose properties are the variables
 */
public record Scope(String type, String name, RemoteValue object) {
    /** A label for the sidebar: the name when there is one, else the capitalised type. */
    public String label() {
        if (name != null && !name.isEmpty()) {
            return capitalize(type) + ": " + name;
        }
        return capitalize(type);
    }

    private static String capitalize(final String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
