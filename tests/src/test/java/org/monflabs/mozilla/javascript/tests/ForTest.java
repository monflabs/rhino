/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class ForTest {

    /** Test for issue #645. There was a NPE. */
    @Test
    public void forInit() {
        Utils.assertWithAllModes(Undefined.instance, "for(({});;){ break; }");
    }
}
