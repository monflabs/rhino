/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.ast;

import org.monflabs.mozilla.javascript.Token;

/**
 * A class declaration or expression (ECMA-262 15.7). Its methods are kept as two object literals,
 * one for the prototype and one for the constructor (static members); the constructor is a
 * function, or null for the default constructor. {@code scope} holds the binding of the class name
 * inside the class.
 */
public class ClassNode extends AstNode {

    private Name className;
    private AstNode heritage;
    private FunctionNode constructor;
    private ObjectLiteral prototypeMembers;
    private ObjectLiteral staticMembers;
    private Scope scope;
    private int sourceStart;
    private int sourceEnd;
    private boolean hasStaticElements;

    {
        type = Token.CLASS;
    }

    public ClassNode(int pos) {
        super(pos);
    }

    /** Whether the class has static fields, which are defined once its name is bound. */
    public boolean hasStaticElements() {
        return hasStaticElements;
    }

    public void setHasStaticElements() {
        hasStaticElements = true;
    }

    public Name getClassName() {
        return className;
    }

    public void setClassName(Name className) {
        this.className = className;
        if (className != null) {
            className.setParent(this);
        }
    }

    public AstNode getHeritage() {
        return heritage;
    }

    public void setHeritage(AstNode heritage) {
        this.heritage = heritage;
        if (heritage != null) {
            heritage.setParent(this);
        }
    }

    public FunctionNode getConstructor() {
        return constructor;
    }

    public void setConstructor(FunctionNode constructor) {
        this.constructor = constructor;
        if (constructor != null) {
            constructor.setParent(this);
        }
    }

    public ObjectLiteral getPrototypeMembers() {
        return prototypeMembers;
    }

    public void setPrototypeMembers(ObjectLiteral members) {
        this.prototypeMembers = members;
        members.setParent(this);
    }

    public ObjectLiteral getStaticMembers() {
        return staticMembers;
    }

    public void setStaticMembers(ObjectLiteral members) {
        this.staticMembers = members;
        members.setParent(this);
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public int getSourceStart() {
        return sourceStart;
    }

    public int getSourceEnd() {
        return sourceEnd;
    }

    public void setSourceBounds(int start, int end) {
        this.sourceStart = start;
        this.sourceEnd = end;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder(makeIndent(depth)).append("class");
        if (className != null) {
            sb.append(' ').append(className.toSource(0));
        }
        if (heritage != null) {
            sb.append(" extends ").append(heritage.toSource(0));
        }
        return sb.append(" {...}").toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (className != null) {
                className.visit(v);
            }
            if (heritage != null) {
                heritage.visit(v);
            }
            if (constructor != null) {
                constructor.visit(v);
            }
            prototypeMembers.visit(v);
            staticMembers.visit(v);
        }
    }
}
