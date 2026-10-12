#!/usr/bin/env python3
"""Reports Rhino's test262 status per feature tag and per directory.

Derived from test262 front matter and tests/testsrc/test262.properties, without
running the suite. A file is counted as: pass, fail (listed as failing), partial
(failing in strict or non-strict only), skipped (skipped folder, unsupported
feature or module flag) or out-of-scope (see MONFLABS_PLAN.md).

    python3 tests/test262-status.py [--by-feature] [--by-dir N]
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEST_DIR = os.path.join(ROOT, "tests/test262/test")
PROPS = os.path.join(ROOT, "tests/testsrc/test262.properties")
SUITE = os.path.join(
    ROOT, "tests/src/test/java/org/monflabs/mozilla/javascript/tests/Test262SuiteTest.java")

# Permanently out of scope for the ES2026 target (see MONFLABS_PLAN.md, "Scope").
OUT_OF_SCOPE_FEATURES = {
    "tail-call-optimization", "Temporal", "explicit-resource-management", "import-defer",
    "Atomics.pause", "ShadowRealm", "decorators", "joint-iteration", "immutable-arraybuffer",
    "source-phase-imports", "source-phase-imports-module-source", "await-dictionary",
    "import-bytes", "import-text", "Intl.Era-monthcode", "nonextensible-applies-to-private",
}
OUT_OF_SCOPE_DIRS = (
    "staging/", "intl402/", "built-ins/Temporal/", "built-ins/ShadowRealm/",
    "built-ins/DisposableStack/", "built-ins/AsyncDisposableStack/", "built-ins/SuppressedError/",
    "built-ins/AbstractModuleSource/", "language/statements/using/",
    "language/statements/await-using/",
)

STATES = ("pass", "fail", "partial", "skipped")


def unsupported_features():
    src = open(SUITE, encoding="utf-8").read()
    block = re.search(r"UNSUPPORTED_FEATURES\s*=.*?Arrays\.asList\((.*?)\)\);", src, re.S)
    return set(re.findall(r'"([^"]+)"', block.group(1))) if block else set()


def read_properties():
    skipped, fail_dirs, fail_files, partial = [], [], set(), set()
    top = None
    for line in open(PROPS, encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        if not line.startswith(" "):
            name = line.split()[0]
            if name.startswith("~"):
                skipped.append(name[1:] + "/")
                top = None
            else:
                top = name
            continue
        if top is None:
            continue
        entry = line.strip()
        path = entry.split()[0]
        rest = entry[len(path):]
        full = top + "/" + path
        if not path.endswith(".js"):
            fail_dirs.append(full + "/")
        elif re.search(r"\b(non-)?strict\b", rest) and "unsupported" not in rest:
            partial.add(full)
        else:
            fail_files.add(full)
    return tuple(skipped), tuple(fail_dirs), fail_files, partial


FRONT_MATTER = re.compile(r"/\*---(.*?)---\*/", re.S)


def metadata(text):
    m = FRONT_MATTER.search(text)
    if not m:
        return [], []
    yaml = m.group(1)
    feats = []
    f = re.search(r"^features:\s*\[(.*?)\]", yaml, re.M | re.S)
    if f:
        feats = [x.strip() for x in f.group(1).split(",") if x.strip()]
    else:
        f = re.search(r"^features:\s*\n((?:\s+-\s*.*\n)+)", yaml, re.M)
        if f:
            feats = [l.strip()[1:].strip() for l in f.group(1).splitlines() if l.strip()]
    flags = []
    f = re.search(r"^flags:\s*\[(.*?)\]", yaml, re.M)
    if f:
        flags = [x.strip() for x in f.group(1).split(",") if x.strip()]
    return feats, flags


def main(argv):
    by_feature = "--by-feature" in argv
    depth = int(argv[argv.index("--by-dir") + 1]) if "--by-dir" in argv else 0
    unsupported = unsupported_features()
    skipped, fail_dirs, fail_files, partial = read_properties()

    totals = [0, 0, 0, 0]
    out_of_scope = 0
    features = defaultdict(lambda: [0, 0, 0, 0])
    dirs = defaultdict(lambda: [0, 0, 0, 0])
    for dirpath, _, files in os.walk(TEST_DIR):
        rel_dir = os.path.relpath(dirpath, TEST_DIR)
        for name in files:
            if not name.endswith(".js") or "_FIXTURE" in name:
                continue
            rel = os.path.normpath(os.path.join(rel_dir, name))
            with open(os.path.join(dirpath, name), encoding="utf-8", errors="replace") as h:
                feats, flags = metadata(h.read())
            if rel.startswith(OUT_OF_SCOPE_DIRS) or OUT_OF_SCOPE_FEATURES.intersection(feats):
                out_of_scope += 1
                continue
            if rel.startswith(skipped) or unsupported.intersection(feats + flags):
                state = 3
            elif rel in fail_files or rel.startswith(fail_dirs):
                state = 1
            elif rel in partial:
                state = 2
            else:
                state = 0
            totals[state] += 1
            for feat in feats or ["<untagged>"]:
                features[feat][state] += 1
            if depth:
                dirs["/".join(rel.split("/")[:depth])][state] += 1

    def row(label, counts):
        total = sum(counts)
        cells = " ".join(f"{s}={c:6d}" for s, c in zip(STATES, counts))
        return f"{label:50s} total={total:6d} {cells} pass%={100.0 * counts[0] / total:5.1f}"

    if by_feature:
        for feat in sorted(features):
            print(row(feat, features[feat]))
    for d in sorted(dirs):
        print(row(d, dirs[d]))
    print(row("IN SCOPE", totals))
    print(f"out of scope: {out_of_scope}")


if __name__ == "__main__":
    main(sys.argv[1:])
