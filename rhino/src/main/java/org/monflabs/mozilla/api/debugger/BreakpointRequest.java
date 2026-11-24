/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * Where a breakpoint goes. Exactly one of {@code url}, {@code urlRegex} and {@code scriptId} names
 * the script; the location is the first statement at or after the line and column.
 *
 * @param url the script's exact url, or null
 * @param urlRegex a regular expression the url must match, or null
 * @param scriptId a script id, or null
 * @param line the line, zero based
 * @param column the column, zero based; negative for any column on the line
 * @param condition an expression that must be truthy for the breakpoint to pause, or null
 */
public record BreakpointRequest(
        String url, String urlRegex, String scriptId, int line, int column, String condition) {

    /**
     * A breakpoint by url and line.
     *
     * @param url the script's url
     * @param line the line, zero based
     * @return the request
     */
    public static BreakpointRequest at(final String url, final int line) {
        return new BreakpointRequest(url, null, null, line, -1, null);
    }
}
