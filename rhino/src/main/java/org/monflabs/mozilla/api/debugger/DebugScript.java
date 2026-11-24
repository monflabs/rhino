/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.api.debugger;

import java.util.List;

/** A compiled script: a source the engine has turned into code. */
public interface DebugScript {

    /**
     * The id, unique within the debugger.
     *
     * @return the id
     */
    String id();

    /**
     * The url. A source loaded from a URL keeps it; any other gets a unique {@code rhino:} url, so
     * that every script can be named in a breakpoint.
     *
     * @return the url
     */
    String url();

    /**
     * The name the source was given - a file name, {@code <eval>}, and so on.
     *
     * @return the name
     */
    String name();

    /**
     * The source text.
     *
     * @return the text
     */
    String source();

    /**
     * A digest of the source, stable across engines.
     *
     * @return the digest
     */
    String hash();

    /**
     * The last line, zero based.
     *
     * @return the last line
     */
    int endLine();

    /**
     * The column after the last character of the last line, zero based.
     *
     * @return the end column
     */
    int endColumn();

    /**
     * The length of the source in characters.
     *
     * @return the length
     */
    int length();

    /**
     * Whether this is code handed to {@code eval}.
     *
     * @return true for eval code
     */
    boolean isEval();

    /**
     * Whether this is a module rather than a script.
     *
     * @return true for a module
     */
    boolean isModule();

    /**
     * The context the script was compiled for.
     *
     * @return the context
     */
    ExecutionContext context();

    /**
     * The locations a breakpoint can be set at within a range, in order.
     *
     * @param startLine first line, zero based
     * @param startColumn first column on that line, zero based
     * @param endLine last line, inclusive; negative for the end of the script
     * @param endColumn last column on that line, exclusive
     * @return the locations
     */
    List<Location> possibleBreakpoints(int startLine, int startColumn, int endLine, int endColumn);
}
