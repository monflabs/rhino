/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Serves modules whose exports are Java values, by exact specifier. */
public final class JavaModuleLoader implements ModuleLoader {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public JavaModuleLoader() {}

    /** Adds the module {@code name}; the key {@code default} of {@code exports} is its default. */
    public JavaModuleLoader add(String name, Map<String, Object> exports) {
        modules.put(Objects.requireNonNull(name, "name"), Module.values(name, exports));
        return this;
    }

    @Override
    public Module load(String specifier, Module referrer) {
        return modules.get(specifier);
    }

    @Override
    public String toString() {
        return "JavaModuleLoader" + modules.keySet();
    }
}
