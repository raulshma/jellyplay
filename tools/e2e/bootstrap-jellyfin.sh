#!/usr/bin/env bash
# Bootstrap a local Jellyfin E2E server for the wave-13 verification passes
# (desktop real-server session pass, web browser pass). This commits the
# recipe that wave 12 used ad-hoc (plan: "Jellyfin in Docker + generated media
# + wizard/API bootstrap").
#
# Produces: http://localhost:8096 with user `harness` / password `harness-e2e-pass`
# and one movie item "Harness Test Clip (2026)" (~12 s ffmpeg testsrc clip with
# audio and a primary image). Prints the item id on success.
#
# Wave 20B adds N=8 "Cache Probe Clip <i> (2026)" movies, each with a DISTINCT
# LARGE poster (2560x1440 testsrc2 frame, hue-rotated per item). Sizing
# arithmetic: a 2560x1440 bitmap decodes to 2560*1440*4 = 14,745,600 bytes, so
# 8 posters = 117,964,800 decoded bytes > Coil's MEASURED wasm memory-cache cap
# (80,530,636 bytes = 15% of the 512 MiB wasm budget — see apps/web Main.kt
# CoilStats). A sequential poster probe through the app therefore MUST evict
# early entries (LRU) — the eviction lane (tools/e2e/web-cache-eviction.mjs)
# proves it by re-fetching item #1 after a full pass.
#
# Usage:   tools/e2e/bootstrap-jellyfin.sh [--keep-media]
# Env:     JELLYFIN_IMAGE   docker image (default jellyfin/jellyfin:latest)
#          E2E_STATE_DIR    runtime state (default <repo>/tools/e2e/.state)
#          E2E_PORT         host port (default 8096)
# Requires: docker, curl, ffmpeg on PATH.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="${E2E_STATE_DIR:-$REPO_ROOT/tools/e2e/.state}"
MEDIA_DIR="$STATE_DIR/media"
CONFIG_DIR="$STATE_DIR/config"
PORT="${E2E_PORT:-8096}"
IMAGE="${JELLYFIN_IMAGE:-jellyfin/jellyfin:latest}"
CONTAINER="jellyplay-e2e"
USERNAME="harness"
PASSWORD="harness-e2e-pass"
ITEM_FILE="$STATE_DIR/item-id.txt"

# All logging to STDERR: wait_for_item PRINTS the item id on stdout for
# command substitution, and a stdout log line would be captured into the id
# (first wave-20B run: ITEM_ID became "<log line>\n<id>" and the follow-up
# image upload died with curl exit 3, URL malformed).
log() { printf '[bootstrap-jellyfin] %s\n' "$*" >&2; }

command -v docker >/dev/null || { log "FATAL: docker not on PATH"; exit 1; }
command -v curl  >/dev/null || { log "FATAL: curl not on PATH"; exit 1; }
command -v ffmpeg >/dev/null || { log "FATAL: ffmpeg not on PATH"; exit 1; }

# Docker Desktop may be stopped (wave-12 process note: poll-and-restart is part of the recipe).
if ! docker info >/dev/null 2>&1; then
  log "docker daemon down - starting Docker Desktop and waiting..."
  DOCKER_DESKTOP="$(ls "/c/Program Files/Docker/Docker/Docker Desktop.exe" 2>/dev/null || true)"
  [ -n "$DOCKER_DESKTOP" ] && "$DOCKER_DESKTOP" &
  for _ in $(seq 1 60); do
    docker info >/dev/null 2>&1 && break
    sleep 5
  done
  docker info >/dev/null 2>&1 || { log "FATAL: docker daemon did not come up"; exit 1; }
fi

mkdir -p "$MEDIA_DIR" "$CONFIG_DIR"
WIN_MEDIA="$(cd "$MEDIA_DIR" && pwd -W)"
WIN_CONFIG="$(cd "$CONFIG_DIR" && pwd -W)"

# --- media: one short, recognizable movie clip -------------------------------
if [ ! -f "$MEDIA_DIR/Harness Test Clip (2026).mp4" ]; then
  log "generating media (ffmpeg testsrc2, 12 s)"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc2=duration=12:size=640x360:rate=24" \
    -f lavfi -i "sine=frequency=440:duration=12" \
    -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart \
    "$MEDIA_DIR/Harness Test Clip (2026).mp4"
fi
if [ ! -f "$STATE_DIR/poster.jpg" ]; then
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc2=duration=1:size=480x270:rate=1" -frames:v 1 \
    "$STATE_DIR/poster.jpg"
fi

# --- wave 20B: cache-probe library (8 additional movies, LARGE posters) ------
# Posters are 2560x1440 single frames (14,745,600 decoded bytes each — see the
# header arithmetic); hue rotation by i*40 degrees makes every poster's CONTENT
# distinct (the probe URLs differ by item id regardless, but distinct pixels
# keep screenshots honestly auditable). Clips stay tiny (3 s at 320x180): only
# the poster matters for the eviction lane.
PROBE_COUNT=8
for i in $(seq 1 "$PROBE_COUNT"); do
  PROBE_NAME="Cache Probe Clip $i (2026)"
  if [ ! -f "$MEDIA_DIR/$PROBE_NAME.mp4" ]; then
    ffmpeg -hide_banner -loglevel error -y \
      -f lavfi -i "testsrc2=duration=3:size=320x180:rate=12" \
      -f lavfi -i "sine=frequency=$((440 + i * 30)):duration=3" \
      -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart \
      "$MEDIA_DIR/$PROBE_NAME.mp4"
  fi
  if [ ! -f "$STATE_DIR/poster-$i.jpg" ]; then
    ffmpeg -hide_banner -loglevel error -y \
      -f lavfi -i "testsrc2=duration=1:size=2560x1440:rate=1" -frames:v 1 \
      -vf "hue=h=$((i * 40)):s=4" \
      "$STATE_DIR/poster-$i.jpg"
  fi
done

# --- container ----------------------------------------------------------------
if docker inspect "$CONTAINER" >/dev/null 2>&1; then
  log "removing stale container $CONTAINER"
  docker rm -f "$CONTAINER" >/dev/null
fi
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  log "pulling $IMAGE (not cached)"
  docker pull "$IMAGE"
fi
log "starting $CONTAINER from $IMAGE on port $PORT"
MSYS_NO_PATHCONV=1 docker run -d --name "$CONTAINER" \
  -p "$PORT:8096" \
  -v "$WIN_MEDIA:/media" \
  -v "$WIN_CONFIG:/config" \
  -e TZ=UTC \
  "$IMAGE" >/dev/null

BASE="http://localhost:$PORT"
json_field() { # json_field <json> <field> - first occurrence, case-insensitive keys (10.11 serves camelCase), no jq needed
  printf '%s' "$1" | grep -oi "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}
SERVER_JSON=""
for _ in $(seq 1 90); do
  SERVER_JSON="$(curl -sf -m 3 "$BASE/System/Info/Public" 2>/dev/null || true)"
  [ -n "$SERVER_JSON" ] && break
  sleep 2
done
[ -n "$SERVER_JSON" ] || { log "FATAL: server never became healthy"; exit 1; }
SERVER_VERSION="$(json_field "$SERVER_JSON" Version)"
log "server healthy: Jellyfin ${SERVER_VERSION:-<unknown>}"

# --- first-run wizard (fresh config only) --------------------------------------
# Note: /System/Info/Public answers while core services still return
# 503 "Jellyfin Server is loading" - wait for the Startup endpoints themselves.
AUTH_HEADER='X-Emby-Authorization: MediaBrowser Client="e2e-bootstrap", Device="e2e", DeviceId="e2e-bootstrap-1", Version="1.0"'
for _ in $(seq 1 60); do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' -m 3 -H "$AUTH_HEADER" "$BASE/Startup/Configuration" || true)"
  [ "$CODE" != "503" ] && [ "$CODE" != "000" ] && break
  sleep 2
done
WIZARD_DONE=""
for _ in $(seq 1 30); do
  # boolean field, not a string - extract the literal true/false
  WIZARD_DONE="$(printf '%s' "$SERVER_JSON" | grep -oi '"startupwizardcompleted":\(true\|false\)' | head -1 | grep -o 'true\|false' || true)"
  [ -n "$WIZARD_DONE" ] && break
  SERVER_JSON="$(curl -sf -m 3 "$BASE/System/Info/Public" 2>/dev/null || true)"
  sleep 2
done
if [ "$WIZARD_DONE" = "false" ]; then
  # MEASURED (wave 20B, Jellyfin 10.11.11): a RE-CREATED container with a
  # completed-wizard config still serves startupwizardcompleted=false for a
  # window while core services load the persisted state — acting on the
  # first false reading runs the wizard into 401s ("could not create first
  # user") and FATALs before the poster-repair no-op pass. Only a STABLE
  # false (held across this settle window) means the wizard really needs
  # running (a genuinely fresh config keeps answering false).
  log "wizard reported incomplete - re-verifying (10.11 re-created-container startup artifact)"
  for _ in $(seq 1 20); do
    sleep 3
    SERVER_JSON="$(curl -sf -m 3 "$BASE/System/Info/Public" 2>/dev/null || true)"
    WIZARD_DONE="$(printf '%s' "$SERVER_JSON" | grep -oi '"startupwizardcompleted":\(true\|false\)' | head -1 | grep -o 'true\|false' || true)"
    [ "$WIZARD_DONE" = "true" ] && break
  done
fi
if [ "$WIZARD_DONE" = "false" ]; then
  log "running first-run wizard over API"
  # 10.11 CSRF middleware: wizard POSTs without a browser-like Origin/Referer
  # (and cookie) are rejected as 404. Send them exactly like the wizard UI does.
  COOKIE_JAR="$STATE_DIR/.cookies.txt"; : > "$COOKIE_JAR"
  BROWSER_HEADERS=(-H "Origin: $BASE" -H "Referer: $BASE/web/index.html" -b "$COOKIE_JAR" -c "$COOKIE_JAR")
  curl -sf -m 10 "${BROWSER_HEADERS[@]}" -H "$AUTH_HEADER" "$BASE/Startup/User" >/dev/null || true
  post_startup() { # post_startup <path> [json-body]
    local out code
    out="$(curl -s -m 15 -w '\n%{http_code}' -X POST "${BROWSER_HEADERS[@]}" -H "$AUTH_HEADER" -H 'Content-Type: application/json' \
      ${2:+-d "$2"} "$BASE/Startup/$1" || true)"
    code="${out##*$'\n'}"
    case "$code" in 200|204) return 0 ;; *) log "WARN: Startup/$1 POST -> $code: ${out%$'\n'*}"; return 1 ;; esac
  }
  post_startup Configuration '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' || true
  post_startup User "{\"Name\":\"$USERNAME\",\"Password\":\"$PASSWORD\"}" \
    || { log "FATAL: could not create first user (wizard CSRF or policy)"; exit 1; }
  post_startup Complete || true
  sleep 3
fi

# --- authenticate (with retries: auth may lag wizard completion) ---------------
auth_as() { # auth_as <user> <pw> -> echoes token
  curl -sf -m 10 -X POST -H "$AUTH_HEADER" -H 'Content-Type: application/json' \
    -d "{\"Username\":\"$1\",\"Pw\":\"$2\"}" \
    "$BASE/Users/AuthenticateByName" 2>/dev/null | grep -oi '"accesstoken":"[^"]*"' | head -1 | cut -d'"' -f4 || true
}
TOKEN=""
for _ in $(seq 1 10); do
  TOKEN="$(auth_as "$USERNAME" "$PASSWORD")"
  [ -n "$TOKEN" ] && break
  sleep 2
done
[ -n "$TOKEN" ] || { log "FATAL: AuthenticateByName returned no token - wipe $CONFIG_DIR and re-run"; exit 1; }
log "authenticated as $USERNAME"

# --- library + item ---------------------------------------------------------------
if ! curl -sf -m 10 -H "X-Emby-Token: $TOKEN" "$BASE/Library/VirtualFolders" | grep -q 'E2E Media'; then
  log "adding library 'E2E Media' -> /media"
  curl -sf -m 30 -X POST -H "X-Emby-Token: $TOKEN" \
    "$BASE/Library/VirtualFolders?name=E2E%20Media&collectionType=movies&paths=/media&refreshLibrary=true" >/dev/null
fi

# --- items: exact-Name id lookup + deterministic primary-image upload ---------
# Id extraction is NAME-SCOPED (split the search response into per-object lines
# and take the id from the object carrying the exact Name): a bare
# first-id-in-response pick (the wave-13 shape, fine for a 1-item library)
# would race the scan and could grab a neighbor once 9 movies exist. NOTE the
# names passed here are the DISPLAY names WITHOUT the year — Jellyfin's file
# parser splits "Foo (2026).mp4" into Name="Foo" + ProductionYear=2026, so a
# lookup keyed on the full filename stem never matches (first wave-20B run's
# lesson: the scan HAD produced every item; the lookup just could not see them).
item_id_by_name() { # item_id_by_name <display name> -> echoes 32-hex id or empty
  curl -sf -m 10 -G -H "X-Emby-Token: $TOKEN" \
    --data-urlencode "searchTerm=$1" \
    --data-urlencode "Recursive=true" \
    --data-urlencode "IncludeItemTypes=Movie" \
    "$BASE/Items" \
    | tr '{' '\n' | grep -F "\"Name\":\"$1\"" \
    | grep -oE '"[Ii]d":"[0-9a-f]{32}"' | head -1 | grep -oE '[0-9a-f]{32}' || true
}

wait_for_item() { # wait_for_item <display name> -> echoes id; triggers one library refresh
  local id="" refreshed=0 _scan
  for _scan in $(seq 1 45); do
    id="$(item_id_by_name "$1")"
    if [ -n "$id" ]; then printf '%s' "$id"; return 0; fi
    if [ "$refreshed" -eq 0 ]; then
      log "item '$1' not scanned yet - requesting library refresh"
      curl -sf -m 15 -X POST -H "X-Emby-Token: $TOKEN" "$BASE/Library/Refresh" >/dev/null || true
      refreshed=1
    fi
    sleep 2
  done
  return 1
}

# Ensure the INTENDED poster is the item's primary image — not merely that A
# primary exists: on 10.11 the library scan EXTRACTS a primary from the video
# file's own pixels (first wave-20B run measured ImageTags.Primary present on
# freshly scanned items), and a "has any primary" check would skip the upload
# and leave the fixture with ~0.2MB decoded thumbnails instead of the 14.7MB
# posters the eviction arithmetic needs. Compare the SERVED byte size to the
# intended file — equal means our poster is already in place (re-runs are
# no-ops), anything else (smaller scan frame, 404) uploads.
# MEASURED (wave 18A, Jellyfin 10.11.11 / jellyfin:latest): SetItemImage
# base64-DECODES the request body (FromBase64Transform inside ImageSaver), so
# a raw JPEG body 500s with "One of the identified items was in an invalid
# format"; the body must be the base64 text of the image. Raw is tried first
# for older-server compatibility, base64 is the fallback.
ensure_primary_image() { # ensure_primary_image <item id> <name> <poster file>
  local want_size have_size
  want_size="$(wc -c < "$3" | tr -d ' ')"
  have_size="$(curl -sf -m 20 -H "X-Emby-Token: $TOKEN" -o /dev/null \
    -w '%{size_download}' "$BASE/Items/$1/Images/Primary" || echo 0)"
  if [ "$have_size" = "$want_size" ]; then return 0; fi
  log "primary on '$2' is ${have_size}B, want ${want_size}B - uploading $(basename "$3")"
  curl -sf -m 60 -X POST -H "X-Emby-Token: $TOKEN" -H 'Content-Type: image/jpeg' \
    --data-binary "@$3" "$BASE/Items/$1/Images/Primary" >/dev/null \
  || { base64 -w0 "$3" > "$3.b64"
       curl -sf -m 60 -X POST -H "X-Emby-Token: $TOKEN" -H 'Content-Type: image/jpeg' \
         --data-binary "@$3.b64" "$BASE/Items/$1/Images/Primary" >/dev/null
       rm -f "$3.b64"; }
}

HARNESS_NAME="Harness Test Clip"
ITEM_ID="$(wait_for_item "$HARNESS_NAME")" \
  || { log "FATAL: library scan never produced '$HARNESS_NAME'"; exit 1; }
log "item id: $ITEM_ID"
ensure_primary_image "$ITEM_ID" "$HARNESS_NAME" "$STATE_DIR/poster.jpg"

# Wave 20B cache-probe items (idempotent per item: name lookup skips
# already-scanned items, ensure_primary_image byte-compares the served image
# and only re-uploads when the intended poster is not in place).
PROBE_IDS=""
for i in $(seq 1 "$PROBE_COUNT"); do
  PROBE_NAME="Cache Probe Clip $i"
  PROBE_ID="$(wait_for_item "$PROBE_NAME")" \
    || { log "FATAL: library scan never produced '$PROBE_NAME'"; exit 1; }
  ensure_primary_image "$PROBE_ID" "$PROBE_NAME" "$STATE_DIR/poster-$i.jpg"
  PROBE_IDS="$PROBE_IDS $PROBE_ID"
  log "cache-probe item $i/$PROBE_COUNT: $PROBE_ID"
done

printf '%s' "$ITEM_ID" > "$ITEM_FILE"
log "OK: $BASE | user=$USERNAME password=$PASSWORD | item=$ITEM_ID ($SERVER_VERSION) | cache-probe items:$PROBE_IDS"
