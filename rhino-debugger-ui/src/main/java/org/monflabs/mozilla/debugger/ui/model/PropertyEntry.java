/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.model;

/**
 * One property of an object, as the scopes and watches trees show it.
 *
 * @param name the property name
 * @param value its value
 * @param own whether it is an own property rather than inherited
 * @param enumerable whether it is enumerable
 * @param wasThrown whether reading it threw (a getter that failed)
 */
public record PropertyEntry(
        String name, RemoteValue value, boolean own, boolean enumerable, boolean wasThrown) {}
