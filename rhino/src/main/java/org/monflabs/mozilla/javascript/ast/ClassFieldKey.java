/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.ast;

import org.monflabs.mozilla.javascript.Token;

/**
 * The key of a class field, in the object literal of the members of its class (a computed key of
 * that literal): the entry is a field rather than a property. Node type is {@link Token#FIELD_KEY}.
 */
public class ClassFieldKey extends AstNode {

    private AstNode key;
    private boolean named;
    private boolean method;
    private boolean staticBlock;

    {
        type = Token.FIELD_KEY;
    }

    public ClassFieldKey(int pos, AstNode key, boolean named) {
        super(pos);
        setKey(key);
        this.named = named;
    }

    /** The expression of the key: a string literal for a written name, else the computed key. */
    public AstNode getKey() {
        return key;
    }

    public void setKey(AstNode key) {
        assertNotNull(key);
        this.key = key;
        key.setParent(this);
    }

    /** Whether the entry is a private method or accessor, whose key is its private name. */
    public boolean isMethod() {
        return method;
    }

    public void setMethod() {
        method = true;
    }

    /** Whether the entry is a class static block, whose value is its code as a method. */
    public boolean isStaticBlock() {
        return staticBlock;
    }

    public void setStaticBlock() {
        staticBlock = true;
    }

    /** Whether the initializer is an anonymous function definition that the key names. */
    public boolean isNamed() {
        return named;
    }

    @Override
    public String toSource(int depth) {
        return key.toSource(depth);
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            key.visit(v);
        }
    }
}
