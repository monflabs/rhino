/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The sample library, read from the {@code samples} resource tree (out of the jar when packaged,
 * off the directory when run from the build) with no manifest to keep in step: a folder holding a
 * {@code main.js} is a sample, any other folder is a category, and folder names carry a number for
 * the order and lose it for display.
 */
public final class SampleLibrary {
    private static final Pattern NUMBERED = Pattern.compile("^\\d+\\s*-\\s*");
    private static final Pattern HEADING = Pattern.compile("^#+\\s*(.+?)\\s*$");

    /**
     * A node of the library tree: a category with children, or a sample.
     *
     * @param name the display name
     * @param sample the sample, or null for a category
     * @param children the children, in order; empty for a sample
     */
    public record Node(String name, Sample sample, List<Node> children) {
        /** Whether this is a sample rather than a category. */
        public boolean isSample() {
            return sample != null;
        }
    }

    private final Node root;
    private final List<Sample> samples;

    private SampleLibrary(Node root, List<Sample> samples) {
        this.root = root;
        this.samples = Collections.unmodifiableList(samples);
    }

    /** Loads the library bundled with the application. */
    public static SampleLibrary load() throws IOException {
        URL url = SampleLibrary.class.getResource("/samples");
        if (url == null) {
            throw new IOException("no samples resource");
        }
        try {
            return load(resourcePath(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /** Loads a library from a directory tree. */
    public static SampleLibrary load(Path root) throws IOException {
        List<Sample> samples = new ArrayList<>();
        Node tree = read(root, root, List.of(), samples);
        return new SampleLibrary(tree, samples);
    }

    private static Path resourcePath(URI uri) throws IOException {
        if ("jar".equals(uri.getScheme())) {
            try {
                FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException e) {
                // opened earlier
            }
        }
        return Paths.get(uri);
    }

    private static Node read(Path root, Path dir, List<String> categories, List<Sample> samples)
            throws IOException {
        String name = dir.equals(root) ? "Samples" : displayName(fileName(dir));
        Path main = dir.resolve("main.js");
        if (Files.isRegularFile(main)) {
            Sample sample = readSample(root, dir, categories, main);
            samples.add(sample);
            return new Node(sample.title(), sample, List.of());
        }
        List<Node> children = new ArrayList<>();
        List<String> nested = new ArrayList<>(categories);
        if (!dir.equals(root)) {
            nested.add(name);
        }
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> dirs =
                    entries.filter(Files::isDirectory)
                            .filter(p -> !fileName(p).startsWith("_"))
                            .sorted((a, b) -> fileName(a).compareToIgnoreCase(fileName(b)))
                            .toList();
            for (Path child : dirs) {
                children.add(read(root, child, nested, samples));
            }
        }
        return new Node(name, null, Collections.unmodifiableList(children));
    }

    /** The last name of a path, without the trailing separator of a directory in a jar. */
    private static String fileName(Path p) {
        String name = p.getFileName().toString();
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static Sample readSample(Path root, Path dir, List<String> categories, Path main)
            throws IOException {
        String source = Files.readString(main, StandardCharsets.UTF_8);
        String readme = null;
        Map<String, String> others = new LinkedHashMap<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path p : entries.filter(Files::isRegularFile).sorted().toList()) {
                String fileName = fileName(p);
                if (fileName.equals("main.js")) {
                    continue;
                }
                if (fileName.equalsIgnoreCase("README.md")) {
                    readme = Files.readString(p, StandardCharsets.UTF_8);
                } else if (!fileName.startsWith(".")) {
                    others.put(fileName, Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        }
        String id = root.relativize(dir).toString();
        String separator = dir.getFileSystem().getSeparator();
        if (!separator.equals("/")) {
            id = id.replace(separator, "/");
        }
        if (id.endsWith("/")) {
            id = id.substring(0, id.length() - 1);
        }
        String folderName = displayName(fileName(dir));
        String title = readme == null ? folderName : titleOf(readme, folderName);
        return new Sample(
                id,
                List.copyOf(categories),
                title,
                source,
                readme,
                Collections.unmodifiableMap(others));
    }

    /** The folder name without its ordering number. */
    static String displayName(String folderName) {
        return NUMBERED.matcher(folderName).replaceFirst("");
    }

    /** The README's first heading, or the fallback. */
    static String titleOf(String readme, String fallback) {
        for (String line : readme.split("\\R")) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
            if (!line.isBlank()) {
                break;
            }
        }
        return fallback;
    }

    /** The tree. */
    public Node root() {
        return root;
    }

    /** Every sample, in tree order. */
    public List<Sample> samples() {
        return samples;
    }

    /** The sample with an id, or null. */
    public Sample byId(String id) {
        for (Sample s : samples) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }
}
