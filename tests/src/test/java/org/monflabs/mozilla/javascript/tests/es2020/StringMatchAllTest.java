package org.monflabs.mozilla.javascript.tests.es2020;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es2020/string-match-all.js")
@LanguageVersion(Context.VERSION_ES6)
public class StringMatchAllTest extends ScriptTestsBase {}
