#!/usr/bin/env bash
# tools/perf/desktop-baseline.sh — JellyPlay desktop Skia startup/memory
# baseline harness (wave 12A). Measurement-only companion to the code marks in
# DesktopStartupPerf; see docs/perf/desktop-skia-baseline.md for methodology.
#
# What it does, per run:
#   1. launches the PACKAGED app image (apps/desktop/build/compose/binaries/
#      main/app/JellyPlay/JellyPlay.exe — run
#      `./gradlew :apps:desktop:createDistributable` first) with JVM properties
#      injected through JAVA_TOOL_OPTIONS (inherited by every hosted-JVM
#      jpackage launcher):
#        -Djellyplay.perf.dataDir=<temp profile>   never touches real appdata
#        -Djellyplay.perf.autoExitSeconds=N        clean-but-blunt exitProcess(0)
#        -Djellyplay.perf.heapSampleSeconds=S      writes memory-latest.json at S s
#   2. samples the process working set (WorkingSet64 via PowerShell
#      Get-Process) once per ~0.75 s tick until exit; hard-kills by PID only
#      if the deadline passes.
#   3. parses <profile>/logs/startup-latest.json + memory-latest.json and
#      prints a markdown results table (min/median/max over measured runs).
#
# Spawn-path note (empirical, Windows): the jpackage launcher EXE is a
# SHORT-LIVED parent — `(Start-Process -PassThru).Id` hands back a stub that
# exits within ~1 s while the real JVM carries on in another process. Direct
# bash exec was verified end-to-end (env inherited, output redirectable), so
# this harness launches that way and samples ALL live JellyPlay.exe processes.
# It refuses to start while any JellyPlay.exe already runs so sampling can
# never capture someone else's session; kills are keyed strictly by PIDs we
# observed for our own window.
#
# Policy: WARMUP_RUNS discarded (default 1), then N measured runs (default 5).
#
# Usage:  tools/perf/desktop-baseline.sh [measured-runs]
# Env overrides: AUTO_EXIT_SECONDS (12), HEAP_SAMPLE_SECONDS (8),
#                WARMUP_RUNS (1), RESULTS_DIR (default under LOCALAPPDATA).
#
# Requires Git Bash on Windows: cygpath + powershell + taskkill. No jq needed.

set -u

MEASURED_RUNS="${1:-5}"
AUTO_EXIT_SECONDS="${AUTO_EXIT_SECONDS:-12}"
HEAP_SAMPLE_SECONDS="${HEAP_SAMPLE_SECONDS:-8}"
WARMUP_RUNS="${WARMUP_RUNS:-1}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_NAME="JellyPlay"
EXE="$REPO_ROOT/apps/desktop/build/compose/binaries/main/app/$APP_NAME/$APP_NAME.exe"

if [[ ! -f "$EXE" ]]; then
    echo "ERROR: app image not found at $EXE" >&2
    echo "Run: ./gradlew :apps:desktop:createDistributable" >&2
    exit 2
fi

# Data dirs: forward-slash Windows form (C:/Users/…) so the JVM property needs
# no backslash escaping AND JAVA_TOOL_OPTIONS whitespace tokenization stays safe.
RUNS_ROOT_NIX="$(cygpath -u "$LOCALAPPDATA")/${APP_NAME}PerfBaseline"
mkdir -p "$RUNS_ROOT_NIX" || { echo "ERROR: cannot mkdir $RUNS_ROOT_NIX" >&2; exit 2; }

STAMP="$(date +%Y%m%d-%H%M%S)"
OUTDIR="$RUNS_ROOT_NIX/results-$STAMP"
mkdir -p "$OUTDIR/logs" || exit 2

map_mixed() { # → C:/Users/… form with NO spaces (JAVA_TOOL_OPTIONS splits on spaces)
    cygpath -m "$1"
}

say_machine_specs() {
    echo "## Machine"
    local cpu cores ram_bytes osname ram_mb
    cpu="$(powershell -NoProfile -Command "(Get-CimInstance Win32_Processor | Select-Object -First 1).Name" | tr -d '\r' | xargs)"
    cores="$(powershell -NoProfile -Command "(Get-CimInstance Win32_Processor | Select-Object -First 1).NumberOfLogicalProcessors" | tr -d '\r' | xargs)"
    ram_bytes="$(powershell -NoProfile -Command "(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory" | tr -d '\r' | tr -d ' ')"
    osname="$(powershell -NoProfile -Command "(Get-CimInstance Win32_OperatingSystem) | ForEach-Object { \$_.Caption + ' build ' + \$_.BuildNumber }" | tr -d '\r' | xargs)"
    ram_mb=$(( ram_bytes / 1024 / 1024 ))
    {
        echo "| Field | Value |"
        echo "|---|---|"
        echo "| OS | $osname |"
        echo "| CPU | $cpu (${cores} logical cores) |"
        echo "| RAM | ${ram_mb} MB |"
        echo "| Harness | autoExitSeconds=$AUTO_EXIT_SECONDS heapSampleSeconds=$HEAP_SAMPLE_SECONDS warmup=$WARMUP_RUNS measured=$MEASURED_RUNS |"
    } | tee "$OUTDIR/machine.md"
}

# extract_number <file> <jsonKey> → stdout number or empty when missing/null
extract_number() {
    sed -n "s/.*\"$2\":\(-\{0,1\}[0-9][0-9.e+]*\).*/\1/p" "$1" 2>/dev/null | head -1
}

median_of_stdin() { # numeric lines → median value or '-'  (buffers ALL stdin
    local cnt mid a b vals          # up front — a piped consumer may only read it once)
    vals="$(cat)"
    cnt="$(grep -c . <<< "$vals" || true)"
    if [[ "$cnt" == "0" ]]; then echo "-"; return; fi
    sort -g <<< "$vals" > "$OUTDIR/.med.tmp"
    if (( cnt % 2 == 1 )); then
        mid=$(( (cnt + 1) / 2 ))
        sed -n "${mid}p" "$OUTDIR/.med.tmp"
    else
        mid=$(( cnt / 2 ))
        a=$(sed -n "${mid}p" "$OUTDIR/.med.tmp")
        b=$(sed -n "$((mid + 1))p" "$OUTDIR/.med.tmp")
        awk -v x="$a" -v y="$b" 'BEGIN { printf "%.3f", (x + y) / 2 }'
    fi
    rm -f "$OUTDIR/.med.tmp"
}

TICK=0
OBSERVED_PIDS=''

run_one() { # $1 = run index label; results land in EMIT_* globals
    local idx="$1"
    TICK=0
    OBSERVED_PIDS=''
    local data_dir_nix="$RUNS_ROOT_NIX/profile-run-$idx"
    rm -rf "$data_dir_nix"
    mkdir -p "$data_dir_nix"
    local data_dir_mixed
    data_dir_mixed="$(map_mixed "$data_dir_nix")"
    case "$data_dir_mixed" in
        *" "*) echo "ERROR: data dir contains spaces ($data_dir_mixed); JAVA_TOOL_OPTIONS cannot carry it safely." >&2; exit 3 ;;
    esac

    # Concurrency guard: refuse while ANY JellyPlay.exe already lives, so our
    # samples can only ever be this harness's own instances.
    local procs_now
    procs_now="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id" | tr -d '\r' | xargs)"
    if [[ -n "$procs_now" ]]; then
        echo "ERROR: JellyPlay.exe already running (PIDs: $procs_now) — refusing to sample shared machine state; close it and retry." >&2
        exit 4
    fi

    local log_file="$OUTDIR/logs/run-$idx.out"
    local err_file="$OUTDIR/logs/run-$idx.err"

    export JAVA_TOOL_OPTIONS="-Djellyplay.perf.autoExitSeconds=$AUTO_EXIT_SECONDS -Djellyplay.perf.heapSampleSeconds=$HEAP_SAMPLE_SECONDS -Djellyplay.perf.dataDir=$data_dir_mixed"
    "$EXE" > "$log_file" 2> "$err_file" &
    local bash_pid=$!
    unset JAVA_TOOL_OPTIONS

    local deadline=$(( SECONDS + AUTO_EXIT_SECONDS + 15 ))
    local ws_max=0 ws_at_sample='' timed_out=0 snap line out_any
    while :; do
        sleep 0.75
        # Each snapshot line: <wsBytes>,<pid> per live JellyPlay.exe instance
        # (comma-delimited: PowerShell "\t" is NOT a tab, so avoid it entirely).
        snap="$(powershell -NoProfile -Command "Get-Process -Name $APP_NAME -ErrorAction SilentlyContinue | ForEach-Object { \"\$(\$_.WorkingSet64),\$(\$_.Id)\" }")"
        snap="${snap//$'\r'/}"
        out_any=0
        if [[ -n "${snap//[[:space:]]/}" ]]; then
            out_any=1
            while IFS= read -r line; do
                [[ -z "$line" ]] && continue
                local ws_cur pid_cur
                ws_cur="${line%,*}"
                pid_cur="${line#*,}"
                (( ws_cur > ws_max )) && ws_max="$ws_cur"
                case "
$OBSERVED_PIDS" in *"$pid_cur"*) ;; *) OBSERVED_PIDS="$OBSERVED_PIDS$pid_cur"$'\n' ;; esac
            done <<< "$snap"
            TICK=$(( TICK + 1 ))
            # Idle-window ≈ ticks HEAP_SAMPLE_SECONDS..+5 wall-clock (~0.75 s
            # each); spawn latency shifts it ~1 s vs app uptime — medians over
            # the window keep that shift honest instead of hiding it.
            if (( TICK >= HEAP_SAMPLE_SECONDS && TICK <= HEAP_SAMPLE_SECONDS + 5 )); then
                ws_at_sample+="$(awk -F',' '{s+=$1} END {print int(s)}' <<< "$snap")"$'\n'
            fi
        fi
        if (( ! out_any )); then break; fi
        if (( SECONDS > deadline )); then
            echo "[harness] TIMEOUT after ${AUTO_EXIT_SECONDS}s +15 s grace; killing OUR observed PIDs only (PID-only kill): $(echo "$OBSERVED_PIDS" | tr '\n' ' ')" >&2
            timed_out=1
            break
        fi
    done
    if (( timed_out )); then
        local kp
        while IFS= read -r kp; do
            [[ -n "$kp" ]] && taskkill //PID "$kp" //F >/dev/null 2>&1
        done <<< "$OBSERVED_PIDS"
    fi
    wait "$bash_pid" 2>/dev/null

    local startup_json="$data_dir_nix/data/logs/startup-latest.json"
    local memory_json="$data_dir_nix/data/logs/memory-latest.json"
    cp -f "$startup_json" "$OUTDIR/logs/run-$idx.startup.json" 2>/dev/null
    cp -f "$memory_json" "$OUTDIR/logs/run-$idx.memory.json" 2>/dev/null
    cat "$err_file" >> "$log_file"
    if grep -Eqi "exception|fatal error|Uncaught" "$log_file"; then
        echo "[harness] WARN run $idx: output mentions exception/fatal/uncaught (see $log_file)" >&2
    fi

    EMIT_KOIN_MS="$(extract_number "$startup_json" koinStartMs)"
    EMIT_SHOWN_MS="$(extract_number "$startup_json" windowShownMs)"
    EMIT_FRAME_MS="$(extract_number "$startup_json" firstFrameMs)"
    EMIT_HEAP_MB=""
    local used idle_ws
    used="$(extract_number "$memory_json" usedHeapBytes)"
    [[ -n "$used" ]] && EMIT_HEAP_MB="$(awk -v u="$used" 'BEGIN { printf "%.1f", u / 1048576 }')"
    EMIT_IDLE_WS_MB=""
    idle_ws="$(printf '%s' "$ws_at_sample" | median_of_stdin)"
    [[ -n "$idle_ws" && "$idle_ws" != "-" ]] && \
        EMIT_IDLE_WS_MB="$(awk -v w="$idle_ws" 'BEGIN { printf "%.1f", w / 1048576 }')"
    EMIT_MAX_WS_MB=""
    (( ws_max > 0 )) && EMIT_MAX_WS_MB="$(awk -v w="$ws_max" 'BEGIN { printf "%.1f", w / 1048576 }')"
}

echo "== JellyPlay desktop Skia startup/memory baseline =="
echo "out dir: $OUTDIR"
say_machine_specs

HDR="| run | koinStartMs | windowShownMs | firstFrameMs | idleWS_MB(~${HEAP_SAMPLE_SECONDS}s) | maxWS_MB | usedHeap_MB |"
ROWS_FILE="$OUTDIR/rows.txt"; : > "$ROWS_FILE"
CSV="$OUTDIR/raw.csv"; echo "run,koinStartMs,windowShownMs,firstFrameMs,idleWSMB,maxWSMB,usedHeapMB" > "$CSV"

total_runs=$(( WARMUP_RUNS + MEASURED_RUNS ))
for i in $(seq 0 $(( total_runs - 1 ))); do
    label="warmup"
    (( i >= WARMUP_RUNS )) && label=$(( i - WARMUP_RUNS + 1 ))
    echo "-- run i=$i label=$label launching..." >&2
    run_one "$i"
    echo "| $label | ${EMIT_KOIN_MS:--} | ${EMIT_SHOWN_MS:--} | ${EMIT_FRAME_MS:--} | ${EMIT_IDLE_WS_MB:--} | ${EMIT_MAX_WS_MB:--} | ${EMIT_HEAP_MB:--} |" >> "$ROWS_FILE"
    echo "$label,$EMIT_KOIN_MS,$EMIT_SHOWN_MS,$EMIT_FRAME_MS,$EMIT_IDLE_WS_MB,$EMIT_MAX_WS_MB,$EMIT_HEAP_MB" >> "$CSV"
done

# Summary over MEASURED runs only (warmups dropped): min / median / max per field.
measured_rows="$(tail -n "$MEASURED_RUNS" "$CSV" | tail -n +2)"
summarize_field() { # $1 col index(1-based) → "min / median / max", missing '-' rows excluded
    local vals mn mx md
    vals="$(cut -d',' -f"$1" <<< "$measured_rows" | grep -Ev '^(-|$)')"
    if [[ -z "$vals" ]]; then echo "- / - / -"; return; fi
    mn="$(sort -g <<< "$vals" | head -1)"
    mx="$(sort -g <<< "$vals" | tail -1)"
    md="$(sort -g <<< "$vals" | median_of_stdin)"
    echo "$mn / $md / $mx"
}

{
    echo "$HDR"
    cat "$ROWS_FILE"
    echo ""
    echo "**Aggregates over $MEASURED_RUNS measured runs** (min / median / max; warmup excluded)"
    echo ""
    echo "| field | min / median / max |"
    echo "|---|---|"
    echo "| koinStart (ms) | $(summarize_field 2) |"
    echo "| windowShown (ms) | $(summarize_field 3) |"
    echo "| firstFrame (ms) | $(summarize_field 4) |"
    echo "| idle combined working set @~${HEAP_SAMPLE_SECONDS}s (MB, launcher+app) | $(summarize_field 5) |"
    echo "| largest single-process working set to exit (MB) | $(summarize_field 6) |"
    echo "| JVM used heap @${HEAP_SAMPLE_SECONDS}s (MB) | $(summarize_field 7) |"
} | tee "$OUTDIR/summary.md"

rm -rf "$RUNS_ROOT_NIX"/profile-run-* 2>/dev/null
echo "raw evidence kept in: $OUTDIR"
