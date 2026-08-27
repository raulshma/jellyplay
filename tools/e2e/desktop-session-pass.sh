#!/usr/bin/env bash
# tools/e2e/desktop-session-pass.sh — wave 13B real-server desktop session
# pass. Verifies IN-APP video playback against a real Jellyfin (the whole
# shared pipeline: VideoPlayerScreen → VideoPlayerViewModel →
# DesktopMpvPlayerEngineFactory → MpvDesktopEngine) plus the Esc-popup
# ordering question wave 9 left open, inside the REAL windowed app.
#
# What one run does:
#   1. waits for the Jellyfin server (GET /System/Info/Public → 200);
#   2. AuthenticatesByName (X-Emby-Authorization header) → access token;
#   3. resolves the movie item id via /Items?searchTerm=<name>&Recursive=true
#      &IncludeItemTypes=Movie;
#   4. builds the packaged app image if missing
#      (./gradlew :apps:desktop:createDistributable);
#   5. REFUSES to start while any JellyPlay.exe already runs (shared-machine
#      safety — screenshots/keystrokes must hit OUR instance only);
#   6. spawns JellyPlay.exe directly under bash with JAVA_TOOL_OPTIONS
#      carrying the jellyplay.harness.* props (see DesktopSessionHarness),
#      an isolated -Djellyplay.perf.dataDir temp profile, and
#      -Djna.library.path=<repo>/tools/mpv for libmpv;
#   7. waits for <profile>/data/logs/session-harness.json (deadline =
#      AUTO_EXIT_SECONDS + 120 s grace; PID-only taskkill fallback — kills
#      only JellyPlay PIDs observed in OUR window, never by window title);
#   8. prints the report, the screenshot dir and exits 0 only when the
#      report says overallPass:true.
#
# Usage:  tools/e2e/desktop-session-pass.sh
# Env overrides: SERVER_URL (http://localhost:8096), E2E_USERNAME (harness),
#                E2E_PASSWORD (harness-e2e-pass), ITEM_NAME ("Harness Test
#                Clip"), AUTO_EXIT_SECONDS (150). (USERNAME is deliberately
#                NOT read — on Windows it is the logged-in user's env var.)
#
# Requires Git Bash on Windows: cygpath + powershell + taskkill. No jq needed.
# The windowed app takes real screenshots via java.awt.Robot — the session
# must be interactive (no locked screen) for the screenshot steps to pass.

set -u

SERVER_URL="${SERVER_URL:-http://localhost:8096}"
# NOTE: USERNAME is an ambient Windows env var (the logged-in user) and would
# silently shadow a ${USERNAME:-…} default — the harness user comes from
# E2E_USERNAME instead (or the hardcoded default below).
USERNAME="${E2E_USERNAME:-harness}"
PASSWORD="${E2E_PASSWORD:-harness-e2e-pass}"
ITEM_NAME="${ITEM_NAME:-Harness Test Clip}"
AUTO_EXIT_SECONDS="${AUTO_EXIT_SECONDS:-150}"

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
[[ "$server_ok" == "1" ]] || fail "server never became healthy at $SERVER_URL"
echo "   server up."

# ── 2. authenticate ────────────────────────────────────────────────────────
EMBY_AUTH='MediaBrowser Client="e2e", Device="script", DeviceId="e2e", Version="1.0"'
auth_json="$(curl -s --max-time 15 -X POST "$SERVER_URL/Users/AuthenticateByName" \
    -H "Content-Type: application/json" \
    -H "X-Emby-Authorization: $EMBY_AUTH" \
    -d "{\"Username\":\"$USERNAME\",\"Pw\":\"$PASSWORD\"}")"
TOKEN="$(grep -oE '"AccessToken":"[0-9a-fA-F]+"' <<<"$auth_json" | head -1 | sed 's/.*:"//; s/"//')"
USER_ID="$(grep -oE '"User":\{[^}]*"Id":"[0-9a-f-]+"' <<<"$auth_json" | grep -oE '"Id":"[0-9a-f-]+"' | head -1 | sed 's/.*:"//; s/"//')"
[[ -n "$TOKEN" ]] || { echo "AUTH RESPONSE: $auth_json" >&2; fail "no AccessToken in AuthenticateByName response"; }
[[ -n "$USER_ID" ]] || fail "no User.Id in AuthenticateByName response"
echo "   authenticated as $USERNAME ($USER_ID)."

# ── 3. resolve the item id ─────────────────────────────────────────────────
# URL-encode the search term (spaces → %20; the clip name is plain ASCII).
SEARCH_ENC="${ITEM_NAME// /%20}"
items_json="$(curl -s --max-time 15 \
    "$SERVER_URL/Items?searchTerm=$SEARCH_ENC&Recursive=true&IncludeItemTypes=Movie&Limit=5" \
    -H "X-Emby-Token: $TOKEN")"
ITEM_ID="$(grep -oE '"Id":"[0-9a-f-]+"' <<<"$items_json" | head -1 | sed 's/.*:"//; s/"//')"
ITEM_FOUND_NAME="$(grep -oE '"Name":"[^"]+"' <<<"$items_json" | head -1 | sed 's/.*:"//; s/"$//')"
[[ -n "$ITEM_ID" ]] || { echo "ITEMS RESPONSE: $items_json" >&2; fail "no Movie item matched searchTerm='$ITEM_NAME'"; }
echo "   item: '$ITEM_FOUND_NAME' ($ITEM_ID)."

# Best-effort rerun hygiene: mark the clip unplayed so the server reports no
# resume position (the 12 s clip would otherwise resume at/near its end on a
# second run and the playback step could see ENDED instead of PLAYING).
curl -s --max-time 10 -X DELETE "$SERVER_URL/Users/$USER_ID/PlayedItems/$ITEM_ID" \
    -H "X-Emby-Token: $TOKEN" >/dev/null || true

# ── 4. app image ───────────────────────────────────────────────────────────
if [[ ! -f "$EXE" ]]; then
    echo "== app image missing — building (:apps:desktop:createDistributable)…"
    (cd "$REPO_ROOT" && ./gradlew :apps:desktop:createDistributable) || fail "createDistributable failed"
fi
[[ -f "$EXE" ]] || fail "app image not found at $EXE"
[[ -f "$MPV_DIR/libmpv-2.dll" ]] || fail "libmpv missing at $MPV_DIR/libmpv-2.dll (per-machine, gitignored)"

# ── 5. refuse a shared machine state ───────────────────────────────────────
procs_now="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r' | xargs)"
[[ -z "$procs_now" ]] || fail "$APP_NAME.exe already running (PIDs: $procs_now) — close it first (screenshots/keys must hit only our instance)."

# ── 6. spawn with harness props ────────────────────────────────────────────
STAMP="$(date +%Y%m%d-%H%M%S)"
PROFILE_NIX="$(mktemp -d -t jellyplay-e2e-XXXXXX)"
PROFILE_MIXED="$(cygpath -m "$PROFILE_NIX")"
MPV_MIXED="$(cygpath -m "$MPV_DIR")"
case "$PROFILE_MIXED" in *" "*) fail "profile dir contains spaces ($PROFILE_MIXED); JAVA_TOOL_OPTIONS cannot carry it."; ;; esac
case "$MPV_MIXED" in *" "*) fail "tools/mpv path contains spaces ($MPV_MIXED)."; ;; esac

LOG_OUT="$PROFILE_NIX/app.out"; LOG_ERR="$PROFILE_NIX/app.err"
echo "== launching $APP_NAME (profile: $PROFILE_NIX)"
export JAVA_TOOL_OPTIONS="-Djellyplay.harness.enabled=true -Djellyplay.harness.serverUrl=$SERVER_URL -Djellyplay.harness.username=$USERNAME -Djellyplay.harness.password=$PASSWORD -Djellyplay.harness.itemId=$ITEM_ID -Djellyplay.harness.autoExitSeconds=$AUTO_EXIT_SECONDS -Djellyplay.harness.screenshotDir=$PROFILE_MIXED/harness-shots -Djellyplay.perf.dataDir=$PROFILE_MIXED/profile -Djna.library.path=$MPV_MIXED"
"$EXE" > "$LOG_OUT" 2> "$LOG_ERR" &
BASH_PID=$!
unset JAVA_TOOL_OPTIONS

# ── 7. wait for the report (PID-only kill fallback) ────────────────────────
REPORT_NIX="$PROFILE_NIX/profile/data/logs/session-harness.json"
deadline=$(( SECONDS + AUTO_EXIT_SECONDS + 120 ))
observed_pids=''
report_seen=0
while (( SECONDS <= deadline )); do
    if [[ -f "$REPORT_NIX" ]]; then report_seen=1; break; fi
    # Sample live JellyPlay PIDs (our own window only) for the kill fallback.
    snap="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r')"
    while IFS= read -r pid_cur; do
        [[ -z "$pid_cur" ]] && continue
        case "
$observed_pids" in *"$pid_cur"*) ;; *) observed_pids="$observed_pids$pid_cur"$'\n' ;; esac
    done <<< "$snap"
    if [[ -z "${snap//[[:space:]]/}" ]] && ! kill -0 "$BASH_PID" 2>/dev/null; then
        break # process gone and no report
    fi
    sleep 2
done

if [[ "$report_seen" != "1" ]]; then
    echo "[harness] TIMEOUT — no session-harness.json after ${AUTO_EXIT_SECONDS}s + 120s grace." >&2
    echo "[harness] killing OUR observed PIDs only: $(echo "$observed_pids" | tr '\n' ' ')" >&2
    while IFS= read -r kp; do
        [[ -n "$kp" ]] && taskkill //PID "$kp" //F >/dev/null 2>&1
    done <<< "$observed_pids"
    echo "---- app stdout (last 40 lines) ----" >&2
    tail -40 "$LOG_OUT" >&2 2>/dev/null
    echo "---- app stderr (last 40 lines) ----" >&2
    tail -40 "$LOG_ERR" >&2 2>/dev/null
    fail "session harness did not produce a report"
fi
wait "$BASH_PID" 2>/dev/null

# ── 8. report + verdict ────────────────────────────────────────────────────
echo "== session-harness.json"
cat "$REPORT_NIX"
echo
echo "== screenshots ($PROFILE_NIX/harness-shots)"
ls -la "$PROFILE_NIX/harness-shots" 2>/dev/null || echo "   (none)"
echo "== harness stdout (app log: $LOG_OUT)"
grep "JellyPlay.*harness" "$LOG_OUT" 2>/dev/null || true

if grep -q '"overallPass":true' "$REPORT_NIX"; then
    echo "== RESULT: OVERALL PASS"
    exit 0
fi
echo "== RESULT: FAIL (see steps above; full logs in $PROFILE_NIX)" >&2
exit 1
