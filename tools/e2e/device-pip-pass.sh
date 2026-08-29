#!/usr/bin/env bash
# Device pass: LIVE-TV/VIDEO PiP entry + exit on a physical phone (wave-19C
# residual "PiP entry/exit needs-device-pass", closed by the wave-21 device
# round together with two launch-crash fixes it surfaced — see
# docs/e2e/device-pip-pass.md for the full account and the recorded verdict:
# ENTRY is device-verified, EXPAND/DISMISS steps 8-9 have NOT yet run on a
# device — the wave-21 round was cut short there by directive, so this
# script's later steps remain unexercised until the next run).
#
# Drives the REAL app on a REAL device over adb + uiautomator:
#   cold start (clean data) -> connect to the Docker Jellyfin fixture over
#   Wi-Fi LAN -> sign in as the bootstrap `harness` user -> play the 5-minute
#   "Harness Pip Clip (2026)" testsrc clip -> HOME enters system PiP ->
#   menu->fullscreen EXPANDS (playback continues) -> HOME re-enters PiP ->
#   menu->X DISMISSES (PlayerActivity finishes, media session released).
#
# Every step echoes PASS/FAIL and drops evidence (uiautomator dumps,
# screencaps, dumpsys excerpts) into tools/e2e/.results/device-pip/.
# Exit 0 only if every step passed.
#
# Usage:   tools/e2e/device-pip-pass.sh
# Env:     ANDROID_SERIAL    device (default: first `adb devices` entry)
#          E2E_SERVER        Jellyfin base URL the PHONE can reach
#                            (default: http://<this PC's Wi-Fi IPv4>:8096)
#          E2E_STATE_DIR     fixture state dir (default tools/e2e/.state)
#          SKIP_INSTALL=1    don't rebuild/reinstall the APK (debug iterations)
# Requires: adb, a debuggable unlocked phone on Wi-Fi, docker fixture from
#           tools/e2e/bootstrap-jellyfin.sh (healthy), ffmpeg, python.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="${E2E_STATE_DIR:-$REPO_ROOT/tools/e2e/.state}"
RESULTS="$REPO_ROOT/tools/e2e/.results/device-pip"
PKG="com.raulshma.jellyplay.dev"                 # phone flavor + debug suffix
MAIN_ACT="$PKG/com.raulshma.jellyplay.MainActivity"
PLAYER_ACT="$PKG/com.raulshma.jellyplay.PlayerActivity"
APK="$REPO_ROOT/app/build/outputs/apk/phone/debug/app-phone-arm64-v8a-debug.apk"
USERNAME="harness"
PASSWORD="harness-e2e-pass"
CLIP_NAME="Harness Pip Clip"                     # display name w/o year
CLIP_FILE="$STATE_DIR/media/Harness Pip Clip (2026).mp4"
mkdir -p "$RESULTS"

ADB="adb"
if [ -n "${ANDROID_SERIAL:-}" ]; then ADB="adb -s $ANDROID_SERIAL"; fi

# ── logging / assertion helpers ────────────────────────────────────────────
STEP=0
pass() { STEP=$((STEP+1)); printf '[device-pip] PASS %2d: %s\n' "$STEP" "$*"; }
fail() { STEP=$((STEP+1)); printf '[device-pip] FAIL %2d: %s\n' "$STEP" "$*"; FAILED=1; }
note() { printf '[device-pip] note: %s\n' "$*"; }
FAILED=0

# ui_dump [name] -> dumps window hierarchy to $RESULTS/<name>.xml (host side)
ui_dump() {
  local name="${1:-dump}"
  $ADB shell "uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1" || true
  $ADB shell "cat /sdcard/window_dump.xml" > "$RESULTS/$name.xml" 2>/dev/null
  # uiautomator intermittently fails right after an activity transition
  # ("null root node"); an empty/missing file must not break dump parsing.
  [ -s "$RESULTS/$name.xml" ] || printf '<?xml version="1.0"?><hierarchy/>' > "$RESULTS/$name.xml"
}

# bounds_of <xml> <attr-regex> -> "cx cy" center of first matching node
# (matches text="..." or content-desc="..." attribute values)
bounds_of() {
  python - "$1" "$2" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
pat = re.compile(r'<node[^>]*?(?:text|content-desc)="' + sys.argv[2] + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
m = pat.search(xml)
if m:
    print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2)
PYEOF
}

# dump_has <text> -> true if current dump contains text/content-desc
dump_has() { grep -q "text=\"$1\"\|content-desc=\"$1\"" "$RESULTS/dump.xml" 2>/dev/null; }

tap_node() { # tap_node <label-regex> [dump-name] -> 0 on success
  local pt; pt="$(bounds_of "$RESULTS/${2:-dump}.xml" "$1")"
  [ -n "$pt" ] || return 1
  $ADB shell "input tap $pt"
}

snap() { $ADB exec-out screencap -p > "$RESULTS/$1.png"; }
shell_has() { $ADB shell "$1" 2>/dev/null | grep -q "$2"; }

wait_dump() { # wait_dump <text> <tries> [settle] -> dump present with text
  local text="$1" tries="${2:-10}" settle="${3:-2}" i
  for ((i=1; i<=tries; i++)); do
    sleep "$settle"
    ui_dump dump
    dump_has "$text" && return 0
    # the self-update sheet re-presents MID-SESSION (a second check lands
    # after first-run renders); keep dismissing while we poll
    grep -q 'text="Update available\|text="Ready to install' "$RESULTS/dump.xml" && dismiss_overlays
  done
  return 1
}

dismiss_overlays() { # update sheet / ANR leftovers / permission prompt
  local i
  for i in 1 2 3; do
    ui_dump dump || return 0
    if dump_has "isn't responding"; then
      note "dismiss ANR dialog (Wait)"
      tap_node "Wait" || $ADB shell "input tap 540 1282"
      sleep 5
    fi
    # self-update sheet: en build buttons; the sheet itself is evidence-only.
    # NOTE prefix match: the node text is "Update available — v0.10.5" / "Ready
    # to install — v0.10.4" - an exact text="..." grep would miss both. The
    # Later/Install buttons can sit below the visible fold (sheet content
    # varies by release) - then close via the "Close sheet" scrim tap instead.
    if grep -q 'text="Update available\|text="Ready to install' "$RESULTS/dump.xml"; then
      if grep -q 'text="Later"' "$RESULTS/dump.xml"; then
        note "dismiss self-update sheet (Later)"
        tap_node "Later" || true
      else
        note "dismiss self-update sheet (Close sheet scrim - buttons below fold)"
        $ADB shell "input tap 540 800"
      fi
      sleep 3
    fi
    if dump_has "send you notifications"; then
      note "grant notifications (Allow)"
      tap_node "Allow" || $ADB shell "input tap 540 1171"
      sleep 3
    fi
  done
  return 0
}

# ── 0. preconditions ───────────────────────────────────────────────────────
SERVER="${E2E_SERVER:-}"
if [ -z "$SERVER" ]; then
  LAN_IP="$(ipconfig | grep -A6 "Wireless LAN adapter Wi-Fi" | grep -oE "192\.168\.[0-9]+\.[0-9]+" | head -1)"
  [ -n "$LAN_IP" ] || { echo "[device-pip] FATAL: no Wi-Fi IPv4 found (set E2E_SERVER)"; exit 1; }
  SERVER="http://$LAN_IP:8096"
fi
note "server: $SERVER"

$ADB get-state >/dev/null 2>&1 || { echo "[device-pip] FATAL: no device"; exit 1; }
if ! curl -sf -m 3 "$SERVER/System/Info/Public" >/dev/null; then
  echo "[device-pip] FATAL: fixture not reachable at $SERVER (run tools/e2e/bootstrap-jellyfin.sh)"; exit 1
fi
if curl -sf -m 3 "$SERVER/System/Info/Public" | grep -qi '"StartupWizardCom.*false'; then
  echo "[device-pip] FATAL: fixture wizard incomplete"; exit 1
fi

# Wake + unlock (must be swipe-unlocked, not PIN-locked, for UI driving).
$ADB shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" >/dev/null
sleep 1
if $ADB shell dumpsys window 2>/dev/null | grep -q "isKeyguardShowing=true"; then
  echo "[device-pip] FATAL: secure keyguard up - cannot automate UI"; exit 1
fi
# Google autofill re-fills saved credentials over typed ones (measured on the
# first wave-21 run: app POSTed username "test" while the field showed
# "harness") - kill it for the session.
$ADB shell "settings put secure selected_autofill_service null"

# ── 1. build + install ─────────────────────────────────────────────────────
if [ "${SKIP_INSTALL:-0}" != "1" ]; then
  if (cd "$REPO_ROOT" && ./gradlew :app:assemblePhoneDebug --console=plain -q >/dev/null 2>&1); then
    pass "gradlew :app:assemblePhoneDebug"
  else
    fail "gradlew :app:assemblePhoneDebug"; exit 1
  fi
  if $ADB install -r "$APK" >/dev/null 2>&1; then
    pass "adb install -r phone-debug arm64 APK"
  else
    fail "adb install -r phone-debug arm64 APK"; exit 1
  fi
fi

# ── 2. fixture media: 5-minute PiP clip (12 s clips auto-exit PiP on END) ──
if [ ! -f "$CLIP_FILE" ]; then
  note "generating 300 s PiP clip (ffmpeg)"
  mkdir -p "$STATE_DIR/media"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc2=duration=300:size=640x360:rate=24" \
    -f lavfi -i "sine=frequency=330:duration=300" \
    -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart \
    "$CLIP_FILE" || { fail "ffmpeg PiP clip generation"; exit 1; }
fi
TOKEN="$(curl -sf -m 10 -X POST -H 'X-Emby-Authorization: MediaBrowser Client="e2e", Device="e2e", DeviceId="pip-pass-1", Version="1.0"' \
  -H 'Content-Type: application/json' -d "{\"Username\":\"$USERNAME\",\"Pw\":\"$PASSWORD\"}" \
  "$SERVER/Users/AuthenticateByName" | grep -oi '"accesstoken":"[^"]*"' | head -1 | cut -d'"' -f4)"
[ -n "$TOKEN" ] && pass "fixture auth (harness)" || { fail "fixture auth (harness)"; exit 1; }
curl -sf -m 15 -X POST -H "X-Emby-Token: $TOKEN" "$SERVER/Library/Refresh" >/dev/null || true
CLIP_ID=""
for i in $(seq 1 30); do
  CLIP_ID="$(curl -sf -m 10 -G -H "X-Emby-Token: $TOKEN" \
    --data-urlencode "searchTerm=$CLIP_NAME" --data-urlencode "Recursive=true" \
    --data-urlencode "IncludeItemTypes=Movie" "$SERVER/Items" \
    | tr '{' '\n' | grep -F "\"Name\":\"$CLIP_NAME\"" \
    | grep -oE '"[Ii]d":"[0-9a-f]{32}"' | head -1 | grep -oE '[0-9a-f]{32}')"
  [ -n "$CLIP_ID" ] && break; sleep 2
done
[ -n "$CLIP_ID" ] && pass "fixture item '$CLIP_NAME' ($CLIP_ID)" || { fail "fixture item '$CLIP_NAME'"; exit 1; }

# ── 3. clean cold start ────────────────────────────────────────────────────
$ADB shell "am force-stop $PKG; pm clear $PKG" >/dev/null
# Pre-grant POST_NOTIFICATIONS: the runtime prompt ignores synthetic taps
# unreliably (measured: repeated input taps on its Allow button never
# registered while the dialog was up) and it covers the first-run screen.
$ADB shell "pm grant $PKG android.permission.POST_NOTIFICATIONS" >/dev/null 2>&1
$ADB shell "cmd locale set-app-locales $PKG --locales en" >/dev/null 2>&1
$ADB shell "am start -n $MAIN_ACT" >/dev/null
# Cold start races the POST_NOTIFICATIONS prompt and the self-update sheet -
# both cover the first-run screen; dismiss as we poll.
COLD_OK=0
for i in $(seq 1 12); do
  sleep 2; dismiss_overlays; ui_dump dump && dump_has "Add Server" && { COLD_OK=1; break; }
done
if [ "$COLD_OK" -eq 1 ]; then
  pass "cold start reaches first-run (Add Server)"
else
  fail "cold start reaches first-run (Add Server)"; snap "fail-coldstart"; exit 1
fi
snap "01-first-run"

# ── 4. connect to fixture + sign in (UI-driven) ────────────────────────────
dismiss_overlays; ui_dump dump
tap_node "Add Server" || { fail "tap Add Server"; exit 1; }
if wait_dump "Server Address" 8 2; then
  pass "Add Server screen opens"
else
  fail "Add Server screen opens"; exit 1
fi
# address field = first EditText (Server Address)
python - "$RESULTS/dump.xml" > "$RESULTS/addr-pt.txt" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m: print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2)
PYEOF
ADDR_PT="$(cat "$RESULTS/addr-pt.txt")"
$ADB shell "input tap $ADDR_PT; sleep 1; input text '$SERVER'"
$ADB shell "input keyevent KEYCODE_BACK"   # hide IME so Connect is on-screen
sleep 1; ui_dump dump
if ! tap_node "Connect"; then
  fail "tap Connect"; exit 1
fi
if wait_dump "Username" 10 2; then
  pass "server connect reaches Sign In"
else
  fail "server connect reaches Sign In"; exit 1
fi
python - "$RESULTS/dump.xml" > "$RESULTS/fields.txt" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
pts = re.findall(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
for p in pts[:2]:
    print((int(p[0])+int(p[2]))//2, (int(p[1])+int(p[3]))//2)
PYEOF
FIELD1="$(sed -n 1p "$RESULTS/fields.txt")"; FIELD2="$(sed -n 2p "$RESULTS/fields.txt")"
$ADB shell "input tap $FIELD1; sleep 1; input text $USERNAME"
$ADB shell "input tap $FIELD2; sleep 1; input text $PASSWORD"
$ADB shell "input keyevent KEYCODE_BACK"; sleep 1; ui_dump dump
# Sign In BUTTON (not the screen title): the node with center y > 800
SIGN_PT="$(python - "$RESULTS/dump.xml" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r'text="Sign In"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    cy = (int(m.group(2))+int(m.group(4)))//2
    if cy > 800:
        print((int(m.group(1))+int(m.group(3)))//2, cy); break
PYEOF
)"
[ -n "$SIGN_PT" ] && $ADB shell "input tap $SIGN_PT"
# First-ever sign-in shows the onboarding carousel (Welcome to JellyPlay /
# Skip / Next) before Home - skip it if present.
for i in $(seq 1 15); do
  sleep 2; dismiss_overlays; ui_dump dump
  if dump_has "Library" && dump_has "Home"; then break; fi
  if dump_has "Skip"; then note "skipping onboarding"; tap_node "Skip"; sleep 2; fi
done
if ui_dump dump && dump_has "Library"; then
  pass "sign-in (harness) reaches Home"
else
  fail "sign-in (harness) reaches Home"; snap "fail-signin"; exit 1
fi
dismiss_overlays
snap "02-home"

# ── 5. navigate: Library -> clip -> Details -> Play ─────────────────────────
if ! tap_node "Library"; then $ADB shell "input swipe 540 1800 540 700 300"; sleep 2; ui_dump dump; tap_node "Library"; fi
if wait_dump "$CLIP_NAME" 10 2; then
  pass "Library lists '$CLIP_NAME'"
else
  fail "Library lists '$CLIP_NAME'"; snap "fail-library"; exit 1
fi
# The poster card may be half-scrolled off-screen (measured: a text node at
# [0,1487][21,2117] - a tap at its center hits the screen edge and opens
# nothing). Pick a WIDE instance of the label; if all are clipped, swipe the
# row and re-dump before tapping.
tap_clip_card() {
  local i pt
  for i in 1 2 3; do
    pt="$(python - "$RESULTS/dump.xml" "$CLIP_NAME" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r'<node[^>]*?(?:text|content-desc)="' + sys.argv[2] + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    x1, y1, x2, y2 = map(int, m.groups())
    if x1 >= 40 and (x2 - x1) >= 100:   # fully-enough visible
        print((x1 + x2)//2, (y1 + y2)//2); break
PYEOF
)"
    if [ -n "$pt" ]; then $ADB shell "input tap $pt"; return 0; fi
    note "clip card clipped - swiping row"
    $ADB shell "input swipe 900 1800 300 1800 400"; sleep 2; ui_dump dump
  done
  return 1
}
tap_clip_card || { fail "tap '$CLIP_NAME' card"; exit 1; }
# After the card tap the app lands on the DETAILS screen - but a tap that
# lands on the poster art itself can quick-start playback directly (observed:
# the player's "Brightness and volume controls" first-run hint). Accept
# either, and only look for the Play button on the details branch.
DETAILS_OK=0; PLAYER_UP=0
for i in 1 2 3 4 5; do
  sleep 2; ui_dump dump
  if dump_has "Play" || dump_has "More Like This" || dump_has "Mark as Watched"; then DETAILS_OK=1; break; fi
  if $ADB shell "dumpsys activity activities" 2>/dev/null | grep topResumedActivity | grep -q "PlayerActivity"; then PLAYER_UP=1; break; fi
  note "neither details nor player yet (attempt $i)"
done
if [ "$PLAYER_UP" -eq 1 ]; then
  note "card tap quick-started playback - skipping Play tap"
elif [ "$DETAILS_OK" -eq 1 ]; then
  # Play can sit below the fold depending on metadata height - scroll if absent
  if ! dump_has "Play"; then
    for i in 1 2 3; do
      $ADB shell "input swipe 540 1700 540 900 400"; sleep 2; ui_dump dump
      dump_has "Play" && break
    done
  fi
  pass "'$CLIP_NAME' details screen"
  snap "03-details"
  tap_node "Play" || { fail "tap Play"; exit 1; }
else
  fail "'$CLIP_NAME' details screen"; snap "fail-details"; exit 1
fi

# ── 6. assert playback ─────────────────────────────────────────────────────
sleep 8
if $ADB shell "dumpsys media_session" 2>/dev/null | grep -q "state=PLAYING(3)"; then
  pass "playback PLAYING (media_session)"
else
  fail "playback PLAYING (media_session)"; snap "fail-playback"; exit 1
fi
if $ADB shell "dumpsys activity activities" 2>/dev/null | grep -q "$PLAYER_ACT"; then
  pass "PlayerActivity is the playback host"
else
  fail "PlayerActivity is the playback host"; exit 1
fi
snap "04-playing"

# ── 7. PiP ENTRY: HOME ─────────────────────────────────────────────────────
$ADB shell "input keyevent KEYCODE_HOME"; sleep 3
PIPED="$($ADB shell "dumpsys activity activities" 2>/dev/null | grep -m1 'mode=pinned')"
if printf '%s' "$PIPED" | grep -q "$PKG"; then
  pass "HOME enters PiP (Task mode=pinned: $(printf '%s' "$PIPED" | grep -oE '#[0-9]+' | head -1))"
else
  fail "HOME enters PiP (no pinned task for $PKG)"; snap "fail-pip-entry"; exit 1
fi
PIP_BOUNDS="$($ADB shell "dumpsys activity activities" 2>/dev/null | grep -A1 -m1 'mode=pinned' | grep -oE 'mBounds=Rect\([0-9]+, [0-9]+ - [0-9]+, [0-9]+\)' | grep -oE '[0-9]+, [0-9]+ - [0-9]+, [0-9]+')"
L="$(printf '%s' "$PIP_BOUNDS" | sed 's/,.*//')";  T="$(printf '%s' "$PIP_BOUNDS" | sed 's/^[0-9]*, //; s/ -.*//')"
R="$(printf '%s' "$PIP_BOUNDS" | sed 's/.*- //; s/,.*//')"; B="$(printf '%s' "$PIP_BOUNDS" | sed 's/.*, //')"
CX=$(( (L + R) / 2 )); CY=$(( (T + B) / 2 ))
note "PiP frame [$L,$T-$R,$B] center ($CX,$CY)"
if $ADB shell "dumpsys media_session" 2>/dev/null | grep -q "state=PLAYING(3)"; then
  pass "playback continues while pinned"
else
  fail "playback continues while pinned"
fi
snap "05-pip"

# ── 8. PiP EXPAND: menu -> fullscreen button ───────────────────────────────
# Stock Android 14 PiP menu geometry (measured on the Nokia 6.1 Plus run):
# tap center opens the menu; buttons render INSIDE the pinned frame at
# left+~60/top+~63 (fullscreen) and right-~60/top+~63 (close X). Timing is a
# two-sided race: <~0.3 s and the two taps read as a double-tap (pause
# toggle), >~2 s and the menu auto-dismisses. Retry the sequence if still
# pinned (a menu timeout just eats one attempt harmlessly).
FS_X=$(( L + 60 )); FS_Y=$(( T + 63 ))
EXPANDED=0
for i in 1 2 3 4; do
  $ADB shell "input tap $CX $CY; sleep 0.5; input tap $FS_X $FS_Y"
  sleep 3
  if ! $ADB shell "dumpsys activity activities" 2>/dev/null | grep -q 'mode=pinned'; then
    EXPANDED=1; break
  fi
  note "expand attempt $i still pinned - retrying"
done
if [ "$EXPANDED" -eq 1 ]; then
  pass "menu->fullscreen expands (no pinned task)"
else
  fail "menu->fullscreen expands (still pinned)"; snap "fail-expand"; exit 1
fi
if $ADB shell "dumpsys activity activities" 2>/dev/null | grep topResumedActivity | grep -q "PlayerActivity"; then
  pass "PlayerActivity resumed after expand"
else
  fail "PlayerActivity resumed after expand"
fi
if $ADB shell "dumpsys media_session" 2>/dev/null | grep -q "state=PLAYING(3)"; then
  pass "playback continued through expand"
else
  fail "playback continued through expand"
fi
snap "06-expanded"

# ── 9. PiP re-entry + DISMISS: menu -> X ───────────────────────────────────
$ADB shell "input keyevent KEYCODE_HOME"; sleep 3
if $ADB shell "dumpsys activity activities" 2>/dev/null | grep -m1 'mode=pinned' | grep -q "$PKG"; then
  pass "HOME re-enters PiP"
else
  fail "HOME re-enters PiP"; snap "fail-pip-reentry"; exit 1
fi
PIP_BOUNDS="$($ADB shell "dumpsys activity activities" 2>/dev/null | grep -A1 -m1 'mode=pinned' | grep -oE 'mBounds=Rect\([0-9]+, [0-9]+ - [0-9]+, [0-9]+\)' | grep -oE '[0-9]+, [0-9]+ - [0-9]+, [0-9]+')"
L="$(printf '%s' "$PIP_BOUNDS" | sed 's/,.*//')";  T="$(printf '%s' "$PIP_BOUNDS" | sed 's/^[0-9]*, //; s/ -.*//')"
R="$(printf '%s' "$PIP_BOUNDS" | sed 's/.*- //; s/,.*//')"; B="$(printf '%s' "$PIP_BOUNDS" | sed 's/.*, //')"
CX=$(( (L + R) / 2 )); CY=$(( (T + B) / 2 ))
X_X=$(( R - 60 )); X_Y=$(( T + 63 ))
DISMISSED=0
for i in 1 2 3 4; do
  $ADB shell "input tap $CX $CY; sleep 0.5; input tap $X_X $X_Y"
  sleep 4
  if ! $ADB shell "dumpsys activity activities" 2>/dev/null | grep -q 'mode=pinned'; then
    DISMISSED=1; break
  fi
  note "dismiss attempt $i still pinned - retrying"
done
if [ "$DISMISSED" -eq 1 ]; then
  pass "menu->X dismisses (no pinned task)"
else
  fail "menu->X dismisses"; snap "fail-dismiss"; exit 1
fi
# Dismiss must finish PlayerActivity and release the session (browse revealed).
sleep 2
if ! $ADB shell "dumpsys activity activities" 2>/dev/null | grep topResumedActivity | grep -q "PlayerActivity"; then
  pass "PlayerActivity no longer top-resumed (finished)"
else
  fail "PlayerActivity still top-resumed after dismiss"
fi
if ! $ADB shell "dumpsys media_session" 2>/dev/null | grep -q "state=PLAYING(3)"; then
  pass "media session released (no PLAYING session)"
else
  fail "media session still PLAYING after dismiss"
fi
snap "07-dismissed"

# ── verdict ────────────────────────────────────────────────────────────────
if [ "$FAILED" -eq 0 ]; then
  echo "[device-pip] VERDICT: PASS ($STEP steps, evidence in tools/e2e/.results/device-pip/)"
  exit 0
else
  echo "[device-pip] VERDICT: FAIL (see FAIL lines above; evidence in tools/e2e/.results/device-pip/)"
  exit 1
fi
