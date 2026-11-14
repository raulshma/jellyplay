#!/usr/bin/env bash
#
# buildserver-run.sh — container-side half of verify-repro.sh; runs INSIDE the
# official F-Droid buildserver image (registry.gitlab.com/fdroid/fdroidserver:
# buildserver-trixie). verify-repro.sh mounts this repo's tools/fdroid at
# /verify-tools and invokes it, so no host path ever appears on this side.
#
# Replicates the fdroiddata MR CI (gitlab.com/fdroid/fdroiddata
# .gitlab-ci.yml) phase by phase, on a scratch fdroiddata:
#
#   --deps <appid:vercode>       manual-only: packages the MR CI's test jobs
#                                install in their own images (exiftool, jq,
#                                python3-yaml, python3-markdown-it,
#                                check-jsonschema).
#   --meta-ci <appid:vercode>    the MR CI *test* jobs: fdroid lint,
#                                fdroid rewritemeta, schema validation,
#                                checkupdates, git redirect, tools check
#                                scripts, check source code (check-fastlane).
#                                Installs the test-job packages itself (a
#                                separate deps container would leave nothing
#                                behind — containers are ephemeral).
#   --readmeta <appid:vercode>   legacy quick parse check.
#   <appid:vercode>              the MR CI *build* job + the dependent
#                                check-apk job: dist-upgrade + pinned JDK 21 +
#                                production buildserver gitconfig + sdkmanager
#                                extras, `fdroid fetchsrclibs`, the real
#                                `fdroid build` (with `Binaries:` metadata
#                                fdroidserver itself byte-compares against the
#                                published reference), then the CI's post-build
#                                sequence (throwaway-key sign when no
#                                .binary.apk, build-contains-signatures,
#                                publish when signatures/ exist, audit-gradle)
#                                and the check-apk gates (fdroid scanner
#                                --exit-code, signing-key DN, manifest report,
#                                ABI/R8 report).
#
# The MR CI jobs that cannot apply to a single-app scratch are documented in
# tools/fdroid/README.md ("MR CI parity" table): fdroid lint config, YAML 1.2
# (config.yml only), strip EXIF / repo/index.html / weblate merge conflict /
# PUBLISH (config.yml or image-file triggers), checkupdates_filter (bot only),
# check_git_repos (schedule only).
set -euo pipefail

FDROIDSERVER_COMMIT=${FDROIDSERVER_COMMIT:-a35fdfddd9c66823987a410566a6101186e39c84}
fdroidserver=/build/fdroidserver
FDROIDDATA=${FDROIDDATA:-/home/vagrant/fdroiddata}
REFDATA=/fdroiddata-ref
# fdroid-bootstrap-buildserver commit the MR CI pins for the production
# buildserver gitconfig.
BOOTSTRAP_BUILDSERVER=b33507043e6750065745dd2d8c35d3bb5c633c11

# The mounted repos are owned by the host uid; git refuses "dubious ownership"
# for root without this. (Re-applied after the bootstrap gitconfig overwrites
# the global file in the build phase.)
[ -d "$HOME" ] || mkdir -p "$HOME"
git config --global --add safe.directory '*' 2>/dev/null || true

if [ ! -d "$fdroidserver" ]; then
  git clone -q https://gitlab.com/fdroid/fdroidserver.git "$fdroidserver"
  git -C "$fdroidserver" -c advice.detachedHead=false checkout -q "$FDROIDSERVER_COMMIT"
fi
export PATH="$fdroidserver:$PATH"
export PYTHONPATH="$fdroidserver:$fdroidserver/examples"
export PYTHONUNBUFFERED=true
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/home/vagrant/.gradle

# A scratch fdroiddata normally carries fdroiddata's own config.yml (staged by
# verify-repro.sh, like the CI checkout). Only when it doesn't, fall back to a
# near-empty config.py — fdroidserver fills every unset key with its built-in
# default (same mechanism as the annotated examples/config.py).
if [ ! -f "$FDROIDDATA/config.yml" ] && [ ! -f "$FDROIDDATA/config.py" ]; then
  cat > "$FDROIDDATA/config.py" <<'PY'
# Written by buildserver-run.sh: deliberately near-empty — fdroidserver fills
# every unset key with its built-in default. See
# /build/fdroidserver/examples/config.py for the annotated full set.
PY
fi

echo "=== fdroidserver $(git -C "$fdroidserver" rev-parse --short HEAD) | $(java -version 2>&1 | sed -n 2p)"

phase=${1:-}
case "$phase" in
  --deps|--meta-ci|--readmeta)
    shift
    build=${1:?usage: buildserver-run.sh [--deps|--meta-ci|--readmeta] <appid:vercode>}
    ;;
  *)
    build=${phase:?usage: buildserver-run.sh [--deps|--meta-ci|--readmeta] <appid:vercode>}
    phase=build
    ;;
esac
appid=${build%%:*}
vercode=${build##*:}

# ----------------------------------------------------------------------------
# CI-JOB PARITY: fdroid build (pre-build env setup)
# ----------------------------------------------------------------------------
build_env_setup() {
  # CI: `apt-get update; apt-get dist-upgrade` — keep the system like the
  # runner the reference binaries come from.
  apt-get update -q
  apt-get -qy dist-upgrade

  # CI: "These packages are needed to make this env like the production
  # buildserver."  `sdkmanager "platform-tools" "build-tools;31.0.0"`
  sdkmanager --install "platform-tools" "build-tools;31.0.0" >/dev/null 2>&1 \
    || echo "NOTE: sdkmanager extras failed — deviating from the MR CI env"

  # CI build job: `git -C $home_vagrant/gradlew-fdroid pull`
  git -C /home/vagrant/gradlew-fdroid pull >/dev/null 2>&1 || true

  # CI: per-build `apt-get install -y openjdk-21-jdk-headless` +
  # `update-alternatives --set java .../java-21-openjdk-amd64/bin/java`
  apt-get install -y openjdk-21-jdk-headless
  # check-apk job dependencies (CI gets them via the deb fdroidserver package
  # in its own image: androguard for the manifest, binutils for strings)
  apt-get install -y --no-install-recommends androguard binutils \
    || echo "NOTE: androguard/binutils unavailable — manifest check will skip"
  if [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
    update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java || true
  else
    echo "NOTE: no java-21-openjdk-amd64 path — keeping image default JDK"
  fi

  # CI: production buildserver gitconfig, cloned and pinned to
  # $BOOTSTRAP_BUILDSERVER, installed as ~/.gitconfig. The file's fsck
  # skipList entries point at ~/fdroiddata/config/*.skipList (present on the
  # production buildserver) — create empty ones so the config stays valid.
  mkdir -p "$HOME/fdroiddata/config"
  touch "$HOME/fdroiddata/config/"{fsck,fetch.fsck,receive.fsck,transfer.fsck}.skipList
  curl -sfL "https://gitlab.com/fdroid/fdroid-bootstrap-buildserver/-/raw/$BOOTSTRAP_BUILDSERVER/roles/production_hardening/files/gitconfig" \
    -o "$HOME/.gitconfig" \
    || echo "NOTE: bootstrap gitconfig fetch failed — keeping default git config"
  # Deviation from CI: our scratch repos are host-owned and mounted in, so
  # "dubious ownership" protection must stay off (CI runs same-uid).
  git config --global --add safe.directory '*'
}

apksigner_bin() {
  ls /opt/android-sdk/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1
}

# ----------------------------------------------------------------------------
# CI-JOB PARITY: fdroid build (post-build) + check apk
# ----------------------------------------------------------------------------
post_ci() {
  local build_rc=$1
  local tmp_apk="$FDROIDDATA/tmp/${appid}_${vercode}.apk"
  local binary_apk="$FDROIDDATA/tmp/binaries/${appid}_${vercode}.binary.apk"
  local signed_apk="$FDROIDDATA/tmp/signed/${appid}_${vercode}.signed.apk"
  local EXITVALUE=0
  gate() { EXITVALUE=1; printf '\033[31mPOST-CI GATE FAIL: %s\033[0m\n' "$*"; }

  echo "===== POST-BUILD: MR CI build-job tail + check-apk gates ====="
  local apk_for_checks=""
  if [ -e "$binary_apk" ]; then
    echo "REPRODUCIBLE-SUCCESS: fdroidserver produced ${binary_apk#$FDROIDDATA/}"
    echo "  (reference signature copied onto the rebuild and apksigner-validated)"
    apk_for_checks=$binary_apk
  elif [ -e "$tmp_apk" ]; then
    apk_for_checks=$tmp_apk
    # CI build job: no .binary.apk → sign with a throwaway key so reviewers
    # get an installable APK. Exact CI commands (PASS=111111, RSA 2048).
    if [ ! -e "$signed_apk" ]; then
      mkdir -p "$FDROIDDATA/tmp/signed"
      local ks pass=111111
      ks=$(mktemp -d)/keystore.jks
      echo -e "$pass\n$pass\ny\ny\ny\ny\ny\ny\ny" \
        | keytool -genkey -keyalg RSA -keystore "$ks" -keysize 2048 2> /dev/null
      "$(apksigner_bin)" sign --ks "$ks" --in "$tmp_apk" --out "$signed_apk"
    fi
    echo "throwaway-signed APK: tmp/signed/${appid}_${vercode}.signed.apk (CI behavior for non-reproducible results)"
  fi

  # CI build job tail: `./tools/build-contains-signatures.py $build || continue;
  # $fdroid publish --verbose --error-on-failed $build`. With Binaries:
  # metadata (no signatures/ dir) the script exits 1 with "no signatures found,
  # skipping publish" and CI skips publish — mirror that outcome exactly.
  echo "===== build-contains-signatures / publish ====="
  local bcs_out bcs_rc=0
  bcs_out=$(python3 "$REFDATA/tools/build-contains-signatures.py" "$build" 2>&1) || bcs_rc=$?
  printf '%s\n' "$bcs_out"
  if [ "$bcs_rc" -eq 0 ]; then
    # signatures exist → CI runs publish with the fdroidserver test keystore
    export keystorepass='r9aquRHYoI8+dYz6jKrLntQ5/NJNASFBacJh7Jv2BlI='
    export keypass='r9aquRHYoI8+dYz6jKrLntQ5/NJNASFBacJh7Jv2BlI='
    export keystore="$fdroidserver/tests/keystore.jks"
    keytool -changealias -alias sova -destalias ciarang \
      -keystore "$keystore" -storepass:env keystorepass -keypass:env keypass
    fdroid publish --verbose --error-on-failed "$build"
  elif grep -q "no signatures found" <<<"$bcs_out"; then
    echo "SKIP-PUBLISH: no signatures/ dir (Binaries: metadata) — CI likewise skips publish"
  else
    echo "NOTE: build-contains-signatures crashed (rc=$bcs_rc) — deviating, not gating"
  fi

  # CI build job tail: `./tools/audit-gradle.py $CHANGED` — fails on plain-HTTP
  # gradle repository blocks in the built source tree. fdroidserver deletes
  # build/<appid> after a successful --on-server build, making this a no-op in
  # CI too; when the tree survives (failed compare) it scans.
  echo "===== audit-gradle ====="
  if [ -d "$FDROIDDATA/build/$appid" ]; then
    if python3 "$REFDATA/tools/audit-gradle.py" "$appid"; then
      echo "audit-gradle: PASS"
    else
      gate "audit-gradle (plain-HTTP gradle repository URLs)"
    fi
  else
    echo "audit-gradle: SKIPPED (build/$appid deleted after build — CI script likewise no-ops)"
  fi

  # CI-JOB check apk. Only two checks set EXITVALUE in CI: the binary scanner
  # and a debug-key signature on a .binary.apk; manifest/ABI/R8 produce
  # codequality reports for human review only — mirrored as prints.
  echo "===== CI-JOB check apk ====="
  if [ -n "$apk_for_checks" ]; then
    # CI installs build-tools;36.0.0 for this job's apksigner
    sdkmanager --install "build-tools;36.0.0" >/dev/null 2>&1 || true
    local scanner_rc=0
    set +e
    fdroid scanner --verbose --exit-code "$apk_for_checks" 2>&1 | tee /tmp/scanner-result
    scanner_rc=$?
    set -e
    if [ "$scanner_rc" -eq 0 ]; then
      echo "scanner: PASS"
    else
      gate "fdroid scanner --exit-code (KnownVulnerable / Dependency-metadata signing block)"
      sed -n "s/.*DEBUG: Problem: found class '\(.*\)'/  found class: \1/p" /tmp/scanner-result || true
    fi

    if [ -e "$binary_apk" ]; then
      local dn
      dn=$("$(apksigner_bin)" verify --print-certs "$binary_apk" 2>/dev/null \
        | sed -n -E 's/.*certificate DN: (.*)/\1/p' || true)
      if [ "$dn" = "C=US, O=Android, CN=Android Debug" ]; then
        gate "signing key is the Android DEBUG key"
      else
        echo "signing key DN: ${dn:-<none extracted>} — not the debug key: PASS"
      fi
    fi

    if androguard axml "$apk_for_checks" -o /tmp/AndroidManifest.xml >/dev/null 2>&1; then
      grep 'android:usesCleartextTraffic="true"' /tmp/AndroidManifest.xml \
        && echo "  WOULD-REPORT (major): Cleartext Traffic Permitted" || true
      grep 'android:debuggable="true"' /tmp/AndroidManifest.xml \
        && echo "  WOULD-REPORT (critical): Debuggable APK" || true
      grep 'android:testOnly="true"' /tmp/AndroidManifest.xml \
        && echo "  WOULD-REPORT (critical): Testing APK" || true
      echo "manifest: checked (cleartext/debuggable/testOnly — CI reports, does not gate)"
    else
      echo "manifest: SKIPPED (androguard axml unavailable — CI runs it in its own image)"
    fi

    local tempd
    tempd=$(mktemp -d)
    unzip -q "$apk_for_checks" -d "$tempd" || true
    if [ -e "$tempd/lib/" ]; then
      echo "ABI + size: $(ls "$tempd/lib/" | tr '\n' ' ')| $(ls -lh "$apk_for_checks" | awk '{print $5}')"
    else
      echo "ABI + size: no native libs | $(ls -lh "$apk_for_checks" | awk '{print $5}')"
    fi
    if unzip -qo "$apk_for_checks" classes.dex -d "$tempd" 2>/dev/null; then
      if strings "$tempd/classes.dex" | grep -q '~~R8'; then
        echo "R8 marker: present"
      else
        echo "R8 marker: absent (R8 not used)"
      fi
    fi
    rm -rf "$tempd"
  else
    echo "check apk: SKIPPED (no built APK retained in tmp/)"
  fi

  echo "===== POST-CI SUMMARY ====="
  if [ "$EXITVALUE" -ne 0 ]; then
    echo "POST-CI: GATE FAILURES ABOVE"
  else
    echo "POST-CI: ALL GATES PASS"
  fi
  return "$EXITVALUE"
}

# ----------------------------------------------------------------------------
# phases
# ----------------------------------------------------------------------------
case "$phase" in
  --deps)
    # Kept for manual use: packages the MR CI's test jobs install in their own
    # images. verify-repro.sh's meta-check installs these inline in the
    # --meta-ci phase instead (containers are ephemeral — a separate deps
    # container would leave nothing behind for the suite container).
    apt-get update -q
    apt-get install -qy --no-install-recommends exiftool jq python3-yaml \
      python3-markdown-it python3-venv ca-certificates curl
    python3 -m venv /opt/check-jsonschema-venv
    /opt/check-jsonschema-venv/bin/pip install --quiet check-jsonschema
    echo "=== deps installed"
    ;;

  --meta-ci)
    # MR CI test jobs, run against: scratch fdroiddata (fdroid commands) and
    # the fdroiddata ref clone + our metadata file (whole-repo tools scripts).
    # Each CI test job installs its own packages in a fresh image — mirror
    # that here, in this one container, before the suite.
    apt-get update -q
    apt-get install -qy --no-install-recommends exiftool jq python3-yaml \
      python3-markdown-it python3-venv ca-certificates curl
    # CI: `image: python; pip install check-jsonschema` — the buildserver
    # image has no pip module, so isolate in a venv (ephemeral, per run).
    # NEVER put the venv on PATH: its python3 shadows the system one and
    # loses fdroidserver's site-packages.
    python3 -m venv /opt/check-jsonschema-venv
    /opt/check-jsonschema-venv/bin/pip install --quiet check-jsonschema

    cd "$FDROIDDATA"
    EXITVALUE=0
    err() { EXITVALUE=1; printf '\033[31mERROR %s\033[0m\n' "$*" >&2; }

    # Drop our staged metadata into the ref clone: the MR branch is
    # fdroiddata master + our file COMMITTED, and the diff-based gates must
    # see exactly that baseline (tools' own edits are what diff reveals).
    cp "metadata/$appid.yml" "$REFDATA/metadata/$appid.yml"
    git -C "$REFDATA" add "metadata/$appid.yml"
    git -C "$REFDATA" -c user.name=fdroid-verify -c user.email=fdroid-verify@localhost \
      commit -q -m "verify-repro: add $appid metadata (MR simulation)"

    echo "===== CI-JOB fdroid lint ====="
    if fdroid lint "$appid"; then
      echo "fdroid lint: PASS"
    else
      err "fdroid lint failed (CI job 'fdroid lint')"
    fi

    echo "===== CI-JOB fdroid rewritemeta ====="
    if fdroid rewritemeta "$appid" && git diff --exit-code; then
      echo "fdroid rewritemeta: PASS (metadata is in fdroidserver canonical form)"
    else
      err "rewritemeta diff — metadata is NOT in canonical form (CI job 'fdroid rewritemeta')"
      git --no-pager diff || true
    fi

    echo "===== CI-JOB schema validation ====="
    if /opt/check-jsonschema-venv/bin/check-jsonschema \
         --check-metaschema "$REFDATA/schemas/metadata.json" \
       && /opt/check-jsonschema-venv/bin/check-jsonschema \
         --schemafile "$REFDATA/schemas/metadata.json" "metadata/$appid.yml"; then
      echo "schema validation: PASS"
    else
      err "schema validation failed (CI job 'schema validation')"
    fi

    echo "===== CI-JOB checkupdates ====="
    if fdroid checkupdates --auto -v "$appid" \
       && git --no-pager diff --color=always --exit-code; then
      echo "checkupdates: PASS (UpdateCheckData/VercodeOperation resolve; no metadata drift)"
    else
      err "checkupdates failed or modified metadata (CI job 'checkupdates')"
    fi

    echo "===== CI-JOB git redirect ====="
    if (cd "$REFDATA" && python3 tools/rewrite-git-redirects.py "$appid" \
        && git diff --exit-code "metadata/$appid.yml"); then
      echo "git redirect: PASS (Repo: URL is direct, not a redirect)"
    else
      err "git URLs should be direct, not redirects! (CI job 'git redirect')"
    fi

    echo "===== CI-JOB tools check scripts ====="
    for t in check-exif-in-images.sh check-localized-metadata.py \
             check-keyalias-collision.py check-metadata-summary-whitespace.py \
             check-for-unattached-signatures.py make-summary-translatable.py; do
      if (cd "$REFDATA" && "./tools/$t"); then
        echo "tools/$t: PASS"
      else
        err "tools/$t failed (CI job 'tools check scripts')"
      fi
    done

    echo "===== CI-JOB check source code (check-fastlane) ====="
    # Report-only in CI (codequality artifact for human review); a crash here
    # is our tooling, not a metadata failure.
    if python3 "$REFDATA/tools/check-fastlane.py" "$appid" > /tmp/fastlane-reports.json; then
      echo "check-fastlane: ran — reports reviewers would see:"
      python3 - <<'PY'
import json
for r in json.load(open('/tmp/fastlane-reports.json')):
    print(f"  [{r.get('severity')}] {r.get('description')}")
PY
    else
      err "check-fastlane crashed (CI job 'check source code')"
    fi

    echo "===== META-CI SUMMARY ====="
    if [ "$EXITVALUE" -eq 0 ]; then
      echo "META-CI: ALL CI JOB GATES PASS"
    else
      echo "META-CI: FAILURES ABOVE"
    fi
    exit "$EXITVALUE"
    ;;

  --readmeta)
    fdroid readmeta
    echo "=== readmeta exit: $?"
    ;;

  build)
    build_env_setup
    cd "$FDROIDDATA"
    # CI build job: `$fdroid fetchsrclibs $build --verbose`
    fdroid fetchsrclibs "$build" --verbose
    # CI build job: `(unset CI; $fdroid build --verbose --test --refresh-scanner --on-server --no-tarball $build)`
    build_rc=0
    (unset CI; fdroid build --verbose --test --refresh-scanner --on-server --no-tarball "$build") || build_rc=$?
    echo "=== fdroid build exit: $build_rc"
    # CI runs the post-build sequence only after a successful build; for a
    # failed Binaries compare (expected for unpublished commits) the retained
    # tmp/ APK still goes through the check-apk gates — beyond-CI, additive.
    post_rc=0
    post_ci "$build_rc" || post_rc=$?
    exit "$((build_rc != 0 ? build_rc : post_rc))"
    ;;
esac
