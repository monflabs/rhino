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

    /**
     * Initializes a const of this block: the binding was created (as undefined) when the block was
     * entered, and becomes read-only once initialized.
     */
    @Override
    public void putConst(String name, VarScope start, Object value) {
        Slot<VarScope> slot = getMap().modify(this, name, 0, 0);
        slot.value = value;
        slot.setAttributes(
                slot.getAttributes() | ScriptableObject.READONLY | ScriptableObject.PERMANENT);
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
