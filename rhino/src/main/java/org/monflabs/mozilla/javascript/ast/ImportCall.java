/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.ast;

import org.monflabs.mozilla.javascript.Token;

/**
 * A dynamic import: {@code import(specifier [, options])} (ES2020). Node type is {@link
 * Token#IMPORT_CALL}.
 */
public class ImportCall extends AstNode {

    private AstNode specifier;
    private AstNode options;
    private String referrer;

    {
        type = Token.IMPORT_CALL;
    }

    public ImportCall(int pos, int len) {
        super(pos, len);
    }

    public AstNode getSpecifier() {
        return specifier;
    }

    public void setSpecifier(AstNode specifier) {
        assertNotNull(specifier);
        this.specifier = specifier;
        specifier.setParent(this);
    }

    /** The options argument (import attributes), or null. */
    public AstNode getOptions() {
        return options;
    }

    public void setOptions(AstNode options) {
        this.options = options;
        if (options != null) {
            options.setParent(this);
        }
    }

    /** The name of the script or module the import is written in. */
    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth)
                + "import("
                + specifier.toSource(0)
                + (options == null ? "" : ", " + options.toSource(0))
                + ")";
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            specifier.visit(v);
            if (options != null) {
                options.visit(v);
            }
        }
    }
}
