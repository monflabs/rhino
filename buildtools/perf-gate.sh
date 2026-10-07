#!/bin/bash
#
# Compares the performance of the working tree against an earlier revision.
#
# Both revisions are measured on this machine, in this run, minutes apart: a
# benchmark number is only meaningful relative to another one taken on the same
# hardware, so there is deliberately no checked-in file of expected timings.
#
# The base revision supplies only the engine (the rhino jar); the working tree
# supplies the harness (benchmarks/src/perf) and the scripts, so the two sides
# differ only in the code under test.
#
#   buildtools/perf-gate.sh [base-ref]      default: the perf-baseline tag
#
# Environment:
#   PERF_ROUNDS     interleaved rounds, one JVM per side each (default 3)
#   PERF_SETTLE     seconds to let the machine settle after the builds (default 30)
#   PERF_JAVA_OPTS  extra JVM options for the harness, e.g. -Dperf.only=v8- or
#                   -Dperf.tolerance=0.05
#   JAVA_HOME       JDK used for the measurements (default: java on the PATH)
#
set -euo pipefail

BASE_REF="${1:-${PERF_BASE_REF:-perf-baseline}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
WORKTREE="$TMP/base"
OUT="$ROOT/benchmarks/build/perf"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if ! git -C "$ROOT" rev-parse --verify --quiet "$BASE_REF^{commit}" >/dev/null; then
    cat >&2 <<MSG
perf-gate: no such revision: $BASE_REF

The default base is the 'perf-baseline' tag, which marks the last revision
before the ES2026 work began. Or pass another revision:

    buildtools/perf-gate.sh HEAD~1
MSG
    exit 2
fi

# A Time Machine backup takes a full core for as long as it runs and turned every
# metric into a "regression" (as it did for Nashorn's gate). Refuse to measure
# during one; PERF_ALLOW_BACKUP=1 overrides.
if [ -z "${PERF_ALLOW_BACKUP:-}" ] && command -v tmutil >/dev/null \
        && tmutil status 2>/dev/null | grep -q "Running = 1"; then
    echo "perf-gate: a Time Machine backup is running; measurements would be meaningless." >&2
    echo "Wait for it to finish (tmutil status), or set PERF_ALLOW_BACKUP=1." >&2
    exit 3
fi

BASE_SHA="$(git -C "$ROOT" rev-parse --short "$BASE_REF^{commit}")"
echo "perf-gate: $BASE_REF ($BASE_SHA) -> working tree"

cleanup() {
    git -C "$ROOT" worktree remove --force "$WORKTREE" 2>/dev/null || true
    rm -rf "$TMP"
}
trap cleanup EXIT

engine_jar() {
    ls "$1"/rhino/build/libs/rhino-*.jar | grep -v -e '-sources' -e '-javadoc' | head -1
}

git -C "$ROOT" worktree add --detach --quiet "$WORKTREE" "$BASE_REF"

echo "perf-gate: building base engine"
(cd "$WORKTREE" && ./gradlew -q :rhino:jar)
cp "$(engine_jar "$WORKTREE")" "$TMP/rhino-base.jar"

echo "perf-gate: building working tree"
(cd "$ROOT" && ./gradlew -q :rhino:jar :benchmarks:perfClasses)
cp "$(engine_jar "$ROOT")" "$TMP/rhino-head.jar"

HARNESS="$ROOT/benchmarks/build/classes/java/perf"
MAIN=org.monflabs.mozilla.javascript.perf.PerfBenchmark
ROUNDS="${PERF_ROUNDS:-3}"
# A pinned heap and collector: left to ergonomics, G1 sizes itself differently
# in each JVM and allocation-heavy benchmarks move by double digits on that alone.
JVM=(-Xms1g -Xmx1g -XX:+UseG1GC ${PERF_JAVA_OPTS:-})

# Let the machine settle after the builds: measuring straight away catches it
# hot and busy, which skews whichever side goes first.
sleep "${PERF_SETTLE:-30}"

# Interleave the two sides rather than measuring one and then the other, so both
# see the same conditions, and take the median across rounds, so that a single
# disturbed round cannot decide the outcome.
mkdir -p "$OUT"
rm -f "$OUT"/perf-*.json
cd "$ROOT/benchmarks"
for round in $(seq 1 "$ROUNDS"); do
    echo "perf-gate: round $round/$ROUNDS"
    "$JAVA" "${JVM[@]}" -cp "$HARNESS:$TMP/rhino-base.jar" $MAIN record "$OUT/perf-base-$round.json" >/dev/null
    "$JAVA" "${JVM[@]}" -cp "$HARNESS:$TMP/rhino-head.jar" $MAIN record "$OUT/perf-head-$round.json" >/dev/null
done

"$JAVA" -cp "$HARNESS:$TMP/rhino-head.jar" $MAIN merge "$OUT/perf-base.json" "$OUT"/perf-base-*.json
"$JAVA" -cp "$HARNESS:$TMP/rhino-head.jar" $MAIN merge "$OUT/perf-head.json" "$OUT"/perf-head-*.json

echo
"$JAVA" "${JVM[@]}" -cp "$HARNESS:$TMP/rhino-head.jar" $MAIN compare "$OUT/perf-base.json" "$OUT/perf-head.json"
