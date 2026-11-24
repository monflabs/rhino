/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * Lets an embedding bound what a script can allocate. The engine reports the size of what it is
 * about to create, on the paths that grow it; the limit throws (usually a {@link
 * Context#reportRuntimeError(String) runtime error} or a {@code RangeError}) to refuse. Set it with
 * {@link Context#setResourceLimits}.
 *
 * <p>The reports are made only where the size is large or the path is rare, so that a limit costs
 * nothing on the usual operations; each {@link Kind} says what is reported.
 */
@FunctionalInterface
public interface ResourceLimits {

    enum Kind {
        /**
         * The length of a string about to be built (concatenation, {@code repeat}, {@code pad*},
         * {@code join}, {@code replace}...); strings under {@link #STRING_FLOOR} are not reported.
         */
        STRING,
        /** The capacity an array grows to (a dense array growing past its allocation). */
        ARRAY,
        /**
         * The number of properties of an object when its slot map moves to a hash table (at {@code
         * 1536} properties), not each addition.
         */
        OBJECT,
        /** The number of entries of a {@code Map} or {@code Set} after an addition. */
        COLLECTION,
        /** The length in bytes of an {@code ArrayBuffer} about to be allocated. */
        BUFFER,
        /** The number of symbols of the {@code Symbol.for} registry after an addition. */
        SYMBOLS,
        /** The length of the source that {@code eval} is about to compile. */
        EVAL
    }

    /** Strings shorter than this are not reported. */
    int STRING_FLOOR = 4096;

    /**
     * @param kind what is being created
     * @param size its size, in the unit of the kind
     * @throws RuntimeException to refuse
     */
    void check(Context cx, Kind kind, long size);
}
