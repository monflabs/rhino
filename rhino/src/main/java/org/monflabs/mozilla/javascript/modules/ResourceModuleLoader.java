/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Loads modules from class-path resources under a root path. */
public final class ResourceModuleLoader implements ModuleLoader {
    private final ClassLoader loader;
    private final String root; // no leading or trailing slash; possibly empty

    public ResourceModuleLoader(Class<?> anchor, String root) {
        this(Objects.requireNonNull(anchor, "anchor").getClassLoader(), root);
    }

    public ResourceModuleLoader(ClassLoader loader, String root) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.root = normalize(root == null ? "" : root);
    }

    private static String normalize(String path) {
        StringBuilder sb = new StringBuilder(path);
        while (sb.length() > 0 && sb.charAt(0) == '/') {
            sb.deleteCharAt(0);
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    @Override
    public Module load(String specifier, Module referrer) {
        String resolved;
        if (specifier.startsWith("./") || specifier.startsWith("../")) {
            String base;
            if (referrer == null) {
                base = root;
            } else if (referrer.origin() instanceof ResourcePath importer
                    && importer.loader == loader) {
                int slash = importer.path.lastIndexOf('/');
                base = slash < 0 ? "" : importer.path.substring(0, slash);
            } else {
                return null; // another loader's module: not ours to resolve
            }
            resolved = join(base, specifier);
        } else {
            resolved = join(root, normalize(specifier));
        }
        if (resolved == null
                || !(root.isEmpty() || resolved.equals(root) || resolved.startsWith(root + "/"))) {
            return null; // escaped above the root
        }
        String text = read(resolved);
        return text == null
                ? null
                : Module.source("classpath:/" + resolved, text, new ResourcePath(loader, resolved));
    }

    private static String join(String base, String relative) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : (base.isEmpty() ? relative : base + "/" + relative).split("/")) {
            switch (part) {
                case "":
                case ".":
                    break;
                case "..":
                    if (parts.isEmpty()) {
                        return null;
                    }
                    parts.removeLast();
                    break;
                default:
                    parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }

    private String read(String path) {
        try (InputStream in = loader.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read module resource " + path, e);
        }
    }

    private record ResourcePath(ClassLoader loader, String path) {}

    @Override
    public String toString() {
        return "ResourceModuleLoader(/" + root + ")";
    }
}
