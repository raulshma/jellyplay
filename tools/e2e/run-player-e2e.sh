#!/usr/bin/env bash
# Run the on-device video-player E2E suite (app/src/androidTest .../e2e/player)
# against the Dockerized Jellyfin fixture on a connected physical device.
#
# Orchestration:
#   1. fixture up (bootstrap-jellyfin.sh — idempotent)
#   2. device prep: wake, keyguard, permissions, autofill off,
#      `pm clear` for a hermetic login (skip with KEEP_APP_STATE=1),
#      `adb reverse` so the device reaches the fixture on loopback
#   3. :app:connectedPhoneDebugAndroidTest (the suite)
#   4. pull failure artifacts (screenshots / view dumps) into .results/
#
# Usage:  tools/e2e/run-player-e2e.sh [gradle args...]
# Env:    ANDROID_SERIAL      device to target (default: first adb device)
#         KEEP_APP_STATE=1    skip `pm clear` (reuse an existing signed-in app)
#         E2E_CLASS           run a single test class, e.g.
#                             com.raulshma.jellyplay.e2e.player.TrickplayE2eTest
#         E2E_SERVER_URL      override the fixture base the TESTS use
#                             (default http://127.0.0.1:8096 via adb reverse)
#         SKIP_FIXTURE=1      assume the fixture is already up
# Requires: docker, curl, ffmpeg (fixture), adb + one arm64 device (API 28+).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULTS_DIR="$REPO_ROOT/tools/e2e/.results/player-e2e"
PKG="com.raulshma.jellyplay.dev"
PORT="${E2E_PORT:-8096}"

log() { printf '[player-e2e] %s\n' "$*"; }
fail() { printf 'VERDICT: FAIL (%s)\n' "$*" >&2; exit 1; }

command -v adb >/dev/null || { echo "FATAL: adb not on PATH"; exit 1; }

# ── device ────────────────────────────────────────────────────────────────
if [ -z "${ANDROID_SERIAL:-}" ]; then
  ANDROID_SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi
[ -n "$ANDROID_SERIAL" ] || fail "no adb device connected (adb devices shows none ready)"
export ANDROID_SERIAL
log "device: $ANDROID_SERIAL"

adb() { command adb -s "$ANDROID_SERIAL" "$@"; }

# ── fixture ───────────────────────────────────────────────────────────────
if [ "${SKIP_FIXTURE:-0}" != "1" ]; then
  log "bootstrapping the Jellyfin fixture (idempotent)"
  bash "$REPO_ROOT/tools/e2e/bootstrap-jellyfin.sh" || fail "fixture bootstrap failed (docker/ffmpeg available?)"
fi
curl -sf -m 5 "http://localhost:$PORT/System/Info/Public" >/dev/null || fail "fixture not reachable on :$PORT (and SKIP_FIXTURE=1)"

# ── device prep ───────────────────────────────────────────────────────────
log "device prep: wake, keyguard, permissions, autofill"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
# Pre-grant the runtime prompts (ignore failures: permission may not exist on
# this API level, or the prompt may be OEM-shaped).
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true
# Google autofill overwrites typed credentials on first run (device-pip-pass lesson).
adb shell settings put secure autofill_service null 2>/dev/null || true

if [ "${KEEP_APP_STATE:-0}" != "1" ]; then
  log "pm clear $PKG (hermetic run: fresh first-run login)"
  adb shell pm clear "$PKG" >/dev/null
fi

# The suite addresses the fixture as http://127.0.0.1:<port> (loopback).
log "adb reverse tcp:$PORT"
adb reverse "tcp:$PORT" "tcp:$PORT" || fail "adb reverse failed (unauthorized device?)"

# ── suite ─────────────────────────────────────────────────────────────────
RUNNER_ARGS=(-Pandroid.testInstrumentationRunnerArguments.serverUrl="${E2E_SERVER_URL:-http://127.0.0.1:$PORT}")
if [ -n "${E2E_CLASS:-}" ]; then
  RUNNER_ARGS+=(-Pandroid.testInstrumentationRunnerArguments.class="$E2E_CLASS")
fi

log "running :app:connectedPhoneDebugAndroidTest ${RUNNER_ARGS[*]} $*"
GRADLE_EXIT=0
(cd "$REPO_ROOT" && ./gradlew :app:connectedPhoneDebugAndroidTest "${RUNNER_ARGS[@]}" "$@") || GRADLE_EXIT=$?

# ── artifacts ─────────────────────────────────────────────────────────────
mkdir -p "$RESULTS_DIR"
log "pulling failure artifacts (if any) into $RESULTS_DIR"
adb pull "/sdcard/Android/data/$PKG/files/e2e-artifacts/." "$RESULTS_DIR/" >/dev/null 2>&1 || true
adb shell rm -rf "/sdcard/Android/data/$PKG/files/e2e-artifacts" >/dev/null 2>&1 || true

if [ "$GRADLE_EXIT" -eq 0 ]; then
  printf 'VERDICT: PASS (player E2E suite green on %s)\n' "$ANDROID_SERIAL"
else
  printf 'VERDICT: FAIL (connectedAndroidTest exited %s — artifacts in %s)\n' "$GRADLE_EXIT" "$RESULTS_DIR"
  exit "$GRADLE_EXIT"
fi
