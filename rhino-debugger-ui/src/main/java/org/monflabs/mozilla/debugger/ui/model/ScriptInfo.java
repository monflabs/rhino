/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

/**
 * A parsed script the server told us about.
 *
 * @param scriptId the server's id, the key for source and locations
 * @param url the script's url; the stable identity across re-parses
 * @param endLine the last line, zero based
 * @param module whether it is an ES module
 */
public record ScriptInfo(String scriptId, String url, int endLine, boolean module) {
    /** A short name for the navigator: the last path segment of the url. */
    public String shortName() {
        final int slash = url.lastIndexOf('/');
        return slash < 0 ? url : url.substring(slash + 1);
    }
}
