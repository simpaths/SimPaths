#!/usr/bin/env bash
# Check the current checkout with the same build and browser suites used by CI.
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
cd "$repo"
source_branch=${1:-develop}
case "$source_branch" in main|develop) ;; *) echo 'Usage: bash documentation/scripts/preflight-docs.sh [main|develop]' >&2; exit 2 ;; esac
for tool in python3 mkdocs mvn node npm; do
  command -v "$tool" >/dev/null || { echo "Missing prerequisite: $tool" >&2; exit 1; }
done

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/simpaths-docs-preflight.XXXXXX")
server_pid=
cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

python3 -m unittest discover -s .github/scripts -p 'test_*.py'
bash documentation/scripts/build-version.sh "$source_branch" "$work_dir/site"
python3 documentation/scripts/check-published-site.py "$work_dir/site"

cd documentation/visual-tests
npm ci
# The pinned Playwright release installs its matching browser once, then reuses it.
node node_modules/@playwright/test/cli.js install chromium

# Bind a fresh, OS-assigned port. Never reuse a potentially stale preview server.
python3 - "$work_dir/site" "$work_dir/port" >"$work_dir/server.log" 2>&1 <<'PY' &
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys

handler = partial(SimpleHTTPRequestHandler, directory=sys.argv[1])
with ThreadingHTTPServer(('127.0.0.1', 0), handler) as server:
    Path(sys.argv[2]).write_text(str(server.server_port))
    server.serve_forever()
PY
server_pid=$!
for ((attempt = 0; attempt < 100; attempt++)); do
  [[ -s "$work_dir/port" ]] && break
  if ! kill -0 "$server_pid" 2>/dev/null; then
    cat "$work_dir/server.log" >&2
    exit 1
  fi
  sleep 0.1
done
[[ -s "$work_dir/port" ]] || { echo 'Preview server did not start.' >&2; exit 1; }
export SIMPATHS_DOCS_URL="http://127.0.0.1:$(cat "$work_dir/port")"
node node_modules/@playwright/test/cli.js test --config playwright.config.mjs
node node_modules/@playwright/test/cli.js test --config publication.config.mjs
echo 'Documentation preflight passed: build, links, API, desktop/mobile layouts and publication checks.'
