/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.util.List;

/**
 * An exception that escaped the outermost script frame.
 *
 * @param thrown the thrown value, an engine object
 * @param message its message
 * @param location where it was thrown, or null if unknown
 * @param frames the frames it unwound, innermost first, valid only during the call
 * @param context the context it was thrown in
 */
public record ExceptionEvent(
        Object thrown,
        String message,
        Location location,
        List<DebugFrame> frames,
        ExecutionContext context) {}
