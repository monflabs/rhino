/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SampleLibraryTest {

    @Test
    void theBundledLibraryHasItsCategories() throws IOException {
        SampleLibrary library = SampleLibrary.load();
        List<String> categories =
                library.root().children().stream().map(SampleLibrary.Node::name).toList();
        assertEquals(
                List.of("Getting started", "ECMAScript support", "Rhino extensions"), categories);
        for (Sample sample : library.samples()) {
            assertNotNull(sample.readme(), sample.id() + " has no README");
            assertFalse(sample.title().isBlank(), sample.id());
        }
    }

    @Test
    void foldersAreOrderedByNumberAndShownWithout(@TempDir Path dir) throws IOException {
        write(dir.resolve("02 - B/01 - Second/main.js"), "print(2)");
        write(dir.resolve("01 - A/01 - First/main.js"), "print(1)");
        write(dir.resolve("01 - A/01 - First/README.md"), "\n# The first\n\ntext");
        write(dir.resolve("01 - A/01 - First/data.json"), "{}");
        write(dir.resolve("_hidden/01 - X/main.js"), "print(0)");
        SampleLibrary library = SampleLibrary.load(dir);
        assertEquals(
                List.of("A", "B"),
                library.root().children().stream().map(SampleLibrary.Node::name).toList());
        Sample first = library.byId("01 - A/01 - First");
        assertEquals("The first", first.title());
        assertEquals(List.of("A"), first.categories());
        assertEquals(List.of("data.json"), List.copyOf(first.files().keySet()));
        assertEquals("01 - First/main.js", first.fileName());
        assertEquals("Second", library.byId("02 - B/01 - Second").title());
        assertTrue(library.samples().size() == 2);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
