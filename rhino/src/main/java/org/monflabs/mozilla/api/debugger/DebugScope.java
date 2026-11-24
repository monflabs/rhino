/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * One link of a frame's scope chain.
 *
 * @param type what kind of scope
 * @param object the object holding the scope's bindings, an engine object
 * @param name the scope's name where one applies - the function's for a local or closure scope - or
 *     null
 */
public record DebugScope(ScopeType type, Object object, String name) {

    /** The kinds of scope. */
    public enum ScopeType {
        /** The global object. */
        GLOBAL,
        /** The paused function's own variables. */
        LOCAL,
        /** An enclosing function's variables. */
        CLOSURE,
        /** A block's lexical declarations. */
        BLOCK,
        /** A catch clause's parameter. */
        CATCH,
        /** A {@code with} statement's object. */
        WITH,
        /** The global lexical declarations of scripts. */
        SCRIPT
    }
}
