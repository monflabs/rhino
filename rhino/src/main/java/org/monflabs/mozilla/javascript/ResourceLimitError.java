/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.io.Serial;

/**
 * Thrown to terminate a script that exceeded a limit of its {@link ResourceScope}, or whose scope
 * was cancelled.
 *
 * <p>It is an {@link Error}, not a {@link RhinoException}: scripts cannot catch it with {@code
 * try/catch} (unless {@link Context#FEATURE_ENHANCED_JAVA_ACCESS} is on), and it is raised again at
 * the next safepoint if anything swallows it. It carries no stack trace. The embedder catches it
 * around the top-level call.
 */
public final class ResourceLimitError extends Error {
    @Serial private static final long serialVersionUID = 1L;

    /** The limit that was reached. */
    public enum Kind {
        /** The deadline passed. */
        TIME,
        /** The charged memory exceeded {@link ResourceLimits#maxMemoryBytes()}. */
        MEMORY,
        /** The thread allocated more than {@link ResourceLimits#maxAllocatedBytes()}. */
        ALLOCATION,
        /** {@link ResourceScope#cancel(String)} was called. */
        CANCELLED
    }

    private final Kind kind;

    ResourceLimitError(Kind kind, String message) {
        super(message, null, false, false);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
