<div align="center">

# JellyPlay

</div>

<p align="center">
	<img src="media-resources/app_logo.svg" width="150" alt="JellyPlay app logo" />
</p>

<p align="center">
	<img alt="API 28+" src="https://img.shields.io/badge/Api%2028%2B-50f270?logo=android&logoColor=black&style=for-the-badge" />
	<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge" />
	<img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white&style=for-the-badge" />
	<img alt="Material 3" src="https://custom-icon-badges.demolab.com/badge/material%20you-lightblue?style=for-the-badge&logoColor=333&logo=material-you" />
	<img alt="Media3" src="https://img.shields.io/badge/Media3-FF6F00?style=for-the-badge&logo=android&logoColor=white" />
	<img alt="License GPL-3.0" src="https://img.shields.io/github/license/raulshma/jellyplay?style=for-the-badge&color=blue" />
	<img alt="Status - Beta" src="https://img.shields.io/badge/Status-Beta-orange?style=for-the-badge" />
</p>

<p align="center">
	<sub>Native Jellyfin client for Android, Android TV & Fire TV &nbsp;&middot;&nbsp; Material 3 Expressive &nbsp;&middot;&nbsp; ExoPlayer, libmpv & LibVLC &nbsp;&middot;&nbsp; Self-hosted, no tracking</sub>
</p>

<div align="center">

### Modern, Open-Source Jellyfin Client for Android & Android TV

**JellyPlay** is a high-performance, feature-rich, and open-source **Jellyfin client app for Android** devices. Built entirely from the ground up using **Kotlin** and **Jetpack Compose** (Material 3/Expressive), JellyPlay delivers a premium, smooth, and native media streaming experience. Designed with an adaptive responsive interface, it adapts flawlessly to **Android mobile phones, tablets, foldables, and Android TV**.

Whether you want to stream movies, play music with synchronized lyrics, manage your server via an integrated **Admin Dashboard**, edit library metadata on the go, request content via **Jellyseerr/Overseerr**, download media for **offline playback**, catch up with a weekly **Newsletter digest**, or join real-time **SyncPlay watch parties**, JellyPlay has you covered with built-in multi-engine support (**ExoPlayer, libmpv, LibVLC**), **remote control** from the server, and a guided **Onboarding Wizard** for personalized setup.

[Key Features](#features) • [Why JellyPlay?](#why-jellyplay) • [Comparison](#jellyplay-vs-other-jellyfin-clients) • [Tech Stack](#tech-stack) • [Requirements](#requirements) • [Building & Flavors](#building) • [Permissions](#permissions) • [Project Structure](#project-structure) • [Docs](./docs) • [Website](https://raulshma.github.io/jellyplay/)

</div>

<table align="center" cellpadding="0" cellspacing="0" style="border-collapse: collapse; border-spacing: 0;">
	<tr>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/home.webp" width="180" alt="Home screen" style="display: block;" /></td>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/library.webp" width="180" alt="Library screen" style="display: block;" /></td>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/search.webp" width="180" alt="Search screen" style="display: block;" /></td>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/settings.webp" width="180" alt="Settings screen" style="display: block;" /></td>
	</tr>
	<tr>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/audio_home.webp" width="180" alt="Audio home screen" style="display: block;" /></td>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/audio_player.webp" width="180" alt="Audio player screen" style="display: block;" /></td>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/media_detail.webp" width="180" alt="Media detail screen" style="display: block;" /></td>
		<td align="center" style="padding: 1px;"><img src="media-resources/screenshots/media_detail_2.webp" width="180" alt="Secondary media detail screen" style="display: block;" /></td>
	</tr>
	<tr>
		<td colspan="4" align="center" style="padding: 1px;"><img src="media-resources/screenshots/video_player.webp" width="760" alt="Landscape video player screen" style="display: block;" /></td>
	</tr>
</table>

---

> [!NOTE]
> **JellyPlay is currently in Active Beta.** 🧪
> The application is under active development. While the core features (such as playback, multi-server support, and offline downloads) are functional and stable, you may encounter occasional visual bugs or edge-case issues. We highly appreciate any bug reports, feedback, and contributions!

## Why JellyPlay?

JellyPlay is built for people who self-host a [Jellyfin](https://jellyfin.org/)
media server and want a **truly native**, **beautiful**, and **capable** client
on every screen — phone, tablet, foldable, Android TV, and Amazon Fire TV.

- **Native, not a web wrapper.** Written from scratch in **Kotlin** and
  [Jetpack Compose](https://developer.android.com/jetpack/compose), with
  [Compose for TV](https://developer.android.com/tv/compose) on the big
  screen. No Cordova, no embedded browser, no compromises.
- **Three video engines.** Pick **[ExoPlayer / Media3](https://developer.android.com/media/media3)**,
  **[libmpv](https://github.com/mpv-player/mpv)**, or **[LibVLC](https://www.videolan.org/vlc/)**
  per device — each has its own strengths for codec support, post-processing,
  and ASS/SSA subtitle rendering.
- **One app, every form factor.** Adaptive layouts across phones, tablets,
  foldables, and Android TV with a dedicated Leanback launcher. Edge-to-edge
  immersive mode, predictive back gesture, and Material 3 dynamic theming
  from your artwork.
- **Self-hosted first.** No accounts, no telemetry, no cloud dependency.
  Multi-server, multi-user support with Quick Connect and token-based auth.
  **Offline downloads** with HTTP Range resumption for travel.
- **Beyond streaming.** **SyncPlay** watch parties, **Jellyseerr / Overseerr**
  requests, a full **metadata editor**, an in-app **server admin dashboard**,
  synchronized lyrics via LRCLIB, 10-band equalizer with night mode, and a
  weekly **newsletter digest** of your library activity.

> If you're looking for a Kodi alternative, a Plex alternative, or a
> self-hosted media player for Android & Android TV — give JellyPlay a try.

## Features

### Platform & UI

- Multi-server Jellyfin support with auto-discovery
- Token-based and Quick Connect authentication
- Multi-user support with per-server user switching
- Material 3 UI with dynamic theming from artwork
- Expressive animations and spring-based motion specifications
- Shared element transitions between screens
- Performance Mode (disables animations for low-end devices)
- Predictive back gesture support
- Edge-to-edge immersive layouts
- Adaptive layouts for phone, tablet, foldable, and TV
- Android TV support with D-pad navigation and Leanback launcher
- Android TV screensaver (Daydream) with configurable slideshow and Ken Burns effect
- PIN lock and biometric authentication (fingerprint/face) with auto-lock timer
- Kids Mode with content filtering
- Home screen widgets (Now Playing, Continue Watching)
- Quick settings tile launcher
- App shortcuts (Continue Watching, Search, Play Music, Downloads, Continue Listening)
- BlurHash image placeholders for smooth loading
- Localization in 9 languages (English, German, Spanish, French, Italian, Portuguese, Japanese, Korean, Chinese)

### Search

- Global Jellyfin search across movies, shows, music, albums, and more
- Filters for genre, year, and media type
- Voice search support

### Video Player

- **Three built-in engines**: ExoPlayer (Media3), libmpv, and LibVLC
- Video filter controls: Adjust brightness, contrast, saturation, and sharpness in-player (libmpv & LibVLC)
- Enhanced Video Stats Overlay presenting real-time stream bitrate, frame rate, and dropped frames
- External player launching (MX Player, VLC, etc.)
- Direct play, direct stream, and transcoding support with quality/transcoding picker
- Resume playback and progress reporting
- Audio and subtitle track selection
- Playback speed control (0.25x–4x)
- Gesture controls for seek, brightness, and volume
- Chapter and episode navigation
- HDR badge indicator
- Picture-in-Picture support
- Mini player overlay
- Trickplay thumbnail seeking (Jellyfin trickplay sprite sheets) with offline caching support
- Frame rate matching for display refresh rate sync
- Adaptive bitrate streaming
- Intro skip and next episode auto-play with segment auto-skip (intro/outro/recap)
- Chromecast support via Google Cast SDK
- Media session integration for lock screen and notifications
- Sleep timer with configurable duration
- Aspect ratio selection
- Hardware/software decoder selection
- Audio delay adjustment (ms)
- Player lock screen overlay to prevent accidental touches
- Tap-to-translate subtitle feature

### Subtitle System

- External subtitle loading and download
- ASS/SSA and VTT subtitle format parsing with enhanced cue handling
- Subtitle styling (font size, color, background, edge type, position) with persistence
- Subtitle delay offset control
- Preferred subtitle language selection
- Subtitle OCR via ML Kit for extracting text from video frames
- Community rating indicator for search results and subtitle download sheet

### Audio Player

- Music browsing for artists, albums, tracks, genres, and playlists
- Queue management with drag-to-reorder
- Shuffle and repeat modes
- Playback speed control
- Waveform-style seek bar
- Real-time FFT audio visualizer
- Synced and unsynced lyrics via LRCLIB API
- 10-band equalizer with presets
- Night Mode (loudness enhancement with configurable strength)
- Dialogue Boost (vocal frequency equalization with configurable strength)
- Audio normalization (ReplayGain) and channel mix modes
- Virtualizer (3D audio) and Reverb effect presets
- Gapless playback and crossfade between tracks
- Ambient Mode with animated color blobs derived from album art
- Sleep timer with configurable duration

### Music Discovery

- Smart playlists with criteria-based filtering (genre, artist, year, rating, play count, tags)
- Mood playlists with 10 presets (Happy Vibes, Chill Out, Energetic, Deep Focus, Workout, Melancholy, Romantic, Party Time, Sleep, Late Night Drive)
- Recently played, frequent artists, and recommended albums

### Library & Browsing

- Home sections: Continue Watching, Next Up, Recently Added, Latest, Favorites, and Surprise Me shuffle
- Library browsing with pagination and folder filtering
- Media detail pages with cast, crew, metadata, and related items
- Person detail pages with filmography browsing
- Collection/box set browsing

### Seerr Integration

- Jellyseerr and Overseerr connection support
- Discover trending, popular, and upcoming content
- Request content via Radarr/Sonarr directly from the app
- Seerr detail pages for unavailable media
- Region configuration for streaming and discover content

### Onboarding Wizard

- 10-step first-run setup wizard shown after initial authentication
- Appearance: Theme, dynamic theming, OLED mode, contrast level, home hero toggle
- Performance: Performance mode toggle for low-end devices
- Home Layout: Home mode (Video/Music), navigation bar labels, enabled home sections
- Video Player: Preferred engine, streaming quality, seek duration, gestures, orientation, autoplay
- Audio Player: Default speed, gapless playback, crossfade, normalization
- Subtitles: Subtitle style customization (font, size, color, background)
- Security: PIN lock, biometric lock, auto-lock timer
- Seerr: Server URL, API key, feature toggles, regions
- Re-accessible anytime from Settings

### Newsletter

- Weekly server digest showing library activity from the past 7 days
- Curated sections: Recently Added, Continue Watching, Next Up, Fresh Picks, Activity Digest, Library Stats
- Prominent banner on Home screen when new newsletter content is available
- Full grid view for each section with navigation to details
- Pull-to-refresh and configurable delivery schedule
- Accessible from Home screen and Settings

### Remote Control

- WebSocket-based remote control from Jellyfin server
- Receive Play, Pause, Seek, and general commands remotely
- Media browser service integration for third-party controller apps
- Active player management and remote playback reporting

### Metadata & Media Editor

- Directly accessible from the media detail screen for authorized/admin users
- Comprehensive metadata editing (title, original title, tagline, overview, premiere/release dates, sorting titles, and custom ratings)
- Rich artwork manager: Upload, update, or remove Primary, Backdrop, Banner, Logo, Art, Disc, and Thumb images
- Subtitle editor: View embedded subtitle tracks, upload external subtitle files, and delete unwanted external tracks

### Admin Dashboard

- Accessible directly via settings menu for server administrators
- Real-time system health monitor showing server status, CPU/Memory load, OS details, and server controls (restart/shutdown)
- Active User Sessions: View all active devices connected to the server, view session details, and end sessions remotely
- Library stats row: Direct overview of item counts across movies, series, episodes, albums, songs, and books
- Scheduled Tasks manager: Monitor, trigger, and cancel scheduled tasks on the Jellyfin server in real-time
- Server Logs viewer: Browse, view, and download active server logs with severity indicators and recent activity timelines
- Running tasks card tracking background operations with live progress updates
- User Statistics: Per-user playback history and statistics with charts
- Stale Media Scanner: Detect unwatched/stale media with background scan worker
- Watched Media Cleanup: Bulk cleanup of watched media with audit history log

### SyncPlay (Watch Parties)

- Create and join synchronized watch groups
- Real-time playback sync with speed-to-sync and skip-to-sync correction
- Server time synchronization for precise coordination
- In-player group chat
- Group settings for repeat and shuffle modes

### Live TV & DVR

- Live TV channel browsing with current program info
- Electronic Program Guide (EPG) with program timeline
- DVR recording management

### Downloads & Offline

- Video downloads via WorkManager with progress tracking
- Series downloads with episode selection
- Pause, resume, and retry support with HTTP Range resumption
- Offline playback for completed downloads
- Offline Library browser organized by series
- Offline Series view for downloaded episode browsing
- Foreground notification with speed and ETA

### Settings

- Player: engine selection, decoder mode, audio passthrough, orientation, seek duration, gesture toggles, autoplay, controls timeout, preload buffer
- Audio: default speed, gapless playback, crossfade, night mode, dialogue boost, equalizer, audio normalization, channel mix, virtualizer, reverb
- Subtitles: language, style, trickplay, intro/outro skip (manual and auto), tap-to-translate
- SyncPlay: progress reporting, auto-join, sync correction parameters
- Downloads: connections preference, max cache size (with unlimited/0 option support)
- Visual: dynamic theming, streaming quality, performance mode
- Security: PIN lock, biometric lock, auto-lock timer
- Kids: mode toggle, max content rating
- Screensaver: interval, Ken Burns effect, transition style, image categories, title overlay
- Newsletter: enable/disable, delivery day, notification badge
- Onboarding: re-run setup wizard anytime

---

## Tech Stack

| Category         | Technologies                                             |
| ---------------- | -------------------------------------------------------- |
| Language         | Kotlin                                                   |
| UI               | Jetpack Compose, Material 3, Material 3 Expressive       |
| TV               | Android TV Material, Leanback                            |
| Navigation       | Navigation 3                                             |
| DI               | Hilt                                                     |
| Storage          | Room, DataStore                                          |
| Background       | WorkManager, Coroutines, StateFlow                       |
| Video Players    | Media3/ExoPlayer, libmpv, LibVLC                         |
| Audio Effects    | Android Equalizer, LoudnessEnhancer, Virtualizer, Reverb |
| Media Session    | Media3 Session, Media3 Cast                              |
| Casting          | Google Play Services Cast Framework                      |
| Networking       | OkHttp, Jellyfin SDK                                     |
| Serialization    | kotlinx.serialization                                    |
| Images           | Coil 3 (with BlurHash support)                           |
| Text Recognition | ML Kit                                                   |
| Biometrics       | AndroidX Biometric                                       |
| Testing          | JUnit 4, Espresso, Compose UI Test, OkHttp MockWebServer |

---

## Requirements

- Android 9.0 (API 28) or later
- JDK 17
- Android Studio with Android SDK (compileSdk 37)
- A Jellyfin server for authentication and playback

---

## Building

<details>
<summary><strong>Prerequisites</strong></summary>

- Android Studio or Gradle CLI
- JDK 17
</details>

### Build Commands

```bash
# Debug builds (phone and TV)
./gradlew assemblePhoneDebug
./gradlew assembleTvDebug

# Release builds
./gradlew assemblePhoneRelease
./gradlew assembleTvRelease
```

The project uses product flavors — `phone` (standard mobile) and `tv` (Android TV with Leanback launcher). ABI splits produce `arm64-v8a`, `x86_64`, and universal APKs.

---

## CI/CD

A GitHub Actions workflow (`.github/workflows/release.yml`) automates release builds:

- Triggered on push to `v[0-9]+*` or `release/**` branches
- Auto-calculates version from branch name with git tag-based patch incrementing
- Builds signed release APKs for both phone and TV flavors
- Publishes APKs to GitHub Releases with auto-generated release notes

---

## Permissions

| Permission                          | Purpose                                       |
| ----------------------------------- | --------------------------------------------- |
| `INTERNET`                          | Jellyfin API access and media playback        |
| `ACCESS_NETWORK_STATE`              | Network connectivity monitoring               |
| `ACCESS_WIFI_STATE`                 | WiFi state for server discovery and streaming |
| `CHANGE_WIFI_MULTICAST_STATE`       | Jellyfin server auto-discovery                |
| `FOREGROUND_SERVICE`                | Foreground service for playback and downloads |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground service type        |
| `FOREGROUND_SERVICE_DATA_SYNC`      | Download worker foreground service type       |
| `POST_NOTIFICATIONS`                | Playback, download, and widget notifications  |
| `USE_BIOMETRIC`                     | Biometric authentication (fingerprint/face)   |

---

## Project Structure

```
app/                     Main Android application module
core/model/              Shared data models
core/designsystem/       Shared theming, artwork colors, and UI primitives
core/network/            Jellyfin API client, Seerr client, server discovery
core/database/           Room database and persistence
core/datastore/          DataStore preferences
core/data/               Repositories, playback managers, audio effects, SyncPlay, Cast, downloads
core/ui/                 Shared UI components, adaptive layouts, TV focus, animations, navigation
feature/auth/            Server selection and authentication
feature/onboarding/      First-run setup wizard (10-step preferences)
feature/home/            Home screen, Kids home, newsletter banner, and discover sections
feature/library/         Library browsing and media collections
feature/search/          Search experience
feature/details/         Media detail, person detail, collection detail, Seerr detail
feature/player/video/    Video playback UI, multi-engine support, SyncPlay integration
feature/player/audio/    Audio playback UI, lyrics, equalizer, ambient mode
feature/downloads/       Download management, offline library, and offline playback
feature/settings/        Settings, server/user management, Seerr configuration
feature/music/           Music browsing, smart/mood playlists, artist/album details
feature/livetv/          Live TV channels, EPG guide, and DVR
feature/syncplay/        SyncPlay group management and watch party UI
feature/editor/          Metadata, artwork, and subtitle editor for media items
feature/admin/           Admin dashboard for server status, active devices, logs, tasks, user stats
feature/newsletter/      Weekly server digest with library activity, curated picks, and stats
```

---

## Documentation

Looking for setup, integration, or troubleshooting guides?

- 📘 [Setup guide](./docs/setup.md) — install JellyPlay on any device
- 🎬 [Android TV & Fire TV setup](./docs/android-tv-setup.md) — sideload & install on the big screen
- 📡 [Jellyseerr/Seerr/Overseerr integration](./docs/seerr-integration.md) — request movies & shows from inside the app
- 👯 [SyncPlay watch parties](./docs/syncplay-guide.md) — synchronized group playback
- ⬇️ [Offline downloads](./docs/offline-downloads.md) — download media for travel
- 🎵 [Music player & synced lyrics](./docs/lyrics-music-player.md) — get the most out of your library

---

## See Also

Other open-source projects in the Jellyfin ecosystem:

- [jellyfin/jellyfin](https://github.com/jellyfin/jellyfin) — the free software media system
- [jellyfin/jellyfin-android](https://github.com/jellyfin/jellyfin-android) — the official Android client (web wrapper)
- [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) — the official Android TV client
- [jarnedemeulemeester/findroid](https://github.com/jarnedemeulemeester/findroid) — third-party native Android client
- [damontecres/Wholphin](https://github.com/damontecres/Wholphin) — OSS Android TV client
- [MakD/AFinity](https://github.com/MakD/AFinity) — modern Compose + LibMPV client
- [streamyfin/streamyfin](https://github.com/streamyfin/streamyfin) — cross-platform Expo client
- [fallensword/awesome-jellyfin](https://github.com/awesome-jellyfin/awesome-jellyfin) — curated list of Jellyfin plugins, themes & clients

---

## Contributing

We welcome contributions of all sizes — bug reports, feature requests, code,
translations, and documentation.

## License

This project is licensed under the **GNU General Public License v3.0** — see
the [`LICENSE`](LICENSE) file for details.

By contributing to JellyPlay, you agree that your contributions will be
licensed under the same GPL-3.0 license.
