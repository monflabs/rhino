/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

import java.util.List;

/**
 * The engine is paused: why, where, and on what.
 *
 * @param reason the CDP reason: {@code "other"}, {@code "step"}, {@code "exception"}, {@code
 *     "debugCommand"}
 * @param frames the call stack, innermost first
 * @param hitBreakpointIds the server ids of the breakpoints hit, if any
 * @param exception the thrown value, when the reason is an exception, else null
 */
public record PauseState(
        String reason,
        List<CallFrame> frames,
        List<String> hitBreakpointIds,
        RemoteValue exception) {}
