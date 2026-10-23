#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#
# Generates the documentation site in rhino-doc/build/site: the docsify pages of rhino-doc/site
# and the browser playground (the rhino-playground jar, which CheerpJ runs in
# playground/index.html). The site is static: .github/workflows/doc.yml publishes it to GitHub
# Pages. With --serve, it is then served on http://localhost:<port>/ (default 8000): docsify needs
# an HTTP server, and CheerpJ needs Range requests, which this adds to Python's http.server.
#
# Usage: buildtools/build-doc.sh [--serve [port]]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SITE="$ROOT/rhino-doc/build/site"

"$ROOT/gradlew" -p "$ROOT" -q :rhino-doc:site
echo "Generated the documentation site in $SITE"

[ "${1:-}" = "--serve" ] || exit 0
PORT="${2:-8000}"
command -v python3 >/dev/null 2>&1 || { echo "error: python3 not found on PATH" >&2; exit 1; }
echo "Serving it at http://localhost:${PORT}/   (Ctrl-C to stop)"
exec python3 - "$PORT" "$SITE" <<'PY'
import functools, http.server, os, re, sys

class RangeHandler(http.server.SimpleHTTPRequestHandler):
    """http.server's handler, answering a single "bytes=a-b" Range with a 206."""

    _range = None

    def send_head(self):
        self._range = None
        m = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", "").strip())
        path = self.translate_path(self.path)
        if m is None or not os.path.isfile(path):
            return super().send_head()
        size = os.path.getsize(path)
        first, last = m.group(1), m.group(2)
        if first:
            start, end = int(first), int(last) if last else size - 1
        elif last:
            start, end = max(0, size - int(last)), size - 1
        else:
            return super().send_head()
        end = min(end, size - 1)
        if start > end:
            self.send_response(416)
            self.send_header("Content-Range", "bytes */%d" % size)
            self.end_headers()
            return None
        f = open(path, "rb")
        f.seek(start)
        self._range = end - start + 1
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        self.send_header("Content-Length", str(self._range))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()
        return f

    def copyfile(self, source, outputfile):
        if self._range is None:
            return super().copyfile(source, outputfile)
        outputfile.write(source.read(self._range))


port, root = int(sys.argv[1]), sys.argv[2]
handler = functools.partial(RangeHandler, directory=root)
with http.server.ThreadingHTTPServer(("", port), handler) as server:
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
PY
