/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture tests.
 *
 * @author Ronald Brill
 */
@AnalyzeClasses(
        packages = "org.monflabs.mozilla.javascript",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /** Use only the RegExpProxy. */
    @ArchTest
    public static final ArchRule regExpProxyRule =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("org.monflabs.mozilla.javascript.regexp..")
                    .and()
                    .doNotHaveFullyQualifiedName("org.monflabs.mozilla.javascript.RegExpProxy")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.monflabs.mozilla.javascript.regexp..");
}
