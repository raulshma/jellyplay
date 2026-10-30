#!/usr/bin/env bash
# Device pass: SETTINGS SMOKE — every settings sub-screen opens on a real
# device without crashing (release variant, R8 on — the surface crash-on-open
# ships on). Closed the v0.11.6 settings crash: PreferenceScreenScaffold's
# inverted exclusivity check threw IllegalStateException at first composition
# of every declaration-path screen (Home / Appearance / Playback / Security /
# Language / Storage) — the settings drill-ins weren't exercised in testing,
# so the crash surfaced in the v0.11.6 testing round. This pass walks the
# settings root and taps every sub-screen row, failing on: process death, a
# new crash-buffer entry, or a screen that never renders.
#
# Drives the REAL app over adb + uiautomator:
#   deep-link jellyplay://settings -> for each sub-screen row: scroll-find ->
#   tap -> assert alive + no crash + navigated (root search bar gone) ->
#   back -> assert root returned. Best-effort nested screens drill in once.
#
# Every step drops evidence (uiautomator dumps, screencaps) into
# tools/e2e/.results/device-settings-smoke/. Exit 0 only if every step passed.
#
# Usage:   tools/e2e/device-settings-smoke-pass.sh
# Env:     ANDROID_SERIAL    device (default: first `adb devices` entry)
#          SMOKE_VARIANT     release (default) | debug — debug targets the
#                            .dev applicationId and the debug APK
#          SKIP_INSTALL=1    don't build/reinstall (reuse the installed app)
# Requires: adb + a Wi-Fi-unlocked phone with the app ALREADY SIGNED IN
#           (the pass does not automate first-run login; after a signature-
#           mismatch reinstall the login is wiped — sign in once, then
#           re-run with SKIP_INSTALL=1).
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULTS="$REPO_ROOT/tools/e2e/.results/device-settings-smoke"
VARIANT="${SMOKE_VARIANT:-release}"
if [ "$VARIANT" = "debug" ]; then
  PKG="com.raulshma.jellyplay.dev"
  APK="$REPO_ROOT/app/build/outputs/apk/phone/debug/app-phone-arm64-v8a-debug.apk"
  GRADLE_TASK=":app:assemblePhoneDebug"
else
  PKG="com.raulshma.jellyplay"
  APK="$(/bin/ls -t "$REPO_ROOT"/app/build/outputs/apk/phone/release/*arm64*.apk 2>/dev/null | head -1)"
  GRADLE_TASK=":app:assemblePhoneRelease"
fi
mkdir -p "$RESULTS"

ADB="adb"
if [ -n "${ANDROID_SERIAL:-}" ]; then ADB="adb -s $ANDROID_SERIAL"; fi

# ── logging / assertion helpers (device-pip-pass conventions) ──────────────
STEP=0
pass() { STEP=$((STEP+1)); printf '[device-settings-smoke] PASS %2d: %s\n' "$STEP" "$*"; }
fail() { STEP=$((STEP+1)); printf '[device-settings-smoke] FAIL %2d: %s\n' "$STEP" "$*"; FAILED=1; }
note() { printf '[device-settings-smoke] note: %s\n' "$*"; }
FAILED=0

ui_dump() {
  local name="${1:-dump}" i
  # uiautomator intermittently fails right after a transition ("null root
  # node") and LEAVES THE PREVIOUS DUMP FILE in place — a non-empty stale
  # read is worse than an empty one. Remove before dumping, retry, and only
  # accept a real hierarchy.
  for i in 1 2 3; do
    $ADB shell "rm -f /sdcard/window_dump.xml; uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1" || true
    $ADB shell "cat /sdcard/window_dump.xml" > "$RESULTS/$name.xml" 2>/dev/null
    if grep -q "<hierarchy" "$RESULTS/$name.xml" 2>/dev/null; then return 0; fi
    sleep 1
  done
  printf '<?xml version="1.0"?><hierarchy/>' > "$RESULTS/$name.xml"
}

# bounds_of <xml> <attr-regex> -> "cx cy" center of first matching node
bounds_of() {
  python3 - "$1" "$2" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
pat = re.compile(r'<node[^>]*?(?:text|content-desc)="' + sys.argv[2] + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
m = pat.search(xml)
if m:
    print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2)
PYEOF
}

dump_has() { grep -q "text=\"$1\"\|content-desc=\"$1\"" "$RESULTS/dump.xml" 2>/dev/null; }
tap_node() { # tap_node <label-regex> -> 0 on success
  local pt; pt="$(bounds_of "$RESULTS/dump.xml" "$1")"
  [ -n "$pt" ] || return 1
  $ADB shell "input tap $pt"
}
snap() { $ADB exec-out screencap -p > "$RESULTS/$1.png"; }

SCREEN_SIZE="$($ADB shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)"
SCREEN_W="${SCREEN_SIZE%x*}"; SCREEN_H="${SCREEN_SIZE#*x}"
[ -n "$SCREEN_W" ] || { SCREEN_W=1080; SCREEN_H=2246; }
CX=$((SCREEN_W / 2))

crash_lines() { $ADB logcat -d -b crash 2>/dev/null | wc -l; }
alive() { [ -n "$($ADB shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; }

# crash_delta <before> -> prints any NEW crash-buffer exception line, or empty
crash_delta() {
  local after; after="$(crash_lines)"
  if [ "$after" -gt "$1" ]; then
    $ADB logcat -d -b crash 2>/dev/null | tail -n +$(( $1 + 1 )) | grep -m1 -E "FATAL|Exception|Error" || echo "(crash buffer grew)"
  fi
}

# ── 0. preconditions ───────────────────────────────────────────────────────
$ADB get-state >/dev/null 2>&1 || { echo "[device-settings-smoke] FATAL: no device"; exit 1; }

# Wake + unlock (must be swipe-unlocked, not PIN-locked, for UI driving).
$ADB shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" >/dev/null
sleep 1
if $ADB shell dumpsys window 2>/dev/null | grep -q "isKeyguardShowing=true"; then
  echo "[device-settings-smoke] FATAL: secure keyguard up - cannot automate UI"; exit 1
fi

# ── 1. build + install ─────────────────────────────────────────────────────
if [ "${SKIP_INSTALL:-0}" != "1" ]; then
  if (cd "$REPO_ROOT" && ./gradlew "$GRADLE_TASK" --console=plain -q >/dev/null 2>&1); then
    pass "gradlew $GRADLE_TASK"
  else
    fail "gradlew $GRADLE_TASK"; exit 1
  fi
  # Local release APKs are unsigned (the release keystore is CI-only): sign
  # with the debug keystore so adb accepts the install. A CI-built APK is
  # already signed and skips through.
  if [ "$VARIANT" = "release" ] && [ -n "${APK:-}" ]; then
    SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$REPO_ROOT/local.properties" 2>/dev/null | tr -d '\r')"
    SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    BT="$(/bin/ls -t "$SDK_DIR/build-tools" 2>/dev/null | head -1)"
    if [ -n "$BT" ] && "$SDK_DIR/build-tools/$BT/apksigner" verify "$APK" >/dev/null 2>&1; then
      note "APK already signed"
    elif [ -n "$BT" ] && [ -f "$HOME/.android/debug.keystore" ]; then
      ZIPALIGN="$SDK_DIR/build-tools/$BT/zipalign"
      ALIGNED="$APK.aligned.apk"
      "$ZIPALIGN" -f 4 "$APK" "$ALIGNED" && \
        "$SDK_DIR/build-tools/$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" \
          --ks-pass pass:android --out "$APK" "$ALIGNED" >/dev/null 2>&1 && rm -f "$ALIGNED" \
        && note "signed with debug keystore (local install)" \
        || { fail "apksigner debug-keystore sign"; exit 1; }
    else
      note "no build-tools/debug.keystore found - installing as-is"
    fi
  fi
  # -d: a local iteration build carries a lower versionCode than the CI
  # release that's installed; --no-incremental: streamed install works where
  # the incremental path is refused on some devices.
  INSTALL_OUT="$($ADB install -r -d --no-incremental "$APK" 2>&1)"
  if echo "$INSTALL_OUT" | grep -q "Success"; then
    pass "adb install -r ($VARIANT)"
  elif echo "$INSTALL_OUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    note "signature mismatch - reinstalling (WIPES the app login)"
    $ADB uninstall "$PKG" >/dev/null 2>&1
    if echo "$($ADB install --no-incremental "$APK" 2>&1)" | grep -q "Success"; then
      fail "reinstalled fresh - SIGN IN on the device once, then re-run with SKIP_INSTALL=1"
    else
      fail "adb install after uninstall"
    fi
    exit 1
  else
    fail "adb install -r: $INSTALL_OUT"; exit 1
  fi
fi

# ── 2. cold start, straight into settings via deep link ────────────────────
$ADB shell am force-stop "$PKG" >/dev/null 2>&1
sleep 1
CRASH_BASE="$(crash_lines)"
$ADB shell "am start -a android.intent.action.VIEW -d 'jellyplay://settings' $PKG" >/dev/null 2>&1
ROOT_MARK="Power User Mode"
FOUND_ROOT=0
for i in $(seq 1 15); do
  sleep 2
  ui_dump dump
  if dump_has "$ROOT_MARK"; then FOUND_ROOT=1; break; fi
  # First-run gate: not signed in -> the walk cannot proceed.
  if dump_has "Add Server"; then
    fail "app is not signed in (onboarding visible) - sign in once, then re-run"
    snap not-signed-in; exit 1
  fi
done
[ "$FOUND_ROOT" = "1" ] && pass "settings root reachable (deep link + $ROOT_MARK marker)" \
  || { fail "settings root never rendered (no '$ROOT_MARK' marker)"; snap root-never; exit 1; }
if ! alive; then fail "process died reaching settings root"; exit 1; fi
D="$(crash_delta "$CRASH_BASE")"; [ -z "$D" ] && pass "no crash reaching root" || fail "crash reaching root: $D"
snap root

# ── 3. the walk ────────────────────────────────────────────────────────────
# Root-level sub-screens in list order; labels are the ROW titles (XML-escaped
# where the dump shows &amp;). REQUIRED rows must open; BEST_EFFORT rows may
# be absent (feature-gated / power-user-gated / renamed) — if present they are
# still crash-asserted. Nested entries drill in from their parent row.
# shellcheck disable=SC2034  # (best_effort read by the walk loop)
walk_rows=(
  "Home Screen|0"
  "Appearance|0"
  "Playback|0"
  "Language &amp; Subtitles|0"
  "Downloads &amp; Storage|0"
  "Notifications|0"
  "Security|0"
  "Privacy &amp; Data|0"
  "Audio Player|1"
  "Controls|1"
  "Backup &amp; Restore|1"
)

scroll_find_tap() { # scroll_find_tap <label> -> 0 if tapped
  local label="$1" i pt cy
  # search down first, then a single up-recover pass, interleaved
  for i in $(seq 1 12); do
    ui_dump dump
    pt="$(bounds_of "$RESULTS/dump.xml" "$label")"
    if [ -n "$pt" ]; then
      cy="${pt#* }"
      # rows under the floating nav bar can't take the tap — nudge further down
      if [ "$cy" -gt $((SCREEN_H - 330)) ]; then
        $ADB shell "input swipe $CX $((SCREEN_H - 400)) $CX $((SCREEN_H - 900)) 300"; sleep 1.5
        ui_dump dump
        pt="$(bounds_of "$RESULTS/dump.xml" "$label")"
        [ -z "$pt" ] && continue
      fi
      $ADB shell "input tap $pt"
      return 0
    fi
    # uiautomator hands back a FROZEN tree when it polls mid-transition (the
    # tree stops matching the real window and never refreshes): neither the
    # row nor the root marker in the dump means stale, not "scrolled past" —
    # re-enter the screen instead of swiping a dead hierarchy.
    if ! dump_has "$ROOT_MARK"; then
      note "stale dump in scroll-find ('$label') - re-deep-linking"
      $ADB shell "am start -a android.intent.action.VIEW -d 'jellyplay://settings' $PKG" >/dev/null 2>&1
      sleep 5
      continue
    fi
    $ADB shell "input swipe $CX $((SCREEN_H - 500)) $CX 700 400"; sleep 1.5
  done
  return 1
}

walk_one() { # walk_one <label> <best_effort> — full assert cycle for one row
  local label="$1" best="$2" D
  # a previous row's crash leaves the process dead — revive so one broken
  # screen doesn't noise up the rest of the walk (each row is asserted
  # independently; the earlier failure already poisoned the verdict)
  if ! alive; then
    note "process dead - re-deep-linking before '$label'"
    $ADB shell "am start -a android.intent.action.VIEW -d 'jellyplay://settings' $PKG" >/dev/null 2>&1
    sleep 4
  fi
  # The crash buffer is a RING buffer — on a crash-heavy walk old entries get
  # evicted and line-count deltas blame the wrong step. Clear it per step
  # instead: anything present after the tap happened on THIS tap.
  $ADB logcat -c -b crash >/dev/null 2>&1
  if ! scroll_find_tap "$label"; then
    if [ "$best" = "1" ]; then
      note "best-effort row '$label' not present - skipped"
      return 0
    fi
    fail "row '$label' never found on the settings root"
    snap "row-notfound-$(echo "$label" | tr -d ' &')"
    return 1
  fi
  sleep 3
  if ! alive; then
    fail "'$label': PROCESS DIED on open"; snap "dead-$(echo "$label" | tr -d ' &')"
    return 1
  fi
  D="$($ADB logcat -d -b crash 2>/dev/null | grep -m1 -E "FATAL|Exception" || true)"
  if [ -n "$D" ]; then
    fail "'$label': crash on open: $D"; snap "crash-$(echo "$label" | tr -d ' &')"
    return 1
  fi
  ui_dump dump
  if dump_has "$ROOT_MARK"; then
    # still on the root: the tap did not navigate (row mis-hit / label dup)
    if [ "$best" = "1" ]; then note "'$label' tap did not navigate - skipped"; return 0
    else fail "'$label': tap did not navigate (still on root)"; return 1; fi
  fi
  pass "'$label' opens, no crash"
  snap "open-$(echo "$label" | tr -d ' &')"
  $ADB shell "input keyevent KEYCODE_BACK"; sleep 2.5
  ui_dump dump
  if dump_has "$ROOT_MARK"; then
    pass "'$label' back to root"
  else
    note "'$label' back did not land on root (nested flow) - re-deep-linking"
    $ADB shell "am start -a android.intent.action.VIEW -d 'jellyplay://settings' $PKG" >/dev/null 2>&1
    sleep 3
  fi
  return 0
}

for row in "${walk_rows[@]}"; do
  label="${row%|*}"; best="${row#*|}"
  walk_one "$label" "$best" || true
done

# ── 4. verdict ─────────────────────────────────────────────────────────────
echo "==== device-settings-smoke summary ===="
echo "steps:    $STEP"
echo "variant:  $VARIANT ($PKG)"
if [ "$FAILED" = "0" ]; then
  echo "verdict:  PASS"
  exit 0
else
  echo "verdict:  FAIL"
  exit 1
fi
