# Setting up JellyPlay

This guide walks you through installing **JellyPlay**, the open-source
Jellyfin client for Android, on your phone, tablet, foldable, Android TV,
or Amazon Fire TV device.

## Prerequisites

- An Android device running **Android 9.0 (API 28) or later**
- A [Jellyfin media server](https://jellyfin.org/downloads/server) — self-hosted
  on your own hardware, NAS, Docker container, or VPS
- A Jellyfin user account with access to at least one library

## Step 1 — Download JellyPlay

Grab the latest APK for your device from the
[**GitHub Releases**](https://github.com/raulshma/jellyplay/releases) page.

Choose the correct APK:

| File | Target device |
| ---- | ------------- |
| `app-phone-<version>.apk` | Phones, tablets, foldables |
| `app-tv-<version>.apk` | Android TV, Fire TV, Chromecast with Google TV |

> Tip: The TV APK includes a Leanback launcher tile so JellyPlay shows
> up alongside Netflix, Prime Video, etc.

## Step 2 — Install

### On a phone or tablet

1. Open the downloaded APK.
2. If prompted, allow "Install unknown apps" for your browser or file
   manager.
3. Tap **Install** and then **Open**.

### On Android TV or Fire TV

See the dedicated [Android TV & Fire TV setup guide](./android-tv-setup.md)
for sideloading instructions (Downloader app, ADB, or USB stick).

## Step 3 — Connect to your Jellyfin server

On first launch JellyPlay walks you through a 10-step **Onboarding Wizard**.
The first step is server connection.

1. Enter your Jellyfin server URL, e.g. `http://192.168.1.100:8096`.
2. Tap **Auto-discover** to let JellyPlay scan your local network.
3. Authenticate with:
   - **Username & password** — token-based, persisted in encrypted storage
   - **Quick Connect** — open Jellyfin's web dashboard, approve the
     6-digit code, and JellyPlay signs you in instantly

JellyPlay supports **multi-server** — you can add as many Jellyfin
servers as you want and switch between them with a tap.

## Step 4 — Personalize

The Onboarding Wizard continues with:

- **Appearance** — light, dark, OLED, or system theme; dynamic theming
  extracted from your library artwork
- **Performance** — enable Performance Mode to disable animations on
  low-end devices
- **Home Layout** — choose Video or Music-first home, customize which
  sections appear
- **Video Player** — pick your preferred engine (ExoPlayer / libmpv /
  LibVLC), streaming quality, seek duration, gesture preferences
- **Audio Player** — default speed, gapless playback, crossfade, normalization
- **Subtitles** — font, size, color, background
- **Security** — PIN lock and biometric lock with auto-lock timer
- **Seerr** *(optional)* — connect your Seerr / Overseerr
  server for media requests — see the
  [Seerr integration guide](./seerr-integration.md)

You can re-run the wizard at any time from **Settings → Onboarding**.

## Step 5 — Start streaming

You're ready! Browse your libraries, cast to a TV, queue up music with
synced lyrics, or download content for offline viewing on your next
flight.

## Troubleshooting

| Problem | Fix |
| ------- | --- |
| "Connection refused" | Verify the server URL includes the port (`8096` by default) and that the server is reachable from the device's network. |
| Login loop | Clear app data from Android Settings, then re-add the server. |
| Video stutters | Try a different player engine in **Settings → Player → Engine** (libmpv is often more robust on budget devices). |
| Audio out of sync | Adjust **Settings → Audio → Audio delay (ms)**. |
| Need more help? | Open a [Q&A discussion](https://github.com/raulshma/jellyplay/discussions). |

## Next steps

- 📺 [Android TV & Fire TV setup →](./android-tv-setup.md)
- 📡 [Connect Seerr for media requests →](./seerr-integration.md)
- 👯 [Start a SyncPlay watch party →](./syncplay-guide.md)
- ⬇️ [Set up offline downloads →](./offline-downloads.md)
