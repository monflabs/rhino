/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/** A realm: one global object and everything created in it. */
public interface ExecutionContext {

    /**
     * The id, unique within the debugger.
     *
     * @return the id
     */
    int id();

    /**
     * A name for display.
     *
     * @return the name
     */
    String name();

    /**
     * The global object - an engine object, to be read through {@link DebugValues}.
     *
     * @return the global object
     */
    Object global();
}
