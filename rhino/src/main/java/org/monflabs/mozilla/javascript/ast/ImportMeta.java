/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.ast;

import org.monflabs.mozilla.javascript.Token;

/**
 * The {@code import.meta} meta-property of a module (ECMA-262 13.3.12). Like {@code this}, it is
 * not an assignment target.
 */
public class ImportMeta extends KeywordLiteral {

    public ImportMeta(int pos, int len) {
        super(pos, len, Token.THIS);
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth) + "import.meta";
    }
}
