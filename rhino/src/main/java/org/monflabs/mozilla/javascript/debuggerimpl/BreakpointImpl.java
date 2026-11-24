/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.monflabs.mozilla.api.debugger.Breakpoint;
import org.monflabs.mozilla.api.debugger.BreakpointRequest;
import org.monflabs.mozilla.api.debugger.Location;

final class BreakpointImpl implements Breakpoint {
    private final String id;
    private final BreakpointRequest request;
    private final List<Location> locations = new CopyOnWriteArrayList<>();

    BreakpointImpl(String id, BreakpointRequest request) {
        this.id = id;
        this.request = request;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public BreakpointRequest request() {
        return request;
    }

    @Override
    public List<Location> locations() {
        return locations;
    }

    void resolveAgainst(ScriptImpl script, DebuggerImpl debugger) {
        if (!DebuggerImpl.urlMatches(request, script)) {
            return;
        }
        Integer line = script.lines().ceiling(request.line() + 1);
        if (line == null) {
            return;
        }
        Location location = new Location(script, line - 1, 0);
        for (Location l : locations) {
            if (l.script() == script) {
                return;
            }
        }
        locations.add(location);
        debugger.breakpointResolved(this, location);
    }

    boolean hits(ScriptImpl script, int line0) {
        for (Location l : locations) {
            if (l.script() == script && l.line() == line0) {
                return true;
            }
        }
        return false;
    }
}
