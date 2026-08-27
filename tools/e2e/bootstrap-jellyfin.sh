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

log() { printf '[bootstrap-jellyfin] %s\n' "$*"; }

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

ITEM_ID=""
for _ in $(seq 1 60); do
  # serialization mixes cases across endpoints - match Id/id with a 32-hex value
  # (ServerId never matches: its "Id" lacks the leading quote).
  ITEM_ID="$(curl -sf -m 10 -H "X-Emby-Token: $TOKEN" \
    "$BASE/Items?searchTerm=Harness&Recursive=true&IncludeItemTypes=Movie&Fields=MediaSources" \
    | grep -oE '"[Ii]d":"[0-9a-f]{32}"' | head -1 | grep -oE '[0-9a-f]{32}' || true)"
  [ -n "$ITEM_ID" ] && break
  sleep 2
done
[ -n "$ITEM_ID" ] || { log "FATAL: library scan never produced the item"; exit 1; }
log "item id: $ITEM_ID"

# Ensure a primary image exists (scan usually extracts one; upload fallback is deterministic).
if ! curl -sf -m 10 -H "X-Emby-Token: $TOKEN" -o /dev/null "$BASE/Items/$ITEM_ID/Images/Primary"; then
  log "no primary image from scan - uploading poster.jpg"
  curl -sf -m 15 -X POST -H "X-Emby-Token: $TOKEN" -H 'Content-Type: image/jpeg' \
    --data-binary "@$STATE_DIR/poster.jpg" "$BASE/Items/$ITEM_ID/Images/Primary" >/dev/null
fi

printf '%s' "$ITEM_ID" > "$ITEM_FILE"
log "OK: $BASE | user=$USERNAME password=$PASSWORD | item=$ITEM_ID ($SERVER_VERSION)"
