#!/usr/bin/env bash
#
# verify-repro.sh — run the REAL F-Droid reproducible-builds check locally.
#
# Stages a scratch fdroiddata from the metadata draft (docs/fdroid/<appid>.yml)
# and drives the same fdroidserver build the fdroiddata MR CI runs, inside the
# official buildserver Docker image. With `Binaries:` metadata fdroidserver
# downloads the published reference APK for the requested versionCode and
# byte-compares it against its own build (apksigcopier + apksigner validate) —
# the exact check F-Droid applies to every release. See docs/fdroid/README.md
# §6 and docs/adr/0014-fdroid-reproducible-binaries.md for why each side must
# transform the tree identically.
#
# Modes:
#   meta-check --tag vX.Y.Z
#       Stage the scratch fdroiddata and run `fdroid readmeta` in the
#       container. Seconds-fast; validates the draft as fdroidserver sees it.
#   env-check --tag vX.Y.Z [--vercode N]
#       Build the TAG commit cloned from the public remote and byte-compare
#       against the published reference binaries. MUST pass — proves this
#       machine faithfully reproduces the buildserver before any branch
#       verdict is trusted.
#   local --tag vX.Y.Z [--commit SHA] [--vercode N] [--local]
#       Build a local commit (default HEAD; unpushed commits automatically
#       switch `Repo:` to a file:// mount of this checkout) and compare
#       against the published reference of --tag. NOTE: an unpublished commit
#       can never byte-match — AGP stamps the built commit into
#       META-INF/version-control-info.textproto. Verdict logic accounts for
#       that: PASS-EXPECTED-DELTA (only the VCS stamp differs = payload
#       byte-identical → safe) is a green verdict; anything else fails.
#   clean
#       Remove the work dir and the docker Gradle-home volumes.
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

usage() {
  cat >&2 <<'EOF'
usage: tools/fdroid/verify-repro.sh <mode> [flags]

modes:
  meta-check --tag vX.Y.Z                  fast: scratch fdroiddata + `fdroid readmeta` in the container
  env-check  --tag vX.Y.Z [--vercode N]    build the TAG from the public remote; must byte-match the published reference
  local      --tag vX.Y.Z [--commit SHA]   build a local commit; compare vs the published reference of --tag
             [--vercode N] [--local]       (PASS-EXPECTED-DELTA = only AGP's VCS stamp differs → safe)
  clean                                    remove work dir + docker Gradle volumes

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

if [ "$mode" = clean ]; then
  echo "removing docker volumes (Gradle homes) and $WORK"
  docker volume rm -f fdroid-verify-gradle-envcheck fdroid-verify-gradle-local >/dev/null 2>&1 || true
  rm -rf "$WORK"
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
  mkdir -p "$dest/metadata"
  grep -v '^[[:space:]]*#' "$DRAFT_FILE" \
    | sed -E -e "s/^([[:space:]]*)commit:.*/\\1commit: $commit/" \
             -e "s|^Repo: .*|Repo: $repourl|" \
    > "$dest/metadata/$APPID.yml"
  git -C "$dest" init -q -b main
  git -C "$dest" add metadata
  git -C "$dest" -c user.name=fdroid-verify -c user.email=fdroid-verify@localhost \
    commit -q -m "scratch metadata for verify-repro" -- metadata
  echo "== staged scratch fdroiddata: $dest (commit: $commit, repo: $repourl)"
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
    mount_args=(-v "$ROOT:/jellyplay-src:ro")
  fi
  echo "== docker run (real build ≈ 35 min; meta-check ≈ 1 min)"
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

verdict() {
  local log=$1
  if grep -q "compared built binary to supplied reference binary successfully" "$log" \
     || grep -qE '[1-9][0-9]* build succeeded' "$log"; then
    echo "VERDICT: PASS — fdroidserver byte-validated the build against the published reference binaries"
    return 0
  fi
  local detail rest
  detail=$(awk '/==== detail begin ====/{f=1;next}/==== detail end ====/{f=0}f' "$log")
  rest=$(grep -vE 'version-control-info\.textproto|^[[:space:]]*revision:' <<<"$detail" || true)
  if [ -n "$detail" ] \
     && grep -q "version-control-info.textproto" <<<"$detail" \
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
  if [ -n "$detail" ]; then
    echo "--- fdroidserver diff detail ---"
    printf '%s\n' "$detail"
  else
    echo "--- log tail ---"
    tail -30 "$log"
  fi
  return 1
}

case "$mode" in
  meta-check)
    stage_data "$data" "$tag_commit" "https://github.com/$GH_SLUG.git"
    docker_run --readmeta "$APPID:$vercode" | tee "$log"
    echo "VERDICT: META-CHECK OK — fdroidserver accepted the staged metadata (log: $log)"
    ;;
  env-check)
    stage_data "$data" "$tag_commit" "https://github.com/$GH_SLUG.git"
    repo_mount=""
    if docker_run "$APPID:$vercode" 2>&1 | tee "$log"; then
      :
    fi
    verdict "$log"
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
    stage_data "$data" "$commit" "$repourl"
    if docker_run "$APPID:$vercode" 2>&1 | tee "$log"; then
      :
    fi
    verdict "$log"
    ;;
esac
