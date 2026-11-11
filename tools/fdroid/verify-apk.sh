#!/usr/bin/env bash
#
# verify-apk.sh — F-Droid pre-submission assertions for a JellyPlay APK.
#
# Usage:
#   tools/fdroid/verify-apk.sh [--expect-version-code N] <apk>
#   MIN_FONT_FILES=25 tools/fdroid/verify-apk.sh app.apk
#
# Asserts the GMS-free / self-contained invariants F-Droid's reviewers and
# reproducible-build tooling check (see tools/fdroid/README.md for what each
# check protects). Every failure prints a FAIL line and the script exits
# non-zero at the end — all checks run, so one pass shows the full gap list.
# Referenced from the kmp-build.yml "F-Droid flavor smoke build" step.

set -euo pipefail

# Font floor: the theme currently ships 17 families; the fdroid font seam
# bundles them as TTFs (res/font/) instead of relying on GMS downloadable
# fonts. 20 leaves headroom for the next family without loosen on regression.
MIN_FONT_FILES=${MIN_FONT_FILES:-20}

failures=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*" >&2; failures=$((failures + 1)); }

usage() {
  echo "usage: $0 [--expect-version-code N] <apk>" >&2
  exit 2
}

expect_version_code=""
apk=""
while [ $# -gt 0 ]; do
  case "$1" in
    --expect-version-code)
      [ $# -ge 2 ] || usage
      expect_version_code="$2"
      shift 2
      ;;
    --expect-version-code=*)
      expect_version_code="${1#*=}"
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      [ -z "$apk" ] || usage
      apk="$1"
      shift
      ;;
  esac
done

[ -n "$apk" ] || usage
if [ ! -f "$apk" ]; then
  echo "FAIL: APK not found: $apk" >&2
  exit 2
fi

# --- tool discovery ---------------------------------------------------------
# Build tools via ANDROID_HOME / ANDROID_SDK_ROOT (CI export both; local
# convention ~/Android/Sdk covers the rest). unzip+grep checks need nothing.
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
aapt2=""
aapt=""
dexdump=""
bt_dir=""
if [ -d "$sdk_dir/build-tools" ]; then
  # Highest installed build-tools version wins (CI runners ship several).
  bt=$(ls -1 "$sdk_dir/build-tools" | sort -V | tail -n 1)
  bt_dir="$sdk_dir/build-tools/$bt"
  if [ -x "$bt_dir/aapt2" ]; then aapt2="$bt_dir/aapt2"; fi
  if [ -x "$bt_dir/aapt" ]; then aapt="$bt_dir/aapt"; fi
  if [ -x "$bt_dir/dexdump" ]; then dexdump="$bt_dir/dexdump"; fi
fi

dump_tool=""
if [ -n "$aapt2" ]; then
  dump_tool="$aapt2"
elif [ -n "$aapt" ]; then
  dump_tool="$aapt"
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

echo "== verify-apk.sh: $apk (build-tools: ${bt_dir:-none}) =="

# --- check 1a: merged manifest — no REQUEST_INSTALL_PACKAGES ----------------
# F-Droid policy: the uploaded APK must not request install packages; on
# JellyPlay the GitHub-release updater is the only legitimate user and the
# fdroid manifest overlay tools:node="remove"s it. A hit here means the
# overlay is missing or src/main gained a new requestor.
if [ -n "$dump_tool" ]; then
  if [ "$dump_tool" = "$aapt2" ]; then
    manifest_dump="$("$dump_tool" dump xmltree "$apk" --file AndroidManifest.xml)"
  else
    manifest_dump="$("$dump_tool" dump xmltree "$apk" AndroidManifest.xml)"
  fi
  if grep -q 'REQUEST_INSTALL_PACKAGES' <<<"$manifest_dump"; then
    fail "manifest contains REQUEST_INSTALL_PACKAGES (fdroid overlay app/src/fdroid/AndroidManifest.xml must tools:node=\"remove\" it)"
  else
    pass "manifest carries no REQUEST_INSTALL_PACKAGES"
  fi

  # --- check 1b: merged manifest — no GMS identifiers ----------------------
  # Covers components, meta-data (incl. the cast OPTIONS_PROVIDER), providers
  # and any gms-adjacent attribute value: F-Droid must see a manifest free of
  # Google Play services references for the "non-free dependency" review.
  # ONE deliberate exception: the Android Auto media marker
  # (com.google.android.gms.car.application, src/phone overlay). It is an
  # inert string — no GMS code or artifact rides it (check 2 proves the dex
  # is gms-free) — and Android Auto lists the app's media service only when
  # it is declared. jellyfin-android ships the same marker on F-Droid.
  auto_marker='com\.google\.android\.gms\.car\.application'
  if grep -q "$auto_marker" <<<"$manifest_dump"; then
    pass "manifest declares the Android Auto media marker (com.google.android.gms.car.application)"
  else
    fail "manifest lost the Android Auto media marker (src/phone/AndroidManifest.xml) — Auto head units stop listing the app"
  fi
  if grep -v "$auto_marker" <<<"$manifest_dump" | grep -q 'com\.google\.android\.gms'; then
    fail "manifest references com.google.android.gms beyond the Auto media marker (cast/GMS leaked into main or fdroid)"
    grep -v "$auto_marker" <<<"$manifest_dump" | grep -n 'com\.google\.android\.gms' | head -5 >&2
  else
    pass "manifest carries no com.google.android.gms references beyond the Auto media marker"
  fi
else
  fail "no aapt2/aapt under $sdk_dir — cannot dump the manifest (set ANDROID_HOME)"
fi

# --- check 2: dex — no GMS / junrar class descriptors -----------------------
# Scans EVERY classes*.dex. junrar (Lcom/github/junrar/) is the classic
# F-Droid rejection: an untracked vendored RAR extractor with no license
# metadata. GMS descriptors mean play-services classes survived into the
# fdroid build (they must not, even if reflection-only).
dex_names="$(unzip -l "$apk" | grep -oE 'classes[0-9]*\.dex' | sort -u || true)"
if [ -z "$dex_names" ]; then
  fail "no classes*.dex found in the APK"
else
  gms_hits=""
  junrar_hits=""
  for dex in $dex_names; do
    dex_file="$tmp_dir/$dex"
    unzip -p "$apk" "$dex" > "$dex_file"
    if [ -n "$dexdump" ]; then
      class_strings="$("$dexdump" "$dex_file" 2>/dev/null || true)"
    else
      # Fallback: dex string tables are uncompressed MUTF-8, so plain
      # strings(1) surfaces every class descriptor.
      class_strings="$(strings "$dex_file" || true)"
    fi
    if grep -q 'Lcom/google/android/gms/' <<<"$class_strings"; then
      gms_hits="$gms_hits $dex"
    fi
    if grep -q 'Lcom/github/junrar/' <<<"$class_strings"; then
      junrar_hits="$junrar_hits $dex"
    fi
  done
  if [ -n "$gms_hits" ]; then
    fail "dex contains com.google.android.gms classes (dex:$gms_hits) — expected on the pre-fdroid-seam tree; later waves make cast store-only"
  else
    pass "dex carries no com/google/android/gms classes (${dex_names// /, })"
  fi
  if [ -n "$junrar_hits" ]; then
    fail "dex contains com.github.junrar classes (dex:$junrar_hits) — unlicensed vendored RAR code is an F-Droid rejection"
  else
    pass "dex carries no com/github/junrar classes"
  fi
fi

# --- check 3: resources — bundled fonts present -----------------------------
# The fdroid theme must not depend on GMS downloadable fonts: the APK itself
# carries the TTF families (merged from designsystem res/font/). Floor is
# MIN_FONT_FILES (the 17 shipped families plus headroom — fetched families
# added by the fonts seam). In minified+shrunk release builds aapt2 optimize
# SHORTENS resource paths (res/font/space_grotesk_regular.ttf → res/2Y.ttf;
# the file extension is preserved), so the count matches packaged res/ TTFs
# by extension instead of the res/font/ prefix — unminified and debug APKs
# keep the full paths and match the same pattern.
font_count="$(unzip -l "$apk" | grep -cE 'res/[^[:space:]]*\.ttf$' || true)"
if [ "$font_count" -ge "$MIN_FONT_FILES" ]; then
  pass "res/font/ carries $font_count font files (>= $MIN_FONT_FILES)"
else
  fail "res/font/ carries only $font_count font files (expected >= $MIN_FONT_FILES) — GMS-font-independent theming missing (fonts seam)"
fi

# --- check 4: assets — aboutlibraries.json ----------------------------------
# The settings screen renders licenses from this asset. It is COMMITTED at
# app/src/fdroid/assets/aboutlibraries.json (RB: the build-time export
# downloads license texts, which made it a network coin flip — see ADR 0014
# — so the fdroid lane ships the committed file as a plain source asset,
# drift-checked against the live dependency graph in CI). A hit-less APK
# ships a license screen that cannot render — an F-Droid compliance gap for
# "declared licenses".
# grep -c (not -q): with pipefail, -q's early exit SIGPIPEs unzip and fails
# the pipeline even when the entry exists.
about_count="$(unzip -l "$apk" | grep -c 'assets/aboutlibraries.json' || true)"
if [ "$about_count" -ge 1 ]; then
  pass "assets/aboutlibraries.json present"
else
  fail "assets/aboutlibraries.json missing — release asset-merge did not run exportLibraryDefinitions"
fi

# --- check 5 (optional): versionCode sanity ---------------------------------
# F-Droid's VercodeOperation aligns the uploaded APK's versionCode with the
# metadata base (+ abiIndex). A mismatch is the #1 mechanical rejection.
if [ -n "$expect_version_code" ]; then
  if [ -z "$dump_tool" ]; then
    fail "cannot read badging (no aapt2/aapt under $sdk_dir) — --expect-version-code skipped"
  else
    actual_code="$("$dump_tool" dump badging "$apk" | grep -oE "versionCode='[0-9]+'" | head -n 1 | grep -oE '[0-9]+' || true)"
    if [ "$actual_code" = "$expect_version_code" ]; then
      pass "versionCode matches expectation: $actual_code"
    else
      fail "versionCode mismatch: APK has '${actual_code:-<none>}', expected '$expect_version_code'"
    fi
  fi
fi

# --- summary ----------------------------------------------------------------
if [ "$failures" -gt 0 ]; then
  echo "== verify-apk.sh: $failures check(s) FAILED ==" >&2
  exit 1
fi
echo "== verify-apk.sh: all checks passed =="
