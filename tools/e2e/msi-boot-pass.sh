#!/usr/bin/env bash
# tools/e2e/msi-boot-pass.sh — wave 13A "installed-MSI boot pass".
#
# The wave 12 boot smoke only ever launched the createDistributable
# app-image exe. This tool closes the remaining gap: verify the ACTUAL MSI
# artifact's payload boots. It never installs anything — it administrative-
# extracts the MSI (msiexec /a, no UAC expected) to a temp dir outside the
# repo, checks the extracted layout, boots the EXTRACTED JellyPlay.exe under
# the perf-harness properties (fresh data dir, 30 s auto-exit), and reports
# PASS/FAIL.
#
# Steps:
#   1. Build the MSI (unless SKIP_BUILD=1 or MSI=<path> is provided):
#        ./gradlew :apps:desktop:packageMsi -PjellyplayVersion=<version>
#      artifact: apps/desktop/build/compose/binaries/main/msi/JellyPlay-<v>.msi
#   2. msiexec /a <msi> /qn TARGETDIR=<temp> — administrative extract, no
#      elevation. Exit 0 (or 3010) expected; the verbose msi log is kept in
#      the run dir for diagnosis. NOTE: on locked-down machines where /a
#      unexpectedly demands elevation (non-zero exit), this script FAILS
#      honestly — it does not silently substitute the app image.
#   3. Layout check: <TARGETDIR>/JellyPlay/JellyPlay.exe + runtime/ + app/;
#      file count sanity-compared against the app image when one exists.
#   4. Boot the extracted exe with JAVA_TOOL_OPTIONS:
#        -Djellyplay.perf.dataDir=<temp>/msi-boot-data
#        -Djellyplay.perf.autoExitSeconds=30
#        -Djellyplay.perf.persist=true
#      Spawn/kill pattern copied from tools/perf/desktop-baseline.sh: direct
#      bash background exec, PowerShell Get-Process JellyPlay sampling every
#      ~1 s, deadline autoExit+20 s, PID-ONLY taskkill fallback (never title
#      patterns), refuse to start while any JellyPlay.exe already runs.
#
# PASS = process exits BY ITSELF + <dataDir>/data/logs/startup-latest.json
# exists with windowShownMs >= 0 + zero crash-*.log under the data dir.
#
# Usage:   tools/e2e/msi-boot-pass.sh
# Env:     JELLYPLAY_VERSION (0.1.1)   MSI (explicit .msi path; skips build)
#          SKIP_BUILD=1 (reuse expected artifact)   AUTO_EXIT_SECONDS (30)
#          KEEP_EXTRACT=1 (keep extracted tree even on PASS)
# Requires Git Bash on Windows: cygpath, cmd, powershell, taskkill.
# All runtime state lives in the OS temp dir OUTSIDE the repo.

set -u

VERSION="${JELLYPLAY_VERSION:-0.1.1}"
AUTO_EXIT_SECONDS="${AUTO_EXIT_SECONDS:-30}"
SKIP_BUILD="${SKIP_BUILD:-0}"
KEEP_EXTRACT="${KEEP_EXTRACT:-0}"
APP_NAME="JellyPlay"
EXE_NAME="$APP_NAME.exe"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MSI_DIR="$REPO_ROOT/apps/desktop/build/compose/binaries/main/msi"
MSI_EXPECTED="$MSI_DIR/$APP_NAME-$VERSION.msi"
MSI="${MSI:-}"

fail() { echo "FAIL: $*" >&2; exit 1; }

# ── temp locations (outside the repo) ────────────────────────────────────────
BASE_TMP_NIX="$(cygpath -u "${TMP:-${TEMP:-/tmp}}")"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK_ROOT="$BASE_TMP_NIX/jellyplay-msi-boot-pass"
EXTRACT_DIR="$WORK_ROOT/extract"
RUN_DIR="$WORK_ROOT/run-$STAMP"
DATA_DIR_NIX="$RUN_DIR/msi-boot-data"
mkdir -p "$RUN_DIR" || fail "cannot mkdir $RUN_DIR"

map_mixed() { # → C:/Users/… form with NO spaces (JAVA_TOOL_OPTIONS splits on spaces)
    cygpath -m "$1"
}

# extract_number <file> <jsonKey> → stdout number or empty when missing/null
extract_number() {
    sed -n "s/.*\"$2\":\(-\{0,1\}[0-9][0-9.e+]*\).*/\1/p" "$1" 2>/dev/null | head -1
}

echo "== JellyPlay installed-MSI boot pass (wave 13A) =="
echo "run dir: $RUN_DIR"

# ── 1. locate / build the MSI ───────────────────────────────────────────────
if [[ -n "$MSI" ]]; then
    [[ -f "$MSI" ]] || fail "MSI override not found: $MSI"
    MSI_ABS="$(cd "$(dirname "$MSI")" && pwd)/$(basename "$MSI")"
    echo "msi: using override $MSI_ABS"
else
    MSI_ABS="$MSI_EXPECTED"
    if [[ ! -f "$MSI_ABS" && "$SKIP_BUILD" == "1" ]]; then
        fail "SKIP_BUILD=1 but expected artifact missing: $MSI_ABS"
    fi
    if [[ ! -f "$MSI_ABS" ]]; then
        echo "msi: not found at $MSI_ABS — building via gradle packageMsi (version $VERSION)..."
        ( cd "$REPO_ROOT" && ./gradlew ":apps:desktop:packageMsi" "-PjellyplayVersion=$VERSION" --console=plain ) \
            > "$RUN_DIR/gradle-packageMsi.log" 2>&1
        GRADLE_RC=$?
        if (( GRADLE_RC != 0 )); then
            tail -30 "$RUN_DIR/gradle-packageMsi.log" >&2
            fail "gradle packageMsi exited $GRADLE_RC (full log: $RUN_DIR/gradle-packageMsi.log)"
        fi
        [[ -f "$MSI_ABS" ]] || fail "gradle reported success but artifact missing: $MSI_ABS"
    else
        echo "msi: reusing existing $MSI_ABS (delete it to force a rebuild; or pass MSI=<path>)"
    fi
fi
MSI_BYTES=$(wc -c < "$MSI_ABS")
MSI_MB=$(( MSI_BYTES / 1024 / 1024 ))
echo "msi size: $MSI_MB MB ($MSI_ABS)"

# ── 2. administrative extract (NO elevation expected) ───────────────────────
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"
MSI_WIN="$(cygpath -w "$MSI_ABS")"
TGT_WIN="$(cygpath -w "$EXTRACT_DIR")"
MSI_LOG_NIX="$RUN_DIR/msiexec-extract.log"
MSI_LOG_WIN="$(cygpath -w "$MSI_LOG_NIX")"
# QUOTING CONTRACT (empirical, this repo's Git Bash): any DOUBLE QUOTE inside
# the cmd //c string arrives at cmd as a literal backslash-quote (the exec
# layer re-escapes it), which msiexec then chokes on (observed exit 86, no
# verbose log ever written). So the command line below is deliberately
# QUOTE-FREE — which is only safe while no path contains whitespace, hence
# the guard. /qn must never be dropped either: without it msiexec pops a GUI
# dialog that blocks the shell forever.
for p in "$MSI_WIN" "$TGT_WIN" "$MSI_LOG_WIN"; do
    case "$p" in
        *' '*) fail "path contains spaces, quote-free msiexec line cannot carry it: $p" ;;
    esac
done
echo "extracting (msiexec /a, no elevation): TARGETDIR=$TGT_WIN"
cmd //c "msiexec /a $MSI_WIN /qn TARGETDIR=$TGT_WIN /l*v $MSI_LOG_WIN" \
    > "$RUN_DIR/msiexec.out" 2>&1
MSIEXEC_RC=$?
echo "msiexec exit code: $MSIEXEC_RC (0 = ok, 3010 = ok+reboot-required)"
if (( MSIEXEC_RC != 0 && MSIEXEC_RC != 3010 )); then
    echo "  common codes: 1603 fatal (often elevation/AV), 1619 msi unreadable, 1635 patch" >&2
    echo "  verbose msi log kept: $MSI_LOG_NIX" >&2
    fail "administrative extract failed with $MSIEXEC_RC — if the log shows elevation demands (locked-down machine), document and use the app-image smoke as proxy"
fi

# ── 3. layout verification ──────────────────────────────────────────────────
APP_ROOT="$EXTRACT_DIR/$APP_NAME"
if [[ ! -f "$APP_ROOT/$EXE_NAME" ]]; then
    # Fallback: some MSI layouts extract directly into TARGETDIR.
    if [[ -f "$EXTRACT_DIR/$EXE_NAME" ]]; then
        APP_ROOT="$EXTRACT_DIR"
        echo "note: exe found directly under TARGETDIR (no $APP_NAME/ subdir)"
    else
        fail "no $EXE_NAME under $EXTRACT_DIR/$APP_NAME or $EXTRACT_DIR — extraction layout unexpected"
    fi
fi
for dir in runtime app; do
    [[ -d "$APP_ROOT/$dir" ]] || fail "expected dir missing in extracted image: $APP_ROOT/$dir"
done
EXTRACTED_EXE="$APP_ROOT/$EXE_NAME"
EXTRACTED_COUNT=$(find "$APP_ROOT" -type f | wc -l | tr -d ' ')
echo "extracted layout ok: $EXTRACTED_EXE + runtime/ + app/"
echo "extracted file count: $EXTRACTED_COUNT"

APP_IMAGE_DIR="$REPO_ROOT/apps/desktop/build/compose/binaries/main/app/$APP_NAME"
APP_IMAGE_COUNT=''
if [[ -d "$APP_IMAGE_DIR" ]]; then
    APP_IMAGE_COUNT=$(find "$APP_IMAGE_DIR" -type f | wc -l | tr -d ' ')
    echo "app-image file count (sanity ref): $APP_IMAGE_COUNT"
    if (( APP_IMAGE_COUNT > 0 && EXTRACTED_COUNT * 2 < APP_IMAGE_COUNT )); then
        fail "extracted file count ($EXTRACTED_COUNT) less than half the app image ($APP_IMAGE_COUNT) — extraction looks truncated"
    fi
    (( EXTRACTED_COUNT != APP_IMAGE_COUNT )) && \
        echo "note: counts differ (extracted=$EXTRACTED_COUNT vs app-image=$APP_IMAGE_COUNT) — informational"
else
    echo "app-image not present at $APP_IMAGE_DIR — skipping sanity count (informational)"
fi

# ── 4. boot the EXTRACTED exe under perf-harness properties ─────────────────
DATA_DIR_MIXED="$(map_mixed "$DATA_DIR_NIX")"
case "$DATA_DIR_MIXED" in
    *" "*) fail "data dir contains spaces ($DATA_DIR_MIXED); JAVA_TOOL_OPTIONS cannot carry it safely" ;;
esac
rm -rf "$DATA_DIR_NIX"; mkdir -p "$DATA_DIR_NIX"

# Concurrency guard: refuse while ANY JellyPlay.exe already lives, so the
# sampled session can only be ours.
PROCS_NOW="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r' | xargs)"
[[ -n "$PROCS_NOW" ]] && fail "$EXE_NAME already running (PIDs: $PROCS_NOW) — close it and retry"

export JAVA_TOOL_OPTIONS="-Djellyplay.perf.dataDir=$DATA_DIR_MIXED -Djellyplay.perf.autoExitSeconds=$AUTO_EXIT_SECONDS -Djellyplay.perf.persist=true"
"$EXTRACTED_EXE" > "$RUN_DIR/boot.out" 2> "$RUN_DIR/boot.err" &
BASH_PID=$!
unset JAVA_TOOL_OPTIONS

DEADLINE=$(( SECONDS + AUTO_EXIT_SECONDS + 20 ))
OBSERVED_PIDS=''
TIMED_OUT=0
while :; do
    sleep 1
    SNAP="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r')"
    if [[ -z "${SNAP//[[:space:]]/}" ]]; then
        break # no live JellyPlay.exe anywhere — our session ended
    fi
    while IFS= read -r pid_cur; do
        [[ -z "$pid_cur" ]] && continue
        case "
$OBSERVED_PIDS" in *"$pid_cur"*) ;; *) OBSERVED_PIDS="$OBSERVED_PIDS$pid_cur"$'\n' ;; esac
    done <<< "$SNAP"
    if (( SECONDS > DEADLINE )); then
        echo "[harness] TIMEOUT after ${AUTO_EXIT_SECONDS}s+20s grace; killing OUR observed PIDs only (PID-only kill): $(echo "$OBSERVED_PIDS" | tr '\n' ' ')" >&2
        TIMED_OUT=1
        break
    fi
done
if (( TIMED_OUT )); then
    while IFS= read -r kp; do
        [[ -n "$kp" ]] && taskkill //PID "$kp" //F >/dev/null 2>&1
    done <<< "$OBSERVED_PIDS"
fi
wait "$BASH_PID" 2>/dev/null
BOOT_RC=$?

grep -q "Picked up JAVA_TOOL_OPTIONS" "$RUN_DIR/boot.err" \
    && echo "java props: injected (stderr banner present)" \
    || echo "java props: WARNING — no 'Picked up JAVA_TOOL_OPTIONS' banner in boot.err"

if (( TIMED_OUT )); then
    tail -20 "$RUN_DIR/boot.out" "$RUN_DIR/boot.err" >&2 || true
    fail "extracted exe did NOT exit by itself within ${AUTO_EXIT_SECONDS}s+20s (auto-exit never fired?)"
fi
echo "self-exit: yes (auto-exit fired; bash wait rc=$BOOT_RC)"

# ── 5. evidence checks + verdict ────────────────────────────────────────────
STARTUP_JSON="$DATA_DIR_NIX/data/logs/startup-latest.json"
CRASH_COUNT=$(find "$DATA_DIR_NIX" -name 'crash-*.log' 2>/dev/null | wc -l | tr -d ' ')
WINDOW_SHOWN_MS="$(extract_number "$STARTUP_JSON" windowShownMs)"

VERDICT=PASS
PROBLEMS=''
if [[ ! -f "$STARTUP_JSON" ]]; then
    PROBLEMS+="startup-latest.json missing at $STARTUP_JSON"$'\n'
elif [[ -z "$WINDOW_SHOWN_MS" ]]; then
    PROBLEMS+="windowShownMs missing/null in startup-latest.json"$'\n'
elif (( $(awk -v v="$WINDOW_SHOWN_MS" 'BEGIN { print (v >= 0) ? 1 : 0 }') == 0 )); then
    PROBLEMS+="windowShownMs=$WINDOW_SHOWN_MS is not >= 0"$'\n'
fi
(( CRASH_COUNT == 0 )) || PROBLEMS+="$CRASH_COUNT crash-*.log under $DATA_DIR_NIX"$'\n'

if [[ -n "$PROBLEMS" ]]; then
    VERDICT=FAIL
    while IFS= read -r p; do
        [[ -n "$p" ]] && echo "  problem: $p" >&2
    done <<< "$PROBLEMS"
fi

echo ""
echo "==== msi-boot-pass summary ===="
echo "verdict:         $VERDICT"
echo "msi:             $MSI_ABS ($MSI_MB MB, version $VERSION)"
echo "extracted files: $EXTRACTED_COUNT (app-image ref: ${APP_IMAGE_COUNT:-n/a})"
echo "windowShownMs:   ${WINDOW_SHOWN_MS:--}"
echo "crash logs:      $CRASH_COUNT"
echo "self-exit:       yes (${AUTO_EXIT_SECONDS}s auto-exit)"
echo "evidence kept:   $RUN_DIR (boot.out/boot.err, msiexec-extract.log, startup json)"

if [[ "$VERDICT" == "PASS" && "$KEEP_EXTRACT" != "1" ]]; then
    rm -rf "$EXTRACT_DIR" # ~300 MB temp payload; keep on FAIL/KEEP_EXTRACT=1
fi
[[ "$VERDICT" == "PASS" ]] || exit 1
exit 0
