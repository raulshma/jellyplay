# Offline downloads

Going on a flight? Commuting on a subway? JellyPlay's offline download
manager lets you take your entire Jellyfin library on the go — movies,
TV series, music, and more.

## Feature overview

- 📥 **WorkManager-backed queue** — downloads survive app kills, device
  reboots, and OS-imposed background limits
- ⏸️ **Pause / resume / retry** — long-press a download in the queue
  to pause, resume, or restart with one tap
- 🔁 **HTTP Range resumption** — interrupted downloads pick up exactly
  where they left off, no wasted bandwidth
- 📊 **Foreground notification** — see live progress, download speed,
  and ETA from the Android notification shade
- 📚 **Offline library browser** — downloaded content is organized
  in a dedicated **Downloads** section, independent of your Jellyfin
  server's availability
- 📺 **Series & episode selection** — request single episodes, full
  seasons, or entire series; JellyPlay pre-calculates storage needs
- 🔐 **Encrypted at rest** — downloaded media is stored in app-private
  storage, protected by Android's per-app sandbox

## How to download a movie

1. Open any movie in JellyPlay
2. Tap the **Download** icon (or kebab menu → **Download**)
3. Choose a quality:
   - **Original** (direct play, no transcoding) — best quality, largest
     file
   - **1080p** — high quality, transcoded if needed
   - **720p** — good for tablets
   - **480p** — saves space, ideal for phones on a long flight
4. Tap **Start download**

The movie appears in **Library → Downloads** with a progress badge.

## How to download a TV series

1. Open any series detail page
2. Tap **Download**
3. Pick **Entire series**, **Specific seasons**, or **Specific episodes**
4. JellyPlay shows an estimated storage size before you confirm
5. Tap **Start downloads**

Each episode is queued as a separate download so you can watch the
first one while the rest continue downloading.

## Configuring downloads

Open **Settings → Downloads** to tune the experience:

| Setting | Default | Notes |
| ------- | ------- | ----- |
| **Max concurrent downloads** | 2 | Bump to 3-4 on fast WiFi, drop to 1 on cellular |
| **Preferred quality (WiFi)** | Original | Uses more data but best quality |
| **Preferred quality (Cellular)** | 720p | Saves data |
| **Max cache size** | Unlimited (0) | Set a cap in MB to auto-evict old downloads |
| **Download over roaming** | Off | Toggle if you want to download on roaming |
| **Auto-delete after watching** | Off | Saves storage by cleaning up finished downloads |

## Offline playback

Once a download is complete:

- The item appears in **Library → Offline Library**
- Tap to play — JellyPlay uses the local copy, no network needed
- All player features work: subtitles, audio tracks, trickplay, sync,
  speed control
- Resume position syncs back to your Jellyfin server the next time
  you're online (optional — disable in **Settings → Privacy**)

## Storage tips

- **Where are downloads stored?** In Android's app-private directory:
  `/Android/data/raulshma.jellyplay/files/Downloads/`
- **Move to SD card** — supported on Android 11+ via the system file
  picker in **Settings → Downloads → Storage location**
- **Clear all downloads** — Settings → Downloads → Clear all (with a
  confirmation dialog)

## Managing the queue

Open **Settings → Downloads → Queue** to see:

- Currently downloading items with progress, speed, and ETA
- Queued items waiting to start
- Paused items you can resume
- Failed items with the failure reason (network, storage, server error)

Long-press any item to:

- Pause / resume
- Cancel
- Restart from the beginning
- Show in folder (opens the system file manager)

## Subtitles & external assets

When downloading a movie, JellyPlay also caches:

- The selected subtitle track (any language)
- The audio track you last used
- Trickplay sprite sheets for thumbnail seeking
- Chapter markers

External subtitle files (SRT, ASS, VTT) are **not** auto-downloaded —
use the in-app subtitle search if you need one for offline playback.

## Background restrictions

On some manufacturers (Xiaomi, Huawei, OnePlus) Android aggressively
kills background processes. To ensure downloads complete:

- Add JellyPlay to the **protected apps** list in your device's
  battery settings
- Disable **Adaptive battery** for JellyPlay
- Allow **Unrestricted background activity** in
  **App info → Battery**

The download manager uses a foreground service so the system
notification should remain visible throughout.

## Limitations

| Item | Why |
| ---- | --- |
| **Live TV** | Streaming-only by design |
| **Music videos** | ✅ Supported (video download) |
| **Audio-only music** | ✅ Supported — see the [music player guide](./lyrics-music-player.md) |
| **HLS / DASH streams** | ✅ Direct play, no re-encoding |
| **DRM-protected content** | ❌ Blocked (Jellyfin doesn't host DRM content anyway) |

## Next steps

- 📺 [Set up JellyPlay on your TV →](./android-tv-setup.md)
- 🎵 [Download music for offline listening →](./lyrics-music-player.md)
- ⚖️ [See how JellyPlay compares to other Jellyfin clients →](./why-jellyplay-vs-plex-emby.md)
