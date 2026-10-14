/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.modules;

/**
 * Resolves and loads the module an {@code import} names. The engine asks each loader of its chain
 * in order; the first non-null answer wins.
 *
 * <p>For an import with the attribute {@code type: "json"}, the engine parses the text of the
 * module the loader returns as JSON, into a module whose default export is the value (a module of
 * Java values is used as is).
 */
@FunctionalInterface
public interface ModuleLoader {
    /**
     * Loads the module a specifier names, or returns null if this loader does not have it.
     *
     * <p>{@code referrer} is the module the {@code import} was written in, null for an entry
     * module. A loader resolves a relative specifier ({@code ./x}, {@code ../x}) against a referrer
     * it recognises as its own (by {@link Module#origin()}), and returns null for a foreign one.
     *
     * @param specifier the text between the quotes of the import
     * @param referrer the importing module, or null
     * @return the module, or null
     */
    Module load(String specifier, Module referrer);
}
