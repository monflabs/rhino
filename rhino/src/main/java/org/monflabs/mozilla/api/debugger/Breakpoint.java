/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.util.List;

/** A breakpoint that has been set. */
public interface Breakpoint {

    /**
     * The id, unique within the debugger.
     *
     * @return the id
     */
    String id();

    /**
     * What was asked for.
     *
     * @return the request
     */
    BreakpointRequest request();

    /**
     * Where the breakpoint landed: empty while it is pending, otherwise one location per script it
     * matched.
     *
     * @return the locations
     */
    List<Location> locations();
}
