/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

import java.util.List;

/**
 * One frame of a paused call stack.
 *
 * @param callFrameId the server's id, valid only for this pause
 * @param functionName the function's name, or empty for an anonymous one
 * @param url the script's url
 * @param scriptId the script's id
 * @param line the current line, zero based
 * @param column the current column, zero based
 * @param scopeChain the scopes visible here, innermost first
 * @param thisObject the value of {@code this}
 */
public record CallFrame(
        String callFrameId,
        String functionName,
        String url,
        String scriptId,
        int line,
        int column,
        List<Scope> scopeChain,
        RemoteValue thisObject) {
    /** A label for the call-stack list. */
    public String label() {
        final String name =
                functionName == null || functionName.isEmpty() ? "(anonymous)" : functionName;
        final int slash = url == null ? -1 : url.lastIndexOf('/');
        final String file = url == null ? "?" : (slash < 0 ? url : url.substring(slash + 1));
        return name + "  " + file + ":" + (line + 1);
    }
}
