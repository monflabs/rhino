/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.testkit;

import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.javascript.ContextFactory;

/** The contract run against Rhino's own debugger: the example of how to use the kit. */
class RhinoDebuggerContractTest extends DebuggerContractTest {
    @Override
    protected Fixture createFixture() {
        ContextFactory factory = new ContextFactory();
        Debugger debugger = Debugger.of(factory);
        return new Fixture() {
            @Override
            public Debugger debugger() {
                return debugger;
            }

            @Override
            public Object run(String source, String name) {
                return factory.call(
                        cx -> cx.evaluateString(cx.initStandardObjects(), source, name, 1, null));
            }

            @Override
            public void close() {
                debugger.close();
            }
        };
    }
}
