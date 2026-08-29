#!/usr/bin/env bash
# Device pass: compose-resources locale switching under Android per-app
# locales (plan V1a risk: "compose-resources locale switching on Android under
# the app's LocaleApplier/per-app-locale flows — needs a device pass").
#
# Method: set the app's per-app locale (cmd locale set-app-locales), cold
# restart, and assert KNOWN translated strings render on the FIRST-RUN screen
# (shared/feature:auth compose-resources — deterministic, no server needed):
#
#   de: auth_add_server_title        "Server hinzufügen"
#   ja: auth_add_server_title        "サーバーを追加"
#   restore en:                      "Add Server"
#
# The first-run screen is the assertion surface because the SETTINGS screen —
# the originally intended surface — ANRs on device in ANY locale (main thread
# blocks in compose-resources' runBlocking-backed stringResource while the
# ~900-string settings screen composes; stack captured in
# docs/e2e/device-locale-pass.md). Sign-in state is therefore irrelevant here;
# the script works signed-out and does NOT touch the fixture.
#
# Every step echoes PASS/FAIL; evidence (dumps) lands in
# tools/e2e/.results/device-locale/. Exit 0 only if all steps passed.
#
# Usage:   tools/e2e/device-locale-pass.sh
# Env:     ANDROID_SERIAL   device (default: first `adb devices` entry)
#          SKIP_INSTALL=1   don't reinstall the APK
# Requires: adb, a debuggable unlocked phone, python (dump parsing).
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULTS="$REPO_ROOT/tools/e2e/.results/device-locale"
PKG="com.raulshma.jellyplay.dev"
MAIN_ACT="$PKG/com.raulshma.jellyplay.MainActivity"
APK="$REPO_ROOT/app/build/outputs/apk/phone/debug/app-phone-arm64-v8a-debug.apk"
mkdir -p "$RESULTS"

ADB="adb"
if [ -n "${ANDROID_SERIAL:-}" ]; then ADB="adb -s $ANDROID_SERIAL"; fi

STEP=0; FAILED=0
pass() { STEP=$((STEP+1)); printf '[device-locale] PASS %2d: %s\n' "$STEP" "$*"; }
fail() { STEP=$((STEP+1)); printf '[device-locale] FAIL %2d: %s\n' "$STEP" "$*"; FAILED=1; }
note() { printf '[device-locale] note: %s\n' "$*"; }

ui_dump() {
  local name="${1:-dump}"
  $ADB shell "uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1" || true
  $ADB shell "cat /sdcard/window_dump.xml" > "$RESULTS/$name.xml" 2>/dev/null
  # uiautomator intermittently fails right after an activity transition
  # ("null root node"); an empty/missing file must not crash dump_text.
  [ -s "$RESULTS/$name.xml" ] || printf '<?xml version="1.0"?><hierarchy/>' > "$RESULTS/$name.xml"
}

# dump_text <substring> [file] -> 0 when the dump contains it in text= or
# content-desc= (CJK may round-trip through XML character references -
# unescape first). Default file is dump.xml; assert_locale passes its own
# per-tag evidence dump so the assertion can never read a stale dismiss-time
# snapshot (first de run FAILed exactly this way: the German screen was up
# and captured in locale-de.xml, but the assertion consulted the dump.xml
# dismiss_overlays had last written during the notification-prompt dance).
dump_text() {
  python - "${2:-$RESULTS/dump.xml}" "$1" <<'PYEOF'
import html, re, sys
xml = html.unescape(open(sys.argv[1], encoding="utf-8", errors="replace").read())
vals = re.findall(r'(?:text|content-desc)="([^"]*)"', xml)
sys.exit(0 if any(sys.argv[2] in v for v in vals) else 1)
PYEOF
}

dismiss_overlays() {
  local i
  for i in 1 2 3; do
    ui_dump dump || return 0
    if dump_text "isn't responding"; then
      note "dismiss ANR dialog (Wait)"
      $ADB shell "input tap 540 1282"; sleep 5
    fi
    if dump_text "Ready to install" || dump_text "Update verfügbar" || dump_text "Update available"; then
      note "dismiss self-update sheet"
      $ADB shell "input tap 540 800"; sleep 3   # scrim tap closes the sheet
    fi
    if dump_text "send you notifications"; then
      note "grant notifications (Allow)"
      $ADB shell "input tap 540 1171"; sleep 3
    fi
  done
}

# assert_locale <tag> <expected-substring> <label>
assert_locale() {
  local tag="$1" want="$2" label="$3" i
  $ADB shell "cmd locale set-app-locales $PKG --locales $tag" >/dev/null
  local got="$($ADB shell "cmd locale get-app-locales $PKG" | tr -d '\r')"
  if printf '%s' "$got" | grep -q "\[$tag\]"; then
    pass "per-app locale set to $tag"
  else
    fail "per-app locale set to $tag (got: $got)"; return
  fi
  $ADB shell "am force-stop $PKG" >/dev/null
  $ADB shell "am start -n $MAIN_ACT" >/dev/null
  # cold start: wait for the first-run screen (or a signed-in Home - both are
  # compose-resources surfaces, but the assertions below target first-run).
  # The loop polls dump.xml (dump_text's default) so each iteration reads the
  # dump JUST taken - polling a different file than the one written would
  # re-read one stale snapshot twelve times and degenerate into a fixed sleep.
  for i in $(seq 1 12); do
    sleep 2; ui_dump dump && { dump_text "Add Server" || dump_text "Server hinzufügen" || dump_text "サーバーを追加" || dump_text "Library"; } && break
  done
  dismiss_overlays
  ui_dump "locale-$tag"
  if dump_text "$want" "$RESULTS/locale-$tag.xml"; then
    pass "$label renders \"$want\" ($tag)"
  else
    fail "$label renders \"$want\" ($tag) - dump in .results/device-locale/locale-$tag.xml"
    $ADB exec-out screencap -p > "$RESULTS/locale-$tag.png"
  fi
}

# ── preconditions ──────────────────────────────────────────────────────────
$ADB get-state >/dev/null 2>&1 || { echo "[device-locale] FATAL: no device"; exit 1; }
$ADB shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" >/dev/null; sleep 1
if $ADB shell dumpsys window 2>/dev/null | grep -q "isKeyguardShowing=true"; then
  echo "[device-locale] FATAL: secure keyguard up - cannot automate UI"; exit 1
fi
if [ "${SKIP_INSTALL:-0}" != "1" ]; then
  if $ADB install -r "$APK" >/dev/null 2>&1; then
    pass "install phone-debug arm64 APK"
  else
    fail "install phone-debug arm64 APK"; exit 1
  fi
fi

# Start from clean data so the first-run (auth) screen is the assertion
# surface regardless of prior sign-in state. Locale assertions are about
# compose-resources, not session state.
$ADB shell "pm clear $PKG" >/dev/null

# ── the pass: de, ja, restore en ───────────────────────────────────────────
assert_locale de "Server hinzufügen"   "auth_add_server_title"
assert_locale de "Keine Server hinzugefügt" "auth no-servers line"
assert_locale ja "サーバーを追加"        "auth_add_server_title"
assert_locale ja "追加されたサーバーはありません" "auth no-servers line"
assert_locale en "Add Server"           "auth_add_server_title (restored)"

# ── verdict ────────────────────────────────────────────────────────────────
if [ "$FAILED" -eq 0 ]; then
  echo "[device-locale] VERDICT: PASS ($STEP steps, evidence in tools/e2e/.results/device-locale/)"
  exit 0
else
  echo "[device-locale] VERDICT: FAIL (see FAIL lines above)"
  exit 1
fi
