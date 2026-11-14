#!/usr/bin/env bash
#
# verify-repro.sh — run the REAL F-Droid fdroiddata MR CI locally.
#
# Stages a scratch fdroiddata from the metadata draft (docs/fdroid/<appid>.yml)
# and replicates the fdroiddata MR CI (gitlab.com/fdroid/fdroiddata
# .gitlab-ci.yml) inside the official buildserver Docker image, fdroidserver at
# the CI's pin:
#
#   meta-check — the MR CI *test* jobs: fdroid lint, fdroid rewritemeta,
#       schema validation (check-jsonschema + fdroiddata's metadata schema),
#       checkupdates --auto (UpdateCheckData/VercodeOperation resolution),
#       git redirect, the six fdroiddata tools scripts (run against a sparse
#       fdroiddata master clone + our metadata file, exactly like the MR
#       branch), and check-fastlane. Minutes, not hours.
#   env-check  — the MR CI *build* job: `fdroid build --test` of the TAG commit
#       cloned from the public remote. With `Binaries:` metadata fdroidserver
#       itself downloads the published reference APK and byte-compares it
#       (apksigcopier + apksigner validate) — the exact gate F-Droid applies
#       to every release. MUST end in PASS.
#   local      — the same build job for a (usually pre-merge) commit. An
#       UNPUBLISHED commit can never byte-match: AGP stamps the built commit
#       into META-INF/version-control-info.textproto. The verdict logic
#       accounts for that.
#   clean      — drop the work dir, the fdroiddata ref clone and the docker
#       Gradle volumes.
#
# The build job's post-build sequence and the dependent check-apk job are
# replicated too: throwaway-key sign when no .binary.apk was produced,
# build-contains-signatures (+ publish when signatures/ exist), audit-gradle,
# fdroid scanner --exit-code, signing-key DN, manifest/ABI/R8 reports. See
# docs/fdroid/README.md §6 and docs/adr/0014-fdroid-reproducible-binaries.md.
#
# Everything machine-specific is derived at runtime (repo root, GitHub slug
# from `git remote get-url origin`, app id from the draft filename, base
# versionCode from version.properties) — the script hardcodes no user, host
# path or repository name.
#
# Requires: docker access (docker group), git. A real build takes ~35 min and
# ~5 GB image + ~4 GB Gradle volume; env-check and local MUST use separate
# Gradle volumes (identical fdroid-graph inputs + a shared cache would make
# the compare vacuously pass on a build-cache replay).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER=buildserver-run.sh
IMAGE=${FDROID_BUILD_IMAGE:-registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie}
WORK=${FDROID_VERIFY_HOME:-${XDG_CACHE_HOME:-$HOME/.cache}/fdroid-verify}
REF="$WORK/fdroiddata-ref"

usage() {
  cat >&2 <<'EOF'
usage: tools/fdroid/verify-repro.sh <mode> [flags]

modes:
  meta-check --tag vX.Y.Z                  minutes: ALL fdroiddata MR CI *test* jobs
                                           (lint, rewritemeta, schema, checkupdates,
                                           git redirect, tools scripts, check-fastlane)
  env-check  --tag vX.Y.Z [--vercode N]    build the TAG from the public remote; must byte-match
                                           the published reference + pass the check-apk gates
  local      --tag vX.Y.Z [--commit SHA]   build a local commit; compare vs the published reference of --tag
             [--vercode N] [--local]       (PASS-EXPECTED-DELTA = only AGP's VCS stamp differs → safe)
  clean                                    remove work dir + fdroiddata ref clone + docker Gradle volumes

flags:
  --tag X.Y.Z    published release tag the reference binaries are attached to (required)
  --vercode N    versionCode to build (default: version.properties base + 1 = arm64-v8a)
  --commit SHA   commit for `local` mode (default HEAD)
  --local        `local` mode: force building from this checkout via file:// even if the commit is pushed
  --work DIR     work dir (default: $FDROID_VERIFY_HOME or ~/.cache/fdroid-verify)

env:
  FDROID_BUILD_IMAGE   buildserver image (default registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie)
  FDROIDSERVER_COMMIT  fdroidserver pin inside the container (default: the fdroiddata MR CI pin)
EOF
  exit 2
}

die() { echo "verify-repro: $*" >&2; exit 1; }

mode=${1:-}
[ -n "$mode" ] || usage
shift || true
case "$mode" in
  meta-check|env-check|local|clean) ;;
  *) usage ;;
esac

tag="" vercode_arg="" commit_arg="" force_local=0
while [ $# -gt 0 ]; do
  case "$1" in
    --tag)    [ $# -ge 2 ] || usage; tag=$2; shift 2 ;;
    --tag=*)  tag=${1#*=}; shift ;;
    --vercode) [ $# -ge 2 ] || usage; vercode_arg=$2; shift 2 ;;
    --vercode=*) vercode_arg=${1#*=}; shift ;;
    --commit) [ $# -ge 2 ] || usage; commit_arg=$2; shift 2 ;;
    --commit=*) commit_arg=${1#*=}; shift ;;
    --local)  force_local=1; shift ;;
    --work)   [ $# -ge 2 ] || usage; WORK=$2; shift 2 ;;
    --work=*) WORK=${1#*=}; shift ;;
    -h|--help) usage ;;
    *) usage ;;
  esac
done
REF="$WORK/fdroiddata-ref"

command -v git >/dev/null || die "git not found"
command -v docker >/dev/null || die "docker not found (docker group membership required)"

# --- repo-derived facts (no hardcoded names/paths) --------------------------

DRAFT="$ROOT/docs/fdroid"
draft_count=$(find "$DRAFT" -maxdepth 1 -name '*.yml' | wc -l)
[ "$draft_count" -eq 1 ] || die "expected exactly one metadata draft in $DRAFT, found $draft_count"
DRAFT_FILE=$(find "$DRAFT" -maxdepth 1 -name '*.yml')
APPID=$(basename "$DRAFT_FILE" .yml)

origin=$(git -C "$ROOT" remote get-url origin 2>/dev/null) \
  || die "no origin remote in $ROOT"
GH_SLUG=$(printf '%s' "$origin" | sed -E 's#^(https?://|git@)github\.com[:/]##; s#\.git$##')
case "$GH_SLUG" in */*) ;; *) die "origin is not a github remote: $origin" ;; esac

base_vercode=$(sed -n 's/^jellyplay\.versionCode=\([0-9]*\)$/\1/p' "$ROOT/version.properties")
[ -n "$base_vercode" ] || die "could not read jellyplay.versionCode from version.properties"

# --- mode setup --------------------------------------------------------------

# rm_work_dir <dir> — remove a work dir even when a previous container run
# left root-owned files inside it (the container runs as root; the host user
# cannot unlink those). Falls back to a throwaway container for the removal.
rm_work_dir() {
  local dir=$1
  rm -rf "$dir" 2>/dev/null || true
  if [ -e "$dir" ]; then
    echo "== root-owned leftovers from a container run — removing via a throwaway container"
    docker run --rm -v "$(dirname "$dir"):/w" "$IMAGE" rm -rf -- "/w/$(basename "$dir")"
  fi
}

if [ "$mode" = clean ]; then
  echo "removing docker volumes (Gradle homes) and $WORK"
  # names mirror docker_run's "fdroid-verify-gradle-$mode" for env-check/local
  docker volume rm -f fdroid-verify-gradle-env-check fdroid-verify-gradle-local >/dev/null 2>&1 || true
  rm_work_dir "$WORK"
  echo "clean done"
  exit 0
fi

[ -n "$tag" ] || usage

if ! git -C "$ROOT" rev-parse -q --verify "refs/tags/$tag^{commit}" >/dev/null; then
  git -C "$ROOT" fetch -q origin "tag $tag" --no-tags \
    || die "tag $tag not found locally and fetch failed"
fi
tag_commit=$(git -C "$ROOT" rev-parse "refs/tags/$tag^{commit}")

vercode=${vercode_arg:-$((base_vercode + 1))}
grep -q "versionCode: $vercode\$" "$DRAFT_FILE" \
  || die "draft $DRAFT_FILE has no Build for versionCode $vercode (draft pair is arm64 base+1 / x86_64 base+2)"

log="$WORK/logs/$mode-$vercode.log"
data="$WORK/fdroiddata-$mode"
mkdir -p "$WORK/logs" "$data"

echo "== appid:      $APPID"
echo "== github:     $GH_SLUG"
echo "== tag:        $tag ($tag_commit)"
echo "== vercode:    $vercode"
echo "== image:      $IMAGE"
echo "== work dir:   $WORK"

# stage_data <dest> <commit> <repo-url>
# Copies the metadata draft with comment lines stripped, rewrites every
# `commit:` (both Builds share one hash by convention) and `Repo:`, then
# git-commits it: fdroidserver's SOURCE_DATE_EPOCH fallback reads the
# fdroiddata git log. The epoch value is proven byte-irrelevant (the env-check
# run passes with a different epoch than the reference was published with).
stage_data() {
  local dest=$1 commit=$2 repourl=$3
  # Fresh scratch every run: a leftover dir from a previous run breaks the
  # commit step (identical content = nothing to commit) and may carry
  # root-owned files from a container phase.
  rm_work_dir "$dest"
  mkdir -p "$dest/metadata"
  grep -v '^[[:space:]]*#' "$DRAFT_FILE" \
    | sed -E -e "s/^([[:space:]]*)commit:.*/\\1commit: $commit/" \
             -e "s|^Repo: .*|Repo: $repourl|" \
    > "$dest/metadata/$APPID.yml"
  # The CI runs every job inside the full fdroiddata checkout: mirror its
  # config sources — root config.yml (repo settings; secrets are env-based),
  # config/ (Categories/AntiFeatures validation data + icons) and .gitignore
  # (checkupdates refuses on `git status --porcelain`, and it creates
  # build/ tmp/ logs/ — fdroiddata's .gitignore is what keeps those from
  # counting as uncommitted changes).
  if [ -f "$REF/config.yml" ]; then cp "$REF/config.yml" "$dest/config.yml"; fi
  if [ -d "$REF/config" ]; then cp -R "$REF/config" "$dest/config"; fi
  if [ -f "$REF/.gitignore" ]; then cp "$REF/.gitignore" "$dest/.gitignore"; fi
  # Fallback when the ref clone carries no config.yml: a near-empty config.py
  # — fdroidserver fills every unset key with its built-in default. Committed
  # either way so fdroidserver's "uncommitted changes" guard (checkupdates)
  # sees a clean fdroiddata, like the MR branch does.
  if [ ! -f "$dest/config.yml" ]; then
    cat > "$dest/config.py" <<'PY'
# Written by verify-repro.sh: deliberately near-empty — fdroidserver fills
# every unset key with its built-in default. See the annotated
# fdroidserver/examples/config.py for the full set.
PY
  fi
  git -C "$dest" init -q -b main
  git -C "$dest" add metadata
  if [ -e "$dest/.gitignore" ]; then git -C "$dest" add .gitignore; fi
  if [ -e "$dest/config.yml" ]; then git -C "$dest" add config.yml; fi
  if [ -e "$dest/config" ]; then git -C "$dest" add config; fi
  if [ -e "$dest/config.py" ]; then git -C "$dest" add config.py; fi
  git -C "$dest" -c user.name=fdroid-verify -c user.email=fdroid-verify@localhost \
    commit -q -m "scratch metadata for verify-repro"
  echo "== staged scratch fdroiddata: $dest (commit: $commit, repo: $repourl)"
}

# ensure_ref_clone — sparse clone of fdroiddata master holding the pieces the
# MR CI jobs run FROM the MR branch itself: tools/*.py|sh, schemas/metadata.json,
# config/*.yml (Categories/AntiFeatures validation source) and all top-level
# metadata ymls (whole-repo tools like the keyalias collision check must see
# the full set). Refreshed to origin/master on every run — the MR CI
# validates against master too.
ensure_ref_clone() {
  local sparse=('metadata/*.yml' "metadata/$APPID/**" 'tools/*' 'schemas/*' 'config/*' 'config.yml' '.gitignore')
  # flock: parallel env-check + local runs share one ref clone.
  (
  flock 9
  if [ ! -d "$REF/.git" ]; then
    echo "== sparse-cloning fdroiddata master (tools/ schemas/ config/ metadata/*.yml) — first run only"
    git clone -q --depth 1 --filter=blob:none --no-checkout --branch master \
      https://gitlab.com/fdroid/fdroiddata.git "$REF"
    git -C "$REF" sparse-checkout set --no-cone "${sparse[@]}"
    git -C "$REF" checkout -q master
  else
    git -C "$REF" fetch -q --depth 1 origin master
    git -C "$REF" sparse-checkout set --no-cone "${sparse[@]}"
    git -C "$REF" reset -q --hard origin/master
    git -C "$REF" sparse-checkout reapply >/dev/null 2>&1 || true
  fi
  ) 9>"$WORK/ref.lock"
}

# docker_run <runner-args...>
docker_run() {
  local vol_args=() mount_args=()
  if [ "$mode" != meta-check ]; then
    # Per-mode Gradle volume: env-check and local have identical fdroid-graph
    # inputs, so a SHARED cache would let the second run replay cached outputs
    # and pass vacuously. Separate volumes force full re-execution.
    vol_args=(-v "fdroid-verify-gradle-$mode:/home/vagrant/.gradle")
  fi
  if [ -n "${repo_mount:-}" ]; then
    mount_args+=(-v "$ROOT:/jellyplay-src:ro")
  fi
  if [ "$mode" = meta-check ]; then
    # tools scripts WRITE (JSON reports, Summary migration) → rw.
    # The container runs as root (like the CI's jobs); verify-repro hands the
    # ref clone's ownership back afterwards.
    mount_args+=(-v "$REF:/fdroiddata-ref")
  else
    # build phase only READS the tools scripts → ro
    mount_args+=(-v "$REF:/fdroiddata-ref:ro")
  fi
  echo "== docker run ($([ "$mode" = meta-check ] && echo "MR CI test jobs ≈ 10 min first run" || echo "real build ≈ 35 min"))"
  set +e
  docker run --rm \
    -v "$data:/home/vagrant/fdroiddata" \
    "${vol_args[@]}" "${mount_args[@]}" \
    -v "$TOOLS_DIR:/verify-tools:ro" \
    "$IMAGE" bash "/verify-tools/$RUNNER" "$@"
  rc=$?
  set -e
  return "$rc"
}

build_verdict() {
  local log=$1
  local gates
  gates=$(grep -c "POST-CI GATE FAIL" "$log" || true)
  if grep -q "compared built binary to supplied reference binary successfully" "$log" \
     || grep -qE '[1-9][0-9]* build succeeded' "$log"; then
    if [ "$gates" -gt 0 ]; then
      echo "VERDICT: FAIL — build/byte-compare succeeded but $gates post-CI gate(s) failed:"
      grep "POST-CI GATE FAIL" "$log"
      return 1
    fi
    if grep -q "REPRODUCIBLE-SUCCESS" "$log"; then
      echo "VERDICT: PASS — fdroidserver produced the .binary.apk (reference signature copied + validated)"
      echo "  and the byte-compare succeeded: this machine IS the F-Droid buildserver."
    else
      echo "VERDICT: PASS — fdroidserver byte-validated the build against the published reference binaries"
    fi
    return 0
  fi
  local detail rest
  # fdroidserver's verbose build failure wraps the error in its own markers
  # (exception.py __str__: "==== detail begin/end ====") and the detail text
  # carries compare boilerplate (common.py compare_apks: "verification of APK
  # with copied signature failed", "Comparing reference APK to …APK with
  # copied signature...", "Unexpected diff output:") plus diff -r scaffolding
  # (hunk headers, --- separator). Strip all of it; what survives must be
  # EMPTY except for textproto/revision lines — i.e. the whole APK differs
  # only in AGP's VCS stamp. Any other differing file or field leaves lines
  # behind and falls through to FAIL.
  detail=$(awk '/==== detail begin ====/{f=1;next}/==== detail end ====/{f=0}f' "$log")
  rest=$(grep -vE \
    -e 'version-control-info\.textproto' \
    -e '^[<>][[:space:]]+revision:' \
    -e '^verification of APK with copied signature failed$' \
    -e '^Comparing reference APK to (unsigned )?APK with copied signature\.\.\.$' \
    -e '^Unexpected diff output:$' \
    -e '^[0-9]+([,][0-9]+)?[acd][0-9]+([,][0-9]+)?$' \
    -e '^---$' \
    <<<"$detail" || true)
  if [ "$gates" -eq 0 ] \
     && [ -n "$detail" ] \
     && grep -q "version-control-info.textproto" <<<"$detail" \
     && grep -qE '^[<>][[:space:]]+revision:' <<<"$detail" \
     && [ -z "$rest" ]; then
    echo "VERDICT: PASS-EXPECTED-DELTA — the ONLY difference is AGP's VCS stamp in"
    echo "  META-INF/version-control-info.textproto (built commit ≠ published commit)."
    echo "  Every other byte (dex/resources/libs) is identical: this commit is safe for"
    echo "  F-Droid reproducible builds. After the tag is published, metadata's commit:"
    echo "  will equal the built commit and env-check-style parity holds (that case is"
    echo "  exactly what env-check proves)."
    return 0
  fi
  echo "VERDICT: FAIL — log: $log"
  if [ "$gates" -gt 0 ]; then
    echo "--- post-CI gate failures ---"
    grep "POST-CI GATE FAIL" "$log"
  fi
  if [ -n "$detail" ]; then
    echo "--- fdroidserver diff detail ---"
    printf '%s\n' "$detail"
  else
    echo "--- log tail ---"
    tail -30 "$log"
  fi
  return 1
}

meta_verdict() {
  local log=$1
  if grep -q "META-CI: ALL CI JOB GATES PASS" "$log"; then
    echo "VERDICT: META-CHECK OK — every fdroiddata MR CI test job replicated here is green (log: $log)"
    return 0
  fi
  echo "VERDICT: META-CHECK FAIL — these MR CI job gates failed (log: $log):"
  grep -E "ERROR .*CI job|META-CI" "$log" || tail -30 "$log"
  return 1
}

case "$mode" in
  meta-check)
    ensure_ref_clone
    stage_data "$data" "$tag_commit" "https://github.com/$GH_SLUG.git"
    if docker_run --meta-ci "$APPID:$vercode" 2>&1 | tee "$log"; then
      :
    fi
    # The suite ran as root: give the ref clone back to the invoking user so
    # host-side refreshes stay possible.
    docker run --rm -v "$REF:/fix" "$IMAGE" chown -R "$(id -u):$(id -g)" /fix
    meta_verdict "$log"
    ;;
  env-check)
    ensure_ref_clone
    stage_data "$data" "$tag_commit" "https://github.com/$GH_SLUG.git"
    repo_mount=""
    if docker_run "$APPID:$vercode" 2>&1 | tee "$log"; then
      :
    fi
    build_verdict "$log"
    ;;
  local)
    commit=${commit_arg:-HEAD}
    commit=$(git -C "$ROOT" rev-parse "$commit^{commit}")
    git -C "$ROOT" fetch -q origin >/dev/null 2>&1 \
      || echo "warn: git fetch failed; using local refs for the push check" >&2
    repo_mount=""
    repourl="https://github.com/$GH_SLUG.git"
    if [ "$force_local" -eq 1 ] \
       || ! git -C "$ROOT" branch -r --contains "$commit" | grep -q .; then
      repo_mount=1
      repourl="file:///jellyplay-src"
      echo "== commit $commit is unpushed → building from a file:// mount of this checkout"
    fi
    ensure_ref_clone
    stage_data "$data" "$commit" "$repourl"
    if docker_run "$APPID:$vercode" 2>&1 | tee "$log"; then
      :
    fi
    build_verdict "$log"
    ;;
esac
