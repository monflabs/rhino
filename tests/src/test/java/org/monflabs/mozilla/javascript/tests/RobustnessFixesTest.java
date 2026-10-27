/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

/** The bugs the robustness harnesses of JavascriptPerformance found, in both backends. */
@RhinoTest("testsrc/jstests/robustness/robustness-fixes.js")
@LanguageVersion(Context.VERSION_ECMASCRIPT)
public class RobustnessFixesTest extends ScriptTestsBase {}
