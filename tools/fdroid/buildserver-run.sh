#!/usr/bin/env bash
#
# buildserver-run.sh — container-side half of verify-repro.sh; runs INSIDE the
# official F-Droid buildserver image (registry.gitlab.com/fdroid/fdroidserver:
# buildserver-trixie). verify-repro.sh mounts this repo's tools/fdroid at
# /verify-tools and invokes it, so no host path ever appears on this side.
#
# Mirrors the fdroiddata MR CI build job: fdroidserver cloned from git at the
# commit the MR CI pins, same env, same `fdroid build` invocation. With
# `Binaries:` metadata, fdroidserver itself downloads the published reference
# APK for the requested versionCode and byte-compares it against this build
# (apksigcopier signature copy + apksigner validate) — the exact check
# F-Droid's infrastructure applies to every release.
#
# usage (from verify-repro.sh): buildserver-run.sh [--readmeta] <appid:vercode>
set -euo pipefail

readmeta=0
if [ "${1:-}" = "--readmeta" ]; then
  readmeta=1
  shift
fi
build=${1:?usage: buildserver-run.sh [--readmeta] <appid:vercode>}

# fdroidserver checkout pin — the fdroiddata MR CI's build job pins this
# commit; override with FDROIDSERVER_COMMIT when CI moves the pin.
FDROIDSERVER_COMMIT=${FDROIDSERVER_COMMIT:-a35fdfddd9c66823987a410566a6101186e39c84}
fdroidserver=/build/fdroidserver
FDROIDDATA=${FDROIDDATA:-/home/vagrant/fdroiddata}

# The mounted repos are owned by the host uid; git refuses "dubious ownership"
# for root without this.
git config --global --add safe.directory '*'

if [ ! -d "$fdroidserver" ]; then
  git clone -q https://gitlab.com/fdroid/fdroidserver.git "$fdroidserver"
  git -C "$fdroidserver" -c advice.detachedHead=false checkout -q "$FDROIDSERVER_COMMIT"
fi
export PATH="$fdroidserver:$PATH"
export PYTHONPATH="$fdroidserver:$fdroidserver/examples"
export PYTHONUNBUFFERED=true
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/home/vagrant/.gradle

# A scratch fdroiddata (built fresh by verify-repro.sh) carries no config.py;
# a near-empty one makes fdroidserver use its built-in defaults for every key
# (same mechanism as the annotated examples/config.py).
if [ ! -f "$FDROIDDATA/config.py" ]; then
  cat > "$FDROIDDATA/config.py" <<'PY'
# Written by buildserver-run.sh: deliberately near-empty — fdroidserver fills
# every unset key with its built-in default. See
# /build/fdroidserver/examples/config.py for the annotated full set.
PY
fi

echo "=== fdroidserver $(git -C "$fdroidserver" rev-parse --short HEAD) | $(java -version 2>&1 | sed -n 2p)"
cd "$FDROIDDATA"

if [ "$readmeta" -eq 1 ]; then
  fdroid readmeta
  echo "=== readmeta exit: $?"
else
  # The MR CI build job's exact invocation.
  fdroid build --verbose --test --refresh-scanner --on-server --no-tarball "$build"
  echo "=== fdroid build exit: $?"
fi
