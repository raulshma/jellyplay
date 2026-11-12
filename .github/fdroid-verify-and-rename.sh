#!/usr/bin/env bash
# Verify a built fdroid per-ABI APK and rename it to the exact filename the
# fdroiddata metadata's Binaries: template expands to. Shared by the two
# sequential builds in .github/workflows/fdroid-release.yml (c1 = arm64
# build 1, c2 = x86_64 build 2); args: <abi> <abi_index> <expected tag>.
#
# Run from the build tree root (the workflow sets working-directory).
set -euo pipefail

ABI="$1"
ABI_INDEX="$2"
EXPECTED_TAG="$3"

VERSION=$(grep -oP '(?<=^jellyplay\.versionName=)\S+' version.properties)
BASE=$(grep -oP '(?<=^jellyplay\.versionCode=)\d+' version.properties)
CODE=$((BASE + ABI_INDEX))
if [ "v${VERSION}" != "${EXPECTED_TAG}" ]; then
  echo "::error::version.properties says v${VERSION} but building ${EXPECTED_TAG} — version.properties was not bumped for this tag"
  exit 1
fi
APK=$(ls app/build/outputs/apk/phoneFdroid/release/*-"${ABI}"-release.apk)
tools/fdroid/verify-apk.sh --expect-version-code "$CODE" "$APK"
# The exact filename fdroidserver's Binaries: template expands to
# (v%v → this tag, %c → this build's versionCode). The store updater
# ignores these by construction: its asset matcher requires a
# -phone-/-tv- prefix and an -<abi>.apk/-universal.apk suffix
# (GitHubReleasesApiImpl.kt).
DEST="jellyplay-v${VERSION}-fdroid-${CODE}.apk"
cp "$APK" "$DEST"
echo "sha256 of the published reference binary:"
sha256sum "$DEST"
