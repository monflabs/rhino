/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/** Which thrown exceptions pause execution. */
public enum PauseOnExceptions {
    /** None. */
    NONE,
    /** Only those that escape the outermost script frame. */
    UNCAUGHT,
    /** Only those that do not. Approximate: decided when the exception is thrown. */
    CAUGHT,
    /** Every one, where it is thrown. */
    ALL
}
