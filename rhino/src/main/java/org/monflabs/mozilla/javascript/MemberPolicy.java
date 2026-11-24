/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

/**
 * Decides which members of a Java class scripts can see, and under which name. It is consulted when
 * the members of a class are reflected (see {@link JavaMembers}), so its answers are cached with
 * the class: a policy must answer the same for the whole life of its {@link ClassCache}, or be
 * replaced with {@link ClassCache#setMemberPolicy(MemberPolicy)}, which empties the cache.
 *
 * <p>Two policies are interchangeable when they are {@code equals}: {@link
 * ClassCache.Snapshot#restore} only accepts a snapshot taken under an equal policy.
 */
public interface MemberPolicy {

    /**
     * @return false to hide the class: scripts see no method, field or constructor of it
     */
    default boolean isClassVisible(Class<?> cls) {
        return true;
    }

    /**
     * @return the name scripts use for the member, or null to hide it. The default is the Java
     *     name.
     */
    default String publicName(Class<?> cls, Member member) {
        return member.getName();
    }

    /**
     * @return true when the parameter does not accept null: an overload for which a script passes
     *     null there is ruled out, see {@link Context#FEATURE_NONNULL_PARAMETERS}
     */
    default boolean isParameterNonNull(Executable method, int index) {
        return false;
    }
}
