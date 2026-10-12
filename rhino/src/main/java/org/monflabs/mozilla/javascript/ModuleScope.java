/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * The scope of the imported bindings of a module, between its body and the global scope: an
 * imported name reads the exporting module's binding (a live binding) and cannot be assigned.
 */
final class ModuleScope extends ScopeObject {
    @Serial private static final long serialVersionUID = 1L;

    private final Map<String, ModuleRecord.Binding> imports = new HashMap<>();

    ModuleScope(TopLevel realm) {
        super(realm);
    }

    void addImport(String localName, ModuleRecord.Binding binding) {
        imports.put(localName, binding);
    }

    @Override
    public Object get(String name, VarScope start) {
        ModuleRecord.Binding binding = imports.get(name);
        if (binding != null) {
            return binding.module().readBinding(Context.getCurrentContext(), binding.bindingName());
        }
        return super.get(name, start);
    }

    @Override
    public boolean has(String name, VarScope start) {
        return imports.containsKey(name) || super.has(name, start);
    }

    @Override
    public void put(String name, VarScope start, Object value) {
        if (imports.containsKey(name)) {
            throw ScriptRuntime.typeErrorById("msg.module.binding.immutable", name);
        }
        super.put(name, start, value);
    }
}
