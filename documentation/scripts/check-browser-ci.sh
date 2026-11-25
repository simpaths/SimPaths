#!/usr/bin/env bash
# Run the locked Playwright version with its preinstalled Linux dependencies.
set -euo pipefail
site_dir=${1:?Built site directory required}
config=${2:?Playwright configuration required}
version=$(node -p "require('./package.json').devDependencies['@playwright/test']")
python3 -m http.server 8316 --bind 127.0.0.1 --directory "$site_dir" &
server_pid=$!
trap 'kill "$server_pid"' EXIT
# Host networking lets the browser reach the temporary server without copying
# the built site or installing Python and Java inside the browser image.
docker run --rm --network host --ipc=host \
  -v "$GITHUB_WORKSPACE:$GITHUB_WORKSPACE" -w "$PWD" \
  -e CI=true -e SIMPATHS_DOCS_URL=http://127.0.0.1:8316 \
  "mcr.microsoft.com/playwright:v${version}-noble" \
  npx playwright test --config "$config"
