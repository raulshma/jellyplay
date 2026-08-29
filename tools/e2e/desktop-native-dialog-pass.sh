#!/usr/bin/env bash
# tools/e2e/desktop-native-dialog-pass.sh — wave 22F audit-F9 native-dialog
# pass. Verifies the desktop AWT FileDialog flows INSIDE the real windowed
# app with real native dialogs driven by java.awt.Robot (the wave-20
# "manually-verified-only" cut, see DesktopNativeDialogHarness + docs/e2e/
# desktop-native-dialogs.md). One run:
#
#   1. builds the packaged app image if missing
#      (./gradlew :apps:desktop:createDistributable);
#   2. REFUSES to start while any JellyPlay.exe already runs (shared-machine
#      safety — keystrokes must hit OUR instance only);
#   3. spawns JellyPlay.exe under JAVA_TOOL_OPTIONS carrying the
#      jellyplay.dialogpass.* props, an isolated -Djellyplay.perf.dataDir
#      profile and a fresh space-free workspace dir (NO server fixture, NO
#      libmpv — the settings backup round trip is local-prefs-only);
#   4. inside the app, the harness shows the REAL modal AWT FileDialogs
#      (SAVE with the production jellyplay-settings.json prefill, LOAD,
#      LOAD-again-for-cancel), types the full absolute path into the filename
#      box + Enter (ESC for the cancel leg), and asserts the observable
#      app-side effects: the VM's export/import status lines and the exported
#      file's existence + v2 JSON shape on disk;
#   5. waits for <profile>/data/logs/dialog-harness.json (deadline =
#      AUTO_EXIT_SECONDS + 120 s grace; PID-only taskkill fallback — kills
#      only JellyPlay PIDs observed in OUR window, never by window title);
#   6. prints the report + dialog screenshots and exits 0 only when the
#      report says overallPass:true.
#
# Usage:  tools/e2e/desktop-native-dialog-pass.sh
# Env overrides: AUTO_EXIT_SECONDS (90).
#
# Requires Git Bash on Windows: cygpath + powershell + taskkill. The Robot
# needs a real interactive session (no locked screen). Server-free by design:
# the Jellyfin fixture is NOT required for this pass.

set -u

AUTO_EXIT_SECONDS="${AUTO_EXIT_SECONDS:-90}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_NAME="JellyPlay"
EXE="$REPO_ROOT/apps/desktop/build/compose/binaries/main/app/$APP_NAME/$APP_NAME.exe"

fail() { echo "ERROR: $*" >&2; exit 2; }

# ── 1. app image ────────────────────────────────────────────────────────────
if [[ ! -f "$EXE" ]]; then
    echo "== app image missing — building (:apps:desktop:createDistributable)…"
    (cd "$REPO_ROOT" && ./gradlew :apps:desktop:createDistributable) || fail "createDistributable failed"
fi
[[ -f "$EXE" ]] || fail "app image not found at $EXE"

# ── 2. refuse a shared machine state ────────────────────────────────────────
procs_now="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r' | xargs)"
[[ -z "$procs_now" ]] || fail "$APP_NAME.exe already running (PIDs: $procs_now) — close it first (typed keys must hit only our instance)."

# ── 3. spawn with harness props ─────────────────────────────────────────────
STAMP="$(date +%Y%m%d-%H%M%S)"
PROFILE_NIX="$(mktemp -d -t jellyplay-dialogpass-XXXXXX)"
mkdir -p "$PROFILE_NIX/workspace"
PROFILE_MIXED="$(cygpath -m "$PROFILE_NIX")"
WORKSPACE_MIXED="$PROFILE_MIXED/workspace"
case "$PROFILE_MIXED" in *" "*) fail "profile dir contains spaces ($PROFILE_MIXED); JAVA_TOOL_OPTIONS cannot carry it."; ;; esac
# JVM splits JAVA_TOOL_OPTIONS on whitespace: any space-bearing path would
# truncate this -D value (and every prop after it).

LOG_OUT="$PROFILE_NIX/app.out"; LOG_ERR="$PROFILE_NIX/app.err"
echo "== launching $APP_NAME (profile: $PROFILE_NIX)"
export JAVA_TOOL_OPTIONS="-Djellyplay.dialogpass.enabled=true -Djellyplay.dialogpass.workspace=$WORKSPACE_MIXED -Djellyplay.dialogpass.autoExitSeconds=$AUTO_EXIT_SECONDS -Djellyplay.perf.dataDir=$PROFILE_MIXED/profile"
"$EXE" > "$LOG_OUT" 2> "$LOG_ERR" &
BASH_PID=$!
unset JAVA_TOOL_OPTIONS

# ── 4. wait for the report (PID-only kill fallback) ─────────────────────────
REPORT_NIX="$PROFILE_NIX/profile/data/logs/dialog-harness.json"
deadline=$(( SECONDS + AUTO_EXIT_SECONDS + 120 ))
observed_pids=''
report_seen=0
while (( SECONDS <= deadline )); do
    if [[ -f "$REPORT_NIX" ]]; then report_seen=1; break; fi
    # Sample live JellyPlay PIDs (our own window only) for the kill fallback.
    # Note: this window is not hermetic — an instance a USER launches mid-run
    # would also be sampled and thus killed on timeout. The pre-launch
    # refuse-guard above covers the common case.
    snap="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r')"
    while IFS= read -r pid_cur; do
        [[ -z "$pid_cur" ]] && continue
        # newline-delimited exact-field compare (a substring match would drop
        # PID 23 just because 123 was already recorded)
        case $'\n'"$observed_pids"$'\n' in *$'\n'"$pid_cur"$'\n'*) ;; *) observed_pids="$observed_pids$pid_cur"$'\n' ;; esac
    done <<< "$snap"
    if [[ -z "${snap//[[:space:]]/}" ]] && ! kill -0 "$BASH_PID" 2>/dev/null; then
        break # process gone and no report
    fi
    sleep 2
done

if [[ "$report_seen" != "1" ]]; then
    echo "[dialogpass] TIMEOUT — no dialog-harness.json after ${AUTO_EXIT_SECONDS}s + 120s grace." >&2
    echo "[dialogpass] killing OUR observed PIDs only: $(echo "$observed_pids" | tr '\n' ' ')" >&2
    while IFS= read -r kp; do
        [[ -n "$kp" ]] && taskkill //PID "$kp" //F >/dev/null 2>&1
    done <<< "$observed_pids"
    echo "---- app stdout (last 40 lines) ----" >&2
    tail -40 "$LOG_OUT" >&2 2>/dev/null
    echo "---- app stderr (last 40 lines) ----" >&2
    tail -40 "$LOG_ERR" >&2 2>/dev/null
    fail "dialog harness did not produce a report"
fi
wait "$BASH_PID" 2>/dev/null

# ── 5. report + verdict ─────────────────────────────────────────────────────
echo "== dialog-harness.json"
cat "$REPORT_NIX"
echo
echo "== dialog screenshots ($WORKSPACE_MIXED/shots)"
ls -la "$PROFILE_NIX/workspace/shots" 2>/dev/null || echo "   (none)"
echo "== exported round-trip file ($WORKSPACE_MIXED/jellyplay-settings.json, head)"
head -c 300 "$PROFILE_NIX/workspace/jellyplay-settings.json" 2>/dev/null; echo
echo "== harness stdout (app log: $LOG_OUT)"
grep "JellyPlay.*dialogpass" "$LOG_OUT" 2>/dev/null || true

if grep -q '"overallPass":true' "$REPORT_NIX"; then
    echo "== RESULT: OVERALL PASS"
    exit 0
fi
echo "== RESULT: FAIL (see steps above; full logs in $PROFILE_NIX)" >&2
exit 1
