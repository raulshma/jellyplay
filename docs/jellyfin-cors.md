# Jellyfin CORS setup for JellyPlay Web

JellyPlay's web build is a Kotlin/Wasm (Compose Multiplatform) app whose
network stack is Ktor on the browser `fetch` API. Unlike the Android and
desktop apps — native HTTP clients where CORS does not exist — every request
the web client makes is subject to the browser's Cross-Origin Resource
Sharing rules. If JellyPlay Web is served from one origin (e.g.
`https://jellyplay.example.com`) and your Jellyfin server lives on another
(e.g. `https://media.example.net`), the browser will refuse to expose the
responses **unless your Jellyfin reverse proxy sends CORS headers**.

Jellyfin's own server does not emit `Access-Control-Allow-Origin` for
third-party origins (only for its bundled web client), so the proxy in front
of it must add them. This guide shows the minimal nginx and Caddy snippets.

## What the web client sends

The wasm client (`shared/core/network`, `WasmApiSupport`) authenticates
exactly like the SDK-based native clients:

| Request kind | Headers |
| ------------ | ------- |
| All API calls | `Authorization: MediaBrowser Client="JellyPlay", Version="1.0", DeviceId="<uuid>", Device="JellyPlay+Web", Token="<access token>"` (the `Token` parameter is omitted before login; note the SDK-style space encoding, hence `JellyPlay+Web`) |
| Raw GETs (subtitles, intro/credits) | `X-Emby-Token: <access token>` |
| POST bodies (login, playback reports, mutations) | `Content-Type: application/json` |
| Error handling | reads the `Retry-After` response header on 429/503 |

`Authorization`, `X-Emby-Token` and a JSON `Content-Type` are all
non-simple per the fetch spec, so **every API request triggers an `OPTIONS`
preflight**. The proxy must answer preflights itself — Jellyfin does not.

Images (`/Items/{itemId}/Images/{type}`) and subtitle files are plain GETs;
their URLs are built without an `api_key` query parameter (auth travels on
the request layer, mirroring the Jellyfin SDK), so image loads through the
web image engine follow the same CORS + preflight rules as API calls.

WebSockets (SyncPlay and remote control, future web work) are not subject to
CORS, but the proxy must still pass the `Upgrade`/`Connection` hop-by-hop
headers through — see the snippets below.

## nginx

Inside the `server { }` block that fronts Jellyfin:

```nginx
# JellyPlay Web's origin — the browser-enforced allowed origin.
map $http_origin $jellyplay_cors {
    default "";
    "https://jellyplay.example.com" $http_origin;
}

server {
    listen 443 ssl;
    server_name media.example.net;

    location / {
        # Preflight: answer before the request reaches Jellyfin.
        if ($request_method = OPTIONS) {
            add_header Access-Control-Allow-Origin $jellyplay_cors always;
            add_header Access-Control-Allow-Methods "GET, POST, DELETE, HEAD, OPTIONS" always;
            add_header Access-Control-Allow-Headers "Authorization, X-Emby-Token, Content-Type" always;
            add_header Access-Control-Max-Age "86400" always;
            add_header Content-Length 0;
            return 204;
        }

        add_header Access-Control-Allow-Origin $jellyplay_cors always;
        add_header Access-Control-Expose-Headers "Retry-After" always;

        proxy_pass http://127.0.0.1:8096;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 20M;
    }

    # WebSockets (future SyncPlay / remote control): pass the upgrade through.
    location /socket {
        proxy_pass http://127.0.0.1:8096;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

## Caddy

```caddy
media.example.net {
	# Echo the allowed origin (restrict to your JellyPlay Web origin).
	@allowed_origin header Origin https://jellyplay.example.com
	header @allowed_origin Access-Control-Allow-Origin "https://jellyplay.example.com"
	header @allowed_origin Access-Control-Expose-Headers "Retry-After"

	# Preflight: answer before the request reaches Jellyfin.
	@preflight method OPTIONS
	handle @preflight {
		header Access-Control-Allow-Origin "https://jellyplay.example.com"
		header Access-Control-Allow-Methods "GET, POST, DELETE, HEAD, OPTIONS"
		header Access-Control-Allow-Headers "Authorization, X-Emby-Token, Content-Type"
		header Access-Control-Max-Age "86400"
		respond "" 204
	}

	# reverse_proxy passes WebSocket upgrades through automatically.
	reverse_proxy 127.0.0.1:8096
}
```

## Notes

- **Credentials are not used.** The web client authenticates with headers
  (`Authorization` / `X-Emby-Token`), never cookies, so
  `Access-Control-Allow-Credentials` is not required and the allowed origin
  can be a literal echo of the request origin.
- **HTTPS both sides.** A page served over HTTPS cannot call an
  `http://` Jellyfin origin (mixed-content block) — proxy Jellyfin over TLS
  or serve the web app and Jellyfin under the same host, in which case none
  of this is needed (same-origin requests bypass CORS entirely).
- **`Access-Control-Max-Age`** caches the preflight answer in the browser;
  86400 (one day) keeps the OPTIONS chatter off the wire.
- The same proxy should also cover `/Items/*/Images/*` and subtitle
  endpoints — the snippets above route the whole server surface, which is
  the simplest correct configuration.
