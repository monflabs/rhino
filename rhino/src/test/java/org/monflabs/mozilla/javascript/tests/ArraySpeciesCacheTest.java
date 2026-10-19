/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The methods that create arrays through ArraySpeciesCreate create a plain array while the array
 * constructor and its species are the original ones; any change to them after a first use is seen.
 */
class ArraySpeciesCacheTest {

    private static final String SPECIES =
            "function C(n) { this.n = n; } "
                    + "function kinds(a) { var rs = [a.slice(0, 1), a.concat([]), a.splice(0, 0),"
                    + "  a.map(x => x), a.filter(x => true)], k = '';"
                    + "  for (var i = 0; i < 5; i++)"
                    + "    k += rs[i] instanceof C ? 'C' : Array.isArray(rs[i]) ? 'A' : '?';"
                    + "  return k; }"
                    + "var r = [kinds([1])];";

    @Test
    void replacedConstructor() {
        String expected = "AAAAA CCCCC AAAAA";
        Utils.assertGatedBehavior(
                expected,
                expected,
                SPECIES
                        + "Array.prototype.constructor = { [Symbol.species]: C };"
                        + "r.push(kinds([1]));"
                        + "Array.prototype.constructor = Array;"
                        + "r.push(kinds([1]));"
                        + "r.join(' ')");
    }

    @Test
    void redefinedSpecies() {
        String expected = "AAAAA CCCCC AAAAA";
        Utils.assertGatedBehavior(
                expected,
                expected,
                SPECIES
                        + "var get = Object.getOwnPropertyDescriptor(Array, Symbol.species).get;"
                        + "Object.defineProperty(Array, Symbol.species, { get() { return C; },"
                        + "  configurable: true });"
                        + "r.push(kinds([1]));"
                        + "Object.defineProperty(Array, Symbol.species, { get: get });"
                        + "r.push(kinds([1]));"
                        + "r.join(' ')");
    }

    @Test
    void speciesGetterCalledOnlyByTheMethods() {
        String expected = "AAAAA CCCCC 5";
        Utils.assertGatedBehavior(
                expected,
                expected,
                SPECIES
                        + "var calls = 0;"
                        + "Object.defineProperty(Array, Symbol.species, {"
                        + "  get() { calls++; return C; }, configurable: true });"
                        + "r.push(kinds([1]), calls);"
                        + "r.join(' ')");
    }

    @Test
    void ownConstructorAndOtherPrototypes() {
        String expected = "AAAAA CCCCC CCCCC";
        Utils.assertGatedBehavior(
                expected,
                expected,
                SPECIES
                        + "var a = [1]; a.constructor = { [Symbol.species]: C };"
                        + "r.push(kinds(a));"
                        + "var b = [1];"
                        + "Object.setPrototypeOf(b, Object.create(Array.prototype,"
                        + "  { constructor: { value: { [Symbol.species]: C } } }));"
                        + "r.push(kinds(b));"
                        + "r.join(' ')");
    }

    @Test
    void deletedConstructor() {
        String expected = "AAAAA AAAAA AAAAA";
        Utils.assertGatedBehavior(
                expected,
                expected,
                SPECIES
                        + "delete Array.prototype.constructor;"
                        + "r.push(kinds([1]));"
                        + "Array.prototype.constructor = Array;"
                        + "r.push(kinds([1]));"
                        + "r.join(' ')");
    }
}
