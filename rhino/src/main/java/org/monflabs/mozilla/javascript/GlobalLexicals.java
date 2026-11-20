/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/** The top-level let and const declarations of a script: their names, and which are const. */
record GlobalLexicals(String[] names, boolean[] consts) implements java.io.Serializable {}
