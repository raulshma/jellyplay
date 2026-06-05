# Jellyseerr & Overseerr integration

[Jellyseerr](https://github.com/Fallenbagel/jellyseerr) (and its sibling
[Overseerr](https://overseerr.dev/)) is a self-hosted media-request and
discovery manager for Jellyfin, Plex, and Emby. JellyPlay integrates
natively with both, so you can discover, request, and track content
without ever leaving the couch.

## What you get

- 🔍 **Discover** — browse trending, popular, and upcoming movies &
  TV shows, filterable by region
- 🛒 **Request** — request movies and full TV series, or pick specific
  seasons, from inside the app
- 📊 **Track** — see request status (Pending, Approved, Declined,
  Available) right from the **Requests** tab
- 🔔 **Real-time updates** — JellyPlay polls the Seerr API so the badge
  updates the moment a request is approved

## Prerequisites

- A running [Jellyseerr](https://github.com/Fallenbagel/jellyseerr) **or**
  [Overseerr](https://overseerr.dev/) instance
- Jellyseerr **v2.7.3+** (or Overseerr **v1.x**) connected to your
  Jellyfin server
- JellyPlay v0.6.0 or later

## Step 1 — Get your Seerr API key

1. Open your Seerr / Overseerr web UI in a browser
2. Sign in as an admin
3. Go to **Settings → General → API Key** (Overseerr) or
   **Settings → Users → (your user) → Jellyseerr API Key**
4. Copy the key

## Step 2 — Connect Seerr in JellyPlay

On phone / tablet / TV:

1. Open **Settings → Seerr (Jellyseerr / Overseerr)**
2. Toggle **Enable Seerr integration** on
3. Enter your **Seerr server URL**, e.g. `http://192.168.1.100:5055`
4. Paste your **API key**
5. Tap **Connect**

On first successful connection, JellyPlay automatically discovers your
Seerr version and configures the correct API endpoints.

## Step 3 — Configure regions

To see the right streaming availability and "Trending in" content:

1. In **Settings → Seerr → Regions**
2. Pick your **streaming region** (defaults to your Jellyfin server
   region)
3. Pick your **discover region** for trending content
4. Optional: enable **NSFW content** if your Seerr instance allows it
   (admin must also enable it server-side)

## Step 4 — Request a movie

There are three ways to request content:

### A) From the Discover tab

1. Tap the **Requests** tab in the bottom navigation
2. Browse the **Trending**, **Popular**, or **Upcoming** rows
3. Tap any poster to open the detail page
4. Tap **Request** — pick quality (HD / 4K) and confirm
5. Status updates appear in **Requests → My Requests**

### B) From search

1. Search for any movie or TV show
2. If it's not in your Jellyfin library, the result row shows a
   **Request on Seerr** button
3. Tap to open the request sheet — quality, seasons to request, and
   confirmation

### C) From a media detail page

1. Open any item already in your library
2. Tap the **Seerr** icon in the action bar
3. View the matching Seerr request status, or open in the Seerr web UI

## TV show requests

When requesting a TV show, JellyPlay shows a season picker:

- **All seasons** (default)
- **First season only** — useful for trying out a new show
- **Custom selection** — pick exactly the seasons you want

JellyPlay remembers your preference per series.

## Status badges

JellyPlay shows a small Seerr badge on every search result and library
poster:

| Badge | Meaning |
| ----- | ------- |
| ⏳ Pending | Request submitted, awaiting admin approval |
| ✅ Approved | Request approved, waiting for download |
| ❌ Declined | Request was declined by an admin |
| 📥 Available | Content is now in your Jellyfin library |

## Troubleshooting

| Problem | Fix |
| ------- | --- |
| "Connection failed" | Verify the URL is reachable from the device. Try `http://` if HTTPS certs are misconfigured. |
| 401 Unauthorized | Re-paste the API key from Seerr. |
| No trending content | Set a **discover region** in Settings → Seerr → Regions. |
| Search button missing | The Requests tab only appears when integration is enabled. |
| SyncPlay conflicts | Pause any active SyncPlay session before requesting — Seerr calls are blocked during playback to keep streams stable. |

## Next steps

- 👯 [Start a SyncPlay watch party with the new content →](./syncplay-guide.md)
- ⬇️ [Set up offline downloads for travel →](./offline-downloads.md)
- 📺 [Install JellyPlay on your TV →](./android-tv-setup.md)
