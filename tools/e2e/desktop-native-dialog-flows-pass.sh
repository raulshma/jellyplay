#!/usr/bin/env bash
# tools/e2e/desktop-native-dialog-flows-pass.sh — e2e lane for the four
# native-dialog flows docs/e2e/desktop-native-dialogs.md left checklist-only:
# the editor image picker, the editor subtitle picker, the insights heatmap
# share and the player subtitle upload (DesktopFlowHarness inside the REAL
# windowed app; every row/sheet/button reached by REAL Robot mouse clicks
# through the harness-gated HarnessClickBridge — see the ledger).
#
# One run:
#   1. waits for the Jellyfin fixture (bootstrap-jellyfin.sh; Docker);
#   2. AuthenticatesByName + resolves the movie item (session-pass pattern);
#   3. prepares a space-free ASCII workspace with sample.png / sample.srt;
#   4. builds the packaged app image if missing
#      (./gradlew :apps:desktop:createDistributable);
#   5. REFUSES to start while any JellyPlay.exe already runs (shared-machine
#      safety — clicks/keystrokes must hit OUR instance only);
#   6. spawns JellyPlay.exe under JAVA_TOOL_OPTIONS carrying the
#      jellyplay.flowpass.* props (which also arm HarnessClickBridge in
#      Main.kt), an isolated -Djellyplay.perf.dataDir profile, a redirected
#      -Djava.io.tmpdir (flow 5 asserts the heatmap PNG there) and
#      -Djna.library.path=<repo>/tools/mpv for libmpv;
#   7. waits for <profile>/data/logs/flow-harness.json (deadline =
#      AUTO_EXIT_SECONDS + 300 s grace; PID-only taskkill fallback);
#   8. prints the report + evidence and exits 0 only when the report says
#      overallPass:true.
#
# Usage:  tools/e2e/desktop-native-dialog-flows-pass.sh
# Env overrides: SERVER_URL (http://localhost:8096), E2E_USERNAME (harness),
#                E2E_PASSWORD (harness-e2e-pass), ITEM_NAME ("Harness Test
#                Clip"), AUTO_EXIT_SECONDS (300).
#
# Requires Git Bash on Windows, an interactive session (Robot clicks + native
# dialogs need a real display), the bootstrap-jellyfin.sh fixture and libmpv
# at tools/mpv/libmpv-2.dll.

set -u

SERVER_URL="${SERVER_URL:-http://localhost:8096}"
# NOTE: USERNAME is the ambient Windows env var (the logged-in user); the
# fixture credential comes from E2E_USERNAME (session-pass lesson).
USERNAME="${E2E_USERNAME:-harness}"
PASSWORD="${E2E_PASSWORD:-harness-e2e-pass}"
ITEM_NAME="${ITEM_NAME:-Harness Test Clip}"
AUTO_EXIT_SECONDS="${AUTO_EXIT_SECONDS:-300}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_NAME="JellyPlay"
EXE="$REPO_ROOT/apps/desktop/build/compose/binaries/main/app/$APP_NAME/$APP_NAME.exe"
MPV_DIR="$REPO_ROOT/tools/mpv"

fail() { echo "ERROR: $*" >&2; exit 2; }

# ── 1. wait for the server ──────────────────────────────────────────────────
echo "== waiting for Jellyfin at $SERVER_URL …"
server_ok=0
for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$SERVER_URL/System/Info/Public" || true)"
    if [[ "$code" == "200" ]]; then server_ok=1; break; fi
    sleep 5
done
[[ "$server_ok" == "1" ]] || fail "server never became healthy at $SERVER_URL (run tools/e2e/bootstrap-jellyfin.sh first)"
echo "   server up."

# ── 2. authenticate + resolve the item ─────────────────────────────────────
EMBY_AUTH='MediaBrowser Client="e2e", Device="script", DeviceId="e2e", Version="1.0"'
auth_json="$(curl -s --max-time 15 -X POST "$SERVER_URL/Users/AuthenticateByName" \
    -H "Content-Type: application/json" \
    -H "X-Emby-Authorization: $EMBY_AUTH" \
    -d "{\"Username\":\"$USERNAME\",\"Pw\":\"$PASSWORD\"}")"
TOKEN="$(grep -oE '"AccessToken":"[0-9a-fA-F]+"' <<<"$auth_json" | head -1 | sed 's/.*:"//; s/"//')"
USER_ID="$(grep -oE '"User":\{[^}]*"Id":"[0-9a-f-]+"' <<<"$auth_json" | grep -oE '"Id":"[0-9a-f-]+"' | head -1 | sed 's/.*:"//; s/"//')"
[[ -n "$TOKEN" ]] || { echo "AUTH RESPONSE: $auth_json" >&2; fail "no AccessToken in AuthenticateByName response"; }
echo "   authenticated as $USERNAME."

SEARCH_ENC="${ITEM_NAME// /%20}"
items_json="$(curl -s --max-time 15 \
    "$SERVER_URL/Items?searchTerm=$SEARCH_ENC&Recursive=true&IncludeItemTypes=Movie&Limit=5" \
    -H "X-Emby-Token: $TOKEN")"
ITEM_ID="$(grep -oE '"Id":"[0-9a-fA-F-]+"' <<<"$items_json" | head -1 | sed 's/.*:"//; s/"//')"
[[ -n "$ITEM_ID" ]] || { echo "ITEMS RESPONSE: $items_json" >&2; fail "no Movie item matched searchTerm='$ITEM_NAME'"; }
echo "   item: '$ITEM_NAME' ($ITEM_ID)."

# Rerun hygiene: unplayed + no resume position (session-pass lesson).
curl -s --max-time 10 -X DELETE "$SERVER_URL/Users/$USER_ID/PlayedItems/$ITEM_ID" \
    -H "X-Emby-Token: $TOKEN" >/dev/null || true

# ── 3. workspace with the sample pick files ────────────────────────────────
STAMP="$(date +%Y%m%d-%H%M%S)"
PROFILE_NIX="$(mktemp -d -t jellyplay-flowpass-XXXXXX)"
WORKSPACE_NIX="$PROFILE_NIX/workspace"
mkdir -p "$WORKSPACE_NIX/tmp"
PROFILE_MIXED="$(cygpath -m "$PROFILE_NIX")"
WORKSPACE_MIXED="$(cygpath -m "$WORKSPACE_NIX")"
TMPDIR_MIXED="$WORKSPACE_MIXED/tmp"
case "$PROFILE_MIXED" in *" "*) fail "profile dir contains spaces ($PROFILE_MIXED); JAVA_TOOL_OPTIONS cannot carry it."; ;; esac

# A tiny but REAL PNG (flow 3 uploads it as the item's Primary image; the
# harness asserts the server re-tags the image, so content matters).
ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc2=duration=1:size=320x180:rate=1" -frames:v 1 \
    "$WORKSPACE_NIX/sample.png" || fail "could not generate sample.png (ffmpeg)"
# A minimal but valid .srt (flows 4 + 6 upload it as an external subtitle).
printf '1\n00:00:01,000 --> 00:00:03,000\nharness subtitle line\n\n' > "$WORKSPACE_NIX/sample.srt"
[[ -f "$WORKSPACE_NIX/sample.png" && -f "$WORKSPACE_NIX/sample.srt" ]] || fail "sample files missing after generation"

# Flow-3 rerun hygiene: the step asserts the item's Primary image CHANGES —
# if a previous run already uploaded this exact sample.png (Jellyfin re-tags
# only on content change), restore the fixture poster first. Base64 body per
# the 10.11 SetItemImage contract (see bootstrap-jellyfin.sh's measurement).
POSTER="$REPO_ROOT/tools/e2e/.state/poster.jpg"
SAMPLE_SIZE="$(wc -c < "$WORKSPACE_NIX/sample.png" | tr -d ' ')"
HAVE_SIZE="$(curl -sf -m 20 -H "X-Emby-Token: $TOKEN" -o /dev/null \
    -w '%{size_download}' "$SERVER_URL/Items/$ITEM_ID/Images/Primary" || echo 0)"
if [[ "$HAVE_SIZE" == "$SAMPLE_SIZE" && -f "$POSTER" ]]; then
    echo "   restoring fixture poster (Primary == sample.png from a prior run)"
    base64 -w0 "$POSTER" > "$POSTER.b64"
    curl -sf -m 60 -X POST -H "X-Emby-Token: $TOKEN" -H "Content-Type: image/jpeg" \
        --data-binary "@$POSTER.b64" "$SERVER_URL/Items/$ITEM_ID/Images/Primary" >/dev/null || true
    rm -f "$POSTER.b64"
fi

# ── 4. app image ───────────────────────────────────────────────────────────
# Always rebuild with caches disabled: a stale configuration-cache/classpath
# has already served pre-fix shared-module classes and produced a false
# green lane (run-5/6/7 lesson in docs/e2e/desktop-native-dialogs.md).
echo "== building app image fresh (--no-configuration-cache --no-build-cache)…"
(cd "$REPO_ROOT" && ./gradlew --no-configuration-cache --no-build-cache :apps:desktop:createDistributable) || fail "createDistributable failed"
[[ -f "$EXE" ]] || fail "app image not found at $EXE"
[[ -f "$MPV_DIR/libmpv-2.dll" ]] || fail "libmpv missing at $MPV_DIR/libmpv-2.dll (per-machine, gitignored)"

# ── 5. refuse a shared machine state ────────────────────────────────────────
procs_now="$(tasklist //FI "IMAGENAME eq $APP_NAME.exe" //FO CSV //NH 2>/dev/null | awk -F'","' '{gsub(/"/,"",$2); if ($2 ~ /^[0-9]+$/) print $2}' | tr '\n' ' ')"
[[ -z "$procs_now" ]] || fail "$APP_NAME.exe already running (PIDs: $procs_now) — close it first (clicks must hit only our instance)."

# ── 6. spawn with harness props ────────────────────────────────────────────
MPV_MIXED="$(cygpath -m "$MPV_DIR")"
LOG_OUT="$PROFILE_NIX/app.out"; LOG_ERR="$PROFILE_NIX/app.err"
# The JVM whitespace-splits JAVA_TOOL_OPTIONS, so space-bearing overrides
# would silently truncate the -D value. Fail fast instead (fixture creds
# follow bootstrap-jellyfin.sh and contain no spaces by default).
for __v in "$USERNAME" "$PASSWORD" "$SERVER_URL" "$ITEM_ID" "$MPV_MIXED" "$REPO_ROOT"; do
  case "$__v" in *" "*) echo "FATAL: a credential, the repo path or the libmpv path contains a space (JAVA_TOOL_OPTIONS would truncate it): '$__v'" >&2; exit 2;; esac
done
echo "== launching $APP_NAME (profile: $PROFILE_NIX)"
export JAVA_TOOL_OPTIONS="-Djellyplay.flowpass.enabled=true -Djellyplay.flowpass.workspace=$WORKSPACE_MIXED -Djellyplay.flowpass.serverUrl=$SERVER_URL -Djellyplay.flowpass.username=$USERNAME -Djellyplay.flowpass.password=$PASSWORD -Djellyplay.flowpass.itemId=$ITEM_ID -Djellyplay.flowpass.autoExitSeconds=$AUTO_EXIT_SECONDS -Djellyplay.flowpass.screenshotDir=$WORKSPACE_MIXED/shots -Djellyplay.perf.dataDir=$PROFILE_MIXED/profile -Djava.io.tmpdir=$TMPDIR_MIXED -Djna.library.path=$MPV_MIXED"
"$EXE" > "$LOG_OUT" 2> "$LOG_ERR" &
BASH_PID=$!
unset JAVA_TOOL_OPTIONS

# ── 7. wait for the report (PID-only kill fallback) ─────────────────────────
# NOTE (run-1 lesson): the report file is polled on a tight cadence and the
# app-PID sampling (tasklist, NOT powershell — a hung powershell call once
# stalled the whole loop past the deadline while the report sat on disk)
# only feeds the kill fallback. The grace window is generous because a
# freshly-built exe can sit in a Defender scan for minutes before the JVM
# even starts (measured run 1: ~4.5 min launch→main).
REPORT_NIX="$PROFILE_NIX/profile/data/logs/flow-harness.json"
deadline=$(( SECONDS + AUTO_EXIT_SECONDS + 300 ))
observed_pids=''
report_seen=0
last_sample=0
while (( SECONDS <= deadline )); do
    if [[ -f "$REPORT_NIX" ]]; then report_seen=1; break; fi
    if (( SECONDS - last_sample >= 10 )); then
        last_sample=$SECONDS
        snap="$(tasklist //FI "IMAGENAME eq $APP_NAME.exe" //FO CSV //NH 2>/dev/null | awk -F'","' '{gsub(/"/,"",$2); if ($2 ~ /^[0-9]+$/) print $2}')"
        while IFS= read -r pid_cur; do
            [[ -z "$pid_cur" ]] && continue
            case $'\n'"$observed_pids"$'\n' in *$'\n'"$pid_cur"$'\n'*) ;; *) observed_pids="$observed_pids$pid_cur"$'\n' ;; esac
        done <<< "$snap"
        if [[ -z "${snap//[[:space:]]/}" ]] && ! kill -0 "$BASH_PID" 2>/dev/null; then
            break # process gone and no report
        fi
    fi
    sleep 1
done

if [[ "$report_seen" != "1" ]]; then
    echo "[flowpass] TIMEOUT — no flow-harness.json after ${AUTO_EXIT_SECONDS}s + 300s grace." >&2
    echo "[flowpass] killing OUR observed PIDs only: $(echo "$observed_pids" | tr '\n' ' ')" >&2
    while IFS= read -r kp; do
        [[ -n "$kp" ]] && taskkill //PID "$kp" //F >/dev/null 2>&1
    done <<< "$observed_pids"
    echo "---- app stdout (last 40 lines) ----" >&2
    tail -40 "$LOG_OUT" >&2 2>/dev/null
    echo "---- app stderr (last 40 lines) ----" >&2
    tail -40 "$LOG_ERR" >&2 2>/dev/null
    fail "flows harness did not produce a report"
fi
wait "$BASH_PID" 2>/dev/null

# ── 8. report + verdict ─────────────────────────────────────────────────────
echo "== flow-harness.json"
cat "$REPORT_NIX"
echo
echo "== screenshots ($WORKSPACE_NIX/shots)"
ls -la "$WORKSPACE_NIX/shots" 2>/dev/null || echo "   (none)"
echo "== heatmap share output ($WORKSPACE_NIX/tmp)"
ls -la "$WORKSPACE_NIX/tmp" 2>/dev/null || echo "   (none)"
echo "== harness stdout (app log: $LOG_OUT)"
grep "JellyPlay.*flowpass" "$LOG_OUT" 2>/dev/null | tail -60 || true

if grep -q '"overallPass":true' "$REPORT_NIX"; then
    echo "== RESULT: OVERALL PASS"
    exit 0
fi
echo "== RESULT: FAIL (see steps above; full logs in $PROFILE_NIX)" >&2
exit 1
