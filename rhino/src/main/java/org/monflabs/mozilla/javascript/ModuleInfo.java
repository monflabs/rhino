/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the module-goal parse of a source records: its import and export entries (ECMA-262 16.2.1.6
 * Source Text Module Records). The parser makes the statements of the module the body of a
 * generator function (see {@link ModuleRecord}).
 */
final class ModuleInfo {
    /** The binding name of the default export of an anonymous declaration or an expression. */
    static final String DEFAULT_BINDING = "$$module$default$$";

    /** The parameter of the generator function: the module record. */
    static final String RECORD_PARAM = "$$module$record$$";

    /** ImportName of a namespace import ({@code import * as ns}). */
    static final String NAMESPACE = "*namespace*";

    /** ImportName of {@code export * from "m"} (all names, not a binding). */
    static final String ALL_BUT_DEFAULT = "*all-but-default*";

    /**
     * A ModuleRequest Record (16.2.1.3.1): a specifier and the import attributes of its {@code
     * with} clause; the same specifier with other attributes is another module.
     */
    record ModuleRequest(String specifier, Map<String, String> attributes) {}

    record ImportEntry(ModuleRequest moduleRequest, String importName, String localName) {}

    /**
     * An export: local ({@code moduleRequest} null), indirect ({@code export {a} from "m"}), or
     * star ({@code export * from "m"}: {@code exportName} null).
     */
    record ExportEntry(
            String exportName, ModuleRequest moduleRequest, String importName, String localName) {}

    final Set<ModuleRequest> requestedModules = new LinkedHashSet<>();
    final List<ImportEntry> importEntries = new ArrayList<>();
    final List<ExportEntry> localExportEntries = new ArrayList<>();
    final List<ExportEntry> indirectExportEntries = new ArrayList<>();
    final List<ExportEntry> starExportEntries = new ArrayList<>();

    /** The export entries in source order, before they are sorted into the lists above. */
    final List<ExportEntry> exportEntries = new ArrayList<>();

    /** The function declared by {@code export default function () {}}, named "default". */
    boolean anonymousDefaultFunction;

    /** Whether the module awaits at its top level: its body is an async generator. */
    boolean hasTopLevelAwait;

    void addRequest(ModuleRequest request) {
        requestedModules.add(request);
    }

    /**
     * Sorts the export entries (16.2.1.6.1 ParseModule, steps 10-11): an export of an imported
     * binding is an indirect export.
     */
    void sortExportEntries() {
        Map<String, ImportEntry> importsByLocal = new LinkedHashMap<>();
        for (ImportEntry ie : importEntries) {
            importsByLocal.put(ie.localName(), ie);
        }
        for (ExportEntry ee : exportEntries) {
            if (ee.moduleRequest() == null) {
                ImportEntry ie = importsByLocal.get(ee.localName());
                if (ie == null) {
                    localExportEntries.add(ee);
                } else if (NAMESPACE.equals(ie.importName())) {
                    // a re-export of an imported namespace: like export * as name
                    indirectExportEntries.add(
                            new ExportEntry(ee.exportName(), ie.moduleRequest(), NAMESPACE, null));
                } else {
                    indirectExportEntries.add(
                            new ExportEntry(
                                    ee.exportName(), ie.moduleRequest(), ie.importName(), null));
                }
            } else if (ALL_BUT_DEFAULT.equals(ee.importName()) && ee.exportName() == null) {
                starExportEntries.add(ee);
            } else {
                indirectExportEntries.add(ee);
            }
        }
    }

    /**
     * The source of the generator function that the body of the module runs in, with its prologue:
     * it hands the getters of the exported local bindings to the record, then yields (the end of
     * InitializeEnvironment). The parser moves the statements of the module after the prologue.
     */
    String functionSource() {
        Set<String> locals = new LinkedHashSet<>();
        for (ExportEntry ee : localExportEntries) {
            locals.add(ee.localName());
        }
        StringBuilder sb = new StringBuilder();
        sb.append(hasTopLevelAwait ? "(async function* (" : "(function* (")
                .append(RECORD_PARAM)
                .append(") {'use strict'; ");
        sb.append(RECORD_PARAM).append(".init(");
        boolean first = true;
        for (String local : locals) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(local).append("\", () => ").append(local);
        }
        sb.append("); yield; })");
        return sb.toString();
    }
}
