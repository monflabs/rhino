package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest(value = "testsrc/jstests/top-level-strict-mode.js")
public class TopLevelStrictModeTest extends ScriptTestsBase {}
