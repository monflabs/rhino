package org.monflabs.mozilla.javascript;

public class LocalScope extends DeclarationScope {
    private static final long serialVersionUID = -7471457301304454454L;

    public LocalScope(VarScope parentScope) {
        super(parentScope);
    }

    @Override
    public boolean isNestedScope() {
        return true;
    }

    @Override
    public void putConst(String name, VarScope start, Object value) {
        super.put(name, start, value);
    }

    /**
     * A new scope with the same parent and a copy of each binding of this one: the
     * CreatePerIterationEnvironment step of a loop with lexical declarations.
     */
    LocalScope copy() {
        LocalScope copy = new LocalScope(getParentScope());
        for (Slot<VarScope> slot : getMap()) {
            copy.getMap().add(copy, slot.copySlot());
        }
        return copy;
    }
}
