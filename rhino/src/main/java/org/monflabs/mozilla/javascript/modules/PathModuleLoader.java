/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.modules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Loads modules from files under a root directory. A relative specifier is resolved against the
 * importing module's directory, any other against the root; the specifier names the file, extension
 * included.
 */
public final class PathModuleLoader implements ModuleLoader {
    private final Path root;

    public PathModuleLoader(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Module load(String specifier, Module referrer) {
        Path candidate;
        try {
            Path path = Path.of(specifier);
            if (path.isAbsolute()) {
                candidate = path.normalize();
            } else if (specifier.startsWith("./") || specifier.startsWith("../")) {
                if (referrer == null) {
                    candidate = root.resolve(specifier).normalize();
                } else if (referrer.origin() instanceof Path importer) {
                    candidate = importer.getParent().resolve(specifier).normalize();
                } else {
                    return null; // another loader's module: not ours to resolve
                }
            } else {
                candidate = root.resolve(specifier).normalize();
            }
        } catch (InvalidPathException notAPath) {
            return null;
        }
        if (!Files.isReadable(candidate)) {
            return null;
        }
        try {
            return Module.source(candidate.toString(), Files.readString(candidate), candidate);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read module " + candidate, e);
        }
    }

    @Override
    public String toString() {
        return "PathModuleLoader(" + root + ")";
    }
}
