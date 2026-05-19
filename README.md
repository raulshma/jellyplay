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
</p>

</br>

<div align="center">

JellyPlay is a modern Android Jellyfin client with multi-server support, adaptive layouts, multi-engine playback, offline downloads, SyncPlay watch parties, and a fully Compose-based UI — optimized for phones, tablets, and Android TV.

</div>

---

## Features

### Platform & UI
- Multi-server Jellyfin support with auto-discovery
- Token-based and Quick Connect authentication
- Multi-user support with per-server user switching
- Material 3 UI with dynamic theming from artwork
- Predictive back gesture support
- Edge-to-edge immersive layouts
- Adaptive layouts for phone, tablet, foldable, and TV
- Android TV support with D-pad navigation and Leanback launcher
- PIN lock protection and Kids Mode with content filtering
- Home screen widgets (Now Playing, Continue Watching)
- Quick settings tile launcher

### Search
- Global Jellyfin search across movies, shows, music, albums, and more
- Filters for genre, year, and media type
- Voice search support

### Video Player
- **Three built-in engines**: ExoPlayer (Media3), libmpv, and LibVLC
- External player launching (MX Player, VLC, etc.)
- Direct play, direct stream, and transcoding support
- Resume playback and progress reporting
- Audio and subtitle track selection
- Playback speed control
- Gesture controls for seek, brightness, and volume
- Chapter and episode navigation
- HDR badge indicator
- Picture-in-Picture support
- Mini player overlay
- Trickplay thumbnail seeking (Jellyfin trickplay sprite sheets)
- Frame rate matching for display refresh rate sync
- Adaptive bitrate streaming
- Intro skip and next episode auto-play
- Chromecast support via Google Cast SDK
- Media session integration for lock screen and notifications

### Subtitle System
- External subtitle loading and download
- ASS/SSA subtitle format parsing
- Subtitle styling (font size, color, background, edge type, position) with persistence
- Subtitle delay offset control
- Preferred subtitle language selection
- Subtitle OCR via ML Kit for extracting text from video frames

### Audio Player
- Music browsing for artists, albums, tracks, genres, and playlists
- Queue management with drag-to-reorder
- Shuffle and repeat modes
- Playback speed control
- Waveform-style seek bar
- Synced and unsynced lyrics via LRCLIB API
- 10-band equalizer
- Night Mode (loudness enhancement with configurable strength)
- Dialogue Boost (vocal frequency equalization with configurable strength)
- Audio normalization and channel mix modes
- Ambient Mode with animated color blobs derived from album art

### Music Discovery
- Smart playlists with criteria-based filtering (genre, artist, year, rating, play count, tags)
- Mood playlists with 10 presets (Happy Vibes, Chill Out, Energetic, Deep Focus, Workout, Melancholy, Romantic, Party Time, Sleep, Late Night Drive)
- Recently played, frequent artists, and recommended albums

### Library & Browsing
- Home sections: Continue Watching, Next Up, Latest, Favorites, and Surprise Me shuffle
- Library browsing with pagination and folder filtering
- Media detail pages with cast, crew, metadata, and related items
- Person detail pages with filmography browsing
- Collection/box set browsing

### Seerr Integration
- Jellyseerr and Overseerr connection support
- Discover trending, popular, and upcoming content
- Request content via Radarr/Sonarr directly from the app
- Seerr detail pages for unavailable media

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
- Pause, resume, and retry support with HTTP Range resumption
- Offline playback for completed downloads
- Foreground notification with speed and ETA

### Settings
- Player: engine selection, decoder mode, audio passthrough, orientation, seek duration, gesture toggles, autoplay, controls timeout, preload buffer
- Audio: default speed, night mode, dialogue boost, equalizer, audio normalization, channel mix
- Subtitles: language, style, trickplay, intro/outro skip (manual and auto)
- SyncPlay: progress reporting, auto-join, sync correction parameters
- Visual: dynamic theming, streaming quality
- Security: PIN lock
- Kids: mode toggle, max content rating

---

## Tech Stack

| Category | Technologies |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Material 3 Adaptive |
| TV | Android TV Material, Leanback |
| Navigation | Navigation 3 |
| DI | Hilt |
| Storage | Room, DataStore |
| Background | WorkManager, Coroutines, StateFlow |
| Video Players | Media3/ExoPlayer, libmpv, LibVLC |
| Audio Effects | Android Equalizer, LoudnessEnhancer |
| Media Session | Media3 Session, Media3 Cast |
| Casting | Google Play Services Cast Framework |
| Networking | OkHttp, Jellyfin SDK |
| Serialization | kotlinx.serialization |
| Images | Coil (with BlurHash support) |
| Text Recognition | ML Kit |
| Testing | JUnit 4, Espresso, Compose UI Test, OkHttp MockWebServer |

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

| Permission | Purpose |
|---|---|
| `INTERNET` | Jellyfin API access and media playback |
| `ACCESS_NETWORK_STATE` | Network connectivity monitoring |
| `ACCESS_WIFI_STATE` | WiFi state for server discovery and streaming |
| `CHANGE_WIFI_MULTICAST_STATE` | Jellyfin server auto-discovery |
| `FOREGROUND_SERVICE` | Foreground service for playback and downloads |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground service type |
| `FOREGROUND_SERVICE_DATA_SYNC` | Download worker foreground service type |
| `POST_NOTIFICATIONS` | Playback, download, and widget notifications |

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
feature/home/            Home screen, Kids home, and discover sections
feature/library/         Library browsing and media collections
feature/search/          Search experience
feature/details/         Media detail, person detail, collection detail, Seerr detail
feature/player/video/    Video playback UI, multi-engine support, SyncPlay integration
feature/player/audio/    Audio playback UI, lyrics, equalizer, ambient mode
feature/downloads/       Download management and offline playback
feature/settings/        Settings, server/user management, Seerr configuration
feature/music/           Music browsing, smart/mood playlists, artist/album details
feature/livetv/          Live TV channels, EPG guide, and DVR
feature/syncplay/        SyncPlay group management and watch party UI
```

---

## License

This project is licensed under the GNU General Public License v3.0 — see the `LICENSE` file for details.
