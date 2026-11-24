/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

/**
 * A property as a debugger shows it.
 *
 * @param name the name, for display
 * @param key the property key: a {@link String}, or a symbol engine object
 * @param value the value, an engine object, or null for an accessor property
 * @param getter the getter, or null
 * @param setter the setter, or null
 * @param writable whether a data property is writable
 * @param enumerable whether the property is enumerable
 * @param configurable whether the property is configurable
 * @param isOwn whether the property is the object's own
 * @param wasThrown whether reading the value threw, in which case {@code value} is what was thrown
 */
public record DebugProperty(
        String name,
        Object key,
        Object value,
        Object getter,
        Object setter,
        boolean writable,
        boolean enumerable,
        boolean configurable,
        boolean isOwn,
        boolean wasThrown) {}
