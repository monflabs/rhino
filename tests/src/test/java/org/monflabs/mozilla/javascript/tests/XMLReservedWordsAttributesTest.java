package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/xml-reserved-words-as-attributes.js")
@LanguageVersion(Context.VERSION_ES6)
public class XMLReservedWordsAttributesTest extends ScriptTestsBase {}
