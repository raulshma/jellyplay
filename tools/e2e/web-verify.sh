#!/usr/bin/env bash
# Web E2E verification lane (wave 13C): builds the webpack bundle, then drives
# headless Edge through the canvas app against a real Jellyfin server via CDP
# (see tools/e2e/web-verify.mjs). Evidence (screenshot + result.json) lands in
# a TEMP dir that is intentionally never committed.
#
# Env (JP_-prefixed: USERNAME/PASSWORD collide with standard Windows env
# vars — Git Bash exports USERNAME=<account>, which silently overrode the
# harness default in the first wrapper runs):
#   JP_SERVER_URL  (default http://localhost:8096)
#   JP_USERNAME    (default harness)
#   JP_PASSWORD    (default harness-e2e-pass)
#   OUT_DIR        (default: driver picks a fresh dir under $TMPDIR)
set -euo pipefail

# Script lives in tools/e2e/ — gradlew and the node paths below are
# repo-root-relative.
cd "$(dirname "$0")/../.."

SERVER_URL="${JP_SERVER_URL:-http://localhost:8096}"
USERNAME="${JP_USERNAME:-harness}"
PASSWORD="${JP_PASSWORD:-harness-e2e-pass}"

echo "== polling ${SERVER_URL}/System/Info/Public"
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${SERVER_URL}/System/Info/Public" || true)
  [ "$code" = "200" ] && break
  sleep 2
done
if [ "${code:-}" != "200" ]; then
  echo "VERDICT: FAIL (server never came up)"; exit 1
fi
echo "== server up (HTTP ${code})"

echo "== :apps:web:wasmJsBrowserDevelopmentWebpack"
./gradlew :apps:web:wasmJsBrowserDevelopmentWebpack --console=plain

echo "== node tools/e2e/web-verify.mjs"
ARGS=(--server-url "$SERVER_URL" --username "$USERNAME" --password "$PASSWORD")
if [ -n "${OUT_DIR:-}" ]; then ARGS+=(--out-dir "$OUT_DIR"); fi
if node tools/e2e/web-verify.mjs "${ARGS[@]}"; then
  echo "VERDICT: PASS"
else
  echo "VERDICT: FAIL"
  exit 1
fi
