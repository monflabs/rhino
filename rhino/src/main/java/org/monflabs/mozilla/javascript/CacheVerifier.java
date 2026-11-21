/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import org.monflabs.mozilla.javascript.config.RhinoConfig;

/**
 * A stress mode ({@code -Drhino.verifyCaches=true}) that compares every answer of a shortcut with
 * the lookup it replaces: {@link PropertyCache}, {@link GlobalNameCache} and the variables read at
 * a fixed position ({@link NativeCall#getVar}, {@link ScriptRuntime#getOuterVar}). The rules these
 * shortcuts rely on are enforced in other classes (the slot maps, the compiler); a difference is a
 * rule that was broken, and stops the script with an {@link IllegalStateException}.
 */
final class CacheVerifier {

    static final boolean ON = RhinoConfig.get("rhino.verifyCaches", false);

    private CacheVerifier() {}

    /** The slot that a lookup of the name finds on the prototype chain, from proto on. */
    static void inheritedSlot(Scriptable proto, String name, Slot<?> cached) {
        for (Scriptable p = proto; p != null; p = p.getPrototype()) {
            if (!(p instanceof ScriptableObject)) {
                break;
            }
            Slot<?> found = ((ScriptableObject) p).getMap().query(name, 0);
            if (found != null) {
                if (found != cached) {
                    throw fail("property cache: another slot for " + name);
                }
                return;
            }
        }
        throw fail("property cache: no slot for " + name);
    }

    /** The slot that a lookup of the name finds in the top scope, then in the global object. */
    static void globalSlot(TopLevel top, String name, Slot<?> cached, boolean lexical) {
        Slot<?> found = top.getMap().query(name, 0);
        if (lexical ? found != cached : found != null) {
            throw fail("global name cache: another lexical binding for " + name);
        }
        if (!lexical && top.getGlobalThis().getMap().query(name, 0) != cached) {
            throw fail("global name cache: another property for " + name);
        }
    }

    /** The slot of a variable at a fixed position is the one the activation has for its name. */
    static void activationSlot(NativeCall activation, String name, Slot<?> cached) {
        if (activation.getMap().query(name, 0) != cached) {
            throw fail("activation variable: another slot for " + name);
        }
    }

    /** No scope between the code and the activation that owns the variable declares its name. */
    static void outerVar(VarScope scope, NativeCall owner, String name) {
        for (VarScope s = scope; s != owner; s = s.getParentScope()) {
            if (!(s instanceof ScopeObject)) {
                throw fail("outer variable " + name + ": read across " + s.getClass().getName());
            }
            if (((ScopeObject) s).getMap().query(name, 0) != null) {
                throw fail("outer variable " + name + ": shadowed in " + s.getClass().getName());
            }
        }
    }

    private static IllegalStateException fail(String message) {
        return new IllegalStateException("rhino.verifyCaches: " + message);
    }
}
