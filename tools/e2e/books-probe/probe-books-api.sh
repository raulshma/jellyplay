#!/usr/bin/env bash
# Books API probe: run a local Jellyfin with a books library and replicate the
# app's exact detail-fetch calls to settle how Path/format data arrives for
# Book items (and what type nested folder containers serialize as).
#
# Produces: http://localhost:8097, user harness/harness-e2e-pass, library
# "Books" (collectionType=books) over .state/media/books (flat epub + cbz and
# a nested Foldered Series/Volume 1.cbz).
#
# Usage: tools/e2e/books-probe/probe-books-api.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
STATE_DIR="${BOOKS_PROBE_STATE:-$REPO_ROOT/tools/e2e/books-probe/.state}"
MEDIA_DIR="$STATE_DIR/media"
CONFIG_DIR="$STATE_DIR/config"
PORT="${BOOKS_PROBE_PORT:-8097}"
IMAGE="${JELLYFIN_IMAGE:-jellyfin/jellyfin:latest}"
CONTAINER="${BOOKS_PROBE_CONTAINER:-jellyplay-books-probe}"
USERNAME="harness"
PASSWORD="harness-e2e-pass"

log() { printf '[books-probe] %s\n' "$*" >&2; }

command -v docker >/dev/null || { log "FATAL: docker not on PATH"; exit 1; }
docker info >/dev/null 2>&1 || { log "FATAL: docker daemon down"; exit 1; }
[ -f "$MEDIA_DIR/books/Flat Book.epub" ] || { log "FATAL: fixtures missing - run make-fixtures.py first"; exit 1; }

mkdir -p "$CONFIG_DIR"
WIN_MEDIA="$(cd "$MEDIA_DIR" && pwd -W)"
WIN_CONFIG="$(cd "$CONFIG_DIR" && pwd -W)"

if docker inspect "$CONTAINER" >/dev/null 2>&1; then
  docker rm -f "$CONTAINER" >/dev/null
fi
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  log "pulling $IMAGE"
  docker pull "$IMAGE" >/dev/null
fi
log "starting $CONTAINER on port $PORT"
MSYS_NO_PATHCONV=1 docker run -d --name "$CONTAINER" -p "$PORT:8096" \
  -v "$WIN_MEDIA:/media" -v "$WIN_CONFIG:/config" -e TZ=UTC "$IMAGE" >/dev/null

BASE="http://localhost:$PORT"
json_field() { printf '%s' "$1" | grep -oi "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

SERVER_JSON=""
for _ in $(seq 1 90); do
  SERVER_JSON="$(curl -sf -m 3 "$BASE/System/Info/Public" 2>/dev/null || true)"
  [ -n "$SERVER_JSON" ] && break
  sleep 2
done
[ -n "$SERVER_JSON" ] || { log "FATAL: server never became healthy"; exit 1; }
log "server healthy: Jellyfin $(json_field "$SERVER_JSON" Version)"

# JF12 parses only the standard Authorization header (legacy X-Emby-Authorization yields request.App=null)
AUTH_HEADER='Authorization: MediaBrowser Client="books-probe", Device="e2e", DeviceId="books-probe-1", Version="1.0"'
for _ in $(seq 1 60); do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' -m 3 -H "$AUTH_HEADER" "$BASE/Startup/Configuration" || true)"
  [ "$CODE" != "503" ] && [ "$CODE" != "000" ] && break
  sleep 2
done
WIZARD_DONE=""
for _ in $(seq 1 30); do
  WIZARD_DONE="$(printf '%s' "$SERVER_JSON" | grep -oi '"startupwizardcompleted":\(true\|false\)' | head -1 | grep -o 'true\|false' || true)"
  [ -n "$WIZARD_DONE" ] && break
  SERVER_JSON="$(curl -sf -m 3 "$BASE/System/Info/Public" 2>/dev/null || true)"
  sleep 2
done
if [ "$WIZARD_DONE" = "false" ]; then
  for _ in $(seq 1 20); do
    sleep 3
    SERVER_JSON="$(curl -sf -m 3 "$BASE/System/Info/Public" 2>/dev/null || true)"
    WIZARD_DONE="$(printf '%s' "$SERVER_JSON" | grep -oi '"startupwizardcompleted":\(true\|false\)' | head -1 | grep -o 'true\|false' || true)"
    [ "$WIZARD_DONE" = "true" ] && break
  done
fi
if [ "$WIZARD_DONE" = "false" ]; then
  log "running first-run wizard over API"
  COOKIE_JAR="$STATE_DIR/.cookies.txt"; : > "$COOKIE_JAR"
  BROWSER_HEADERS=(-H "Origin: $BASE" -H "Referer: $BASE/web/index.html" -b "$COOKIE_JAR" -c "$COOKIE_JAR")
  curl -sf -m 10 "${BROWSER_HEADERS[@]}" -H "$AUTH_HEADER" "$BASE/Startup/User" >/dev/null || true
  post_startup() {
    local out code
    out="$(curl -s -m 15 -w '\n%{http_code}' -X POST "${BROWSER_HEADERS[@]}" -H "$AUTH_HEADER" -H 'Content-Type: application/json' ${2:+-d "$2"} "$BASE/Startup/$1" || true)"
    code="${out##*$'\n'}"
    case "$code" in 200|204) return 0 ;; *) log "WARN: Startup/$1 POST -> $code"; return 1 ;; esac
  }
  post_startup Configuration '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' || true
  post_startup User "{\"Name\":\"$USERNAME\",\"Password\":\"$PASSWORD\"}" \
    || { log "FATAL: could not create first user"; exit 1; }
  post_startup Complete || true
  sleep 3
fi

auth_as() {
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
[ -n "$TOKEN" ] || { log "FATAL: no auth token"; exit 1; }
log "authenticated as $USERNAME"

if ! curl -sf -m 10 -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Library/VirtualFolders" | grep -q 'Books'; then
  log "adding library 'Books' -> /media/books"
  curl -sf -m 30 -X POST -H "Authorization: MediaBrowser Token="$TOKEN"" \
    "$BASE/Library/VirtualFolders?name=Books&collectionType=books&paths=/media/books&refreshLibrary=true" >/dev/null
fi

list_books() {
  curl -sf -m 10 -G -H "Authorization: MediaBrowser Token="$TOKEN"" \
    --data-urlencode "parentId=" \
    --data-urlencode "Recursive=true" \
    --data-urlencode "IncludeItemTypes=Book,Folder" \
    --data-urlencode "Fields=Path" \
    "$BASE/Items"
}

REFRESHED=0
for _ in $(seq 1 45); do
  BODY="$(list_books || true)"
  COUNT="$(printf '%s' "$BODY" | grep -o '"Type":"[A-Za-z]*"' | wc -l | tr -d ' ')"
  if [ "${COUNT:-0}" -ge 3 ]; then break; fi
  if [ "$REFRESHED" -eq 0 ]; then
    curl -sf -m 15 -X POST -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Library/Refresh" >/dev/null || true
    REFRESHED=1
  fi
  sleep 2
done

echo "=== ITEMS (Recursive, IncludeItemTypes=Book,Folder, Fields=Path) ==="
list_books | python -c '
import json,sys
d=json.load(sys.stdin)
for i in d.get("Items",[]):
    print(i.get("Type"), "|", i.get("Name"), "|", i.get("Id"), "| Path=", i.get("Path"))
print("TotalRecordCount:", d.get("TotalRecordCount"))
'
ITEMS_JSON="$(list_books)"
EPUB_ID="$(printf '%s' "$ITEMS_JSON" | python -c 'import json,sys;[print(i["Id"]) for i in json.load(sys.stdin)["Items"] if i.get("Name")=="Flat Book"]' | head -1)"
FOLDER_ID="$(printf '%s' "$ITEMS_JSON" | python -c 'import json,sys;[print(i["Id"]) for i in json.load(sys.stdin)["Items"] if i.get("Type")=="Folder"]' | head -1)"
[ -n "$EPUB_ID" ] || { log "FATAL: Flat Book not scanned"; exit 1; }
USER_ID="$(curl -sf -m 10 -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Users" | python -c 'import json,sys;print(json.load(sys.stdin)[0]["Id"])')"

echo
echo "=== PROBE 1: app detail fetch — GET /Items?ids=<book>&fields=<DETAIL_PROJECTION_FIELDS> ==="
curl -sf -m 10 -G -H "Authorization: MediaBrowser Token="$TOKEN"" \
  --data-urlencode "ids=$EPUB_ID" \
  --data-urlencode "fields=People,Chapters,MediaSources,Trickplay,ExternalUrls,OriginalTitle,ProductionLocations,Studios,Genres,Overview,ProviderIds,PrimaryImageAspectRatio,Path" \
  "$BASE/Items" | python -c '
import json,sys
d=json.load(sys.stdin)
print("ItemsReturned:", len(d.get("Items",[])))
for i in d.get("Items",[]):
    print("Type:", i.get("Type"), "| Name:", i.get("Name"), "| Path:", i.get("Path"), "| IsFolder:", i.get("IsFolder"))
'

echo
echo "=== PROBE 2: fallback — GET /Users/{uid}/Items/<book> (no fields param) ==="
curl -sf -m 10 -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Users/$USER_ID/Items/$EPUB_ID" | python -c '
import json,sys
i=json.load(sys.stdin)
print("Type:", i.get("Type"), "| Name:", i.get("Name"), "| Path:", i.get("Path"), "| IsFolder:", i.get("IsFolder"))
' || echo "(request failed)"

echo
echo "=== PROBE 3: folder container — GET /Users/{uid}/Items/<folderId> ==="
if [ -n "$FOLDER_ID" ]; then
  curl -sf -m 10 -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Users/$USER_ID/Items/$FOLDER_ID" | python -c '
import json,sys
i=json.load(sys.stdin)
print("Type:", i.get("Type"), "| Name:", i.get("Name"), "| IsFolder:", i.get("IsFolder"), "| Path:", i.get("Path"))
'
else
  echo "(no Folder item found in scan)"
fi

echo
echo "=== PROBE 4: library children — GET /Users/{uid}/Items?parentId=<libraryId> ==="
LIB_ID="$(curl -sf -m 10 -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Library/VirtualFolders" | python -c 'import json,sys;[print(v["ItemId"]) for v in json.load(sys.stdin) if v.get("Name")=="Books"]' | head -1)"
curl -sf -m 10 -G -H "Authorization: MediaBrowser Token="$TOKEN"" "$BASE/Users/$USER_ID/Items" --data-urlencode "parentId=$LIB_ID" | python -c '
import json,sys
d=json.load(sys.stdin)
for i in d.get("Items",[]):
    print(i.get("Type"), "|", i.get("Name"), "| IsFolder:", i.get("IsFolder"))
'
