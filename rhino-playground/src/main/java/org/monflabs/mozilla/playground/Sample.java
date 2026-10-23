/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import java.util.List;
import java.util.Map;

/**
 * A sample of the library: a folder with a {@code main.js}, an optional README and optional sibling
 * files.
 *
 * @param id the folder path within the library, the sample's identity
 * @param categories the display names of the enclosing folders, outermost first
 * @param title the display name: the README's first heading, else the folder name without its
 *     number
 * @param source the text of main.js
 * @param readme the README text, or null
 * @param files the other files of the folder, by name
 */
public record Sample(
        String id,
        List<String> categories,
        String title,
        String source,
        String readme,
        Map<String, String> files) {

    /** The name the script runs under. */
    public String fileName() {
        return id.substring(id.lastIndexOf('/') + 1) + "/main.js";
    }

    /** The same sample with another script. */
    public Sample withSource(String text) {
        return new Sample(id, categories, title, text, readme, files);
    }
}
