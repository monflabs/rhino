/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.util.List;

/**
 * A call of a {@code console} function.
 *
 * @param type the function's name: {@code log}, {@code warn}, {@code error}, ...
 * @param arguments the arguments, engine objects
 * @param location where it was called from, or null if unknown
 * @param context the context it was called in
 */
public record ConsoleEvent(
        String type, List<Object> arguments, Location location, ExecutionContext context) {}
