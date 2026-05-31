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
	<img alt="Status - Beta" src="https://img.shields.io/badge/Status-Beta-orange?style=for-the-badge" />
</p>

<div align="center">

### Modern, Open-Source Jellyfin Client for Android & Android TV

**JellyPlay** is a high-performance, feature-rich, and open-source **Jellyfin client app for Android** devices. Built entirely from the ground up using **Kotlin** and **Jetpack Compose** (Material 3/Expressive), JellyPlay delivers a premium, smooth, and native media streaming experience. Designed with an adaptive responsive interface, it adapts flawlessly to **Android mobile phones, tablets, foldables, and Android TV**.

Whether you want to stream movies, play music with synchronized lyrics, manage your server via an integrated **Admin Dashboard**, edit library metadata on the go, request content via **Jellyseerr/Overseerr**, or download media for **offline playback**, JellyPlay has you covered with built-in multi-engine support (**ExoPlayer, libmpv, LibVLC**) and real-time **SyncPlay watch parties**.

[Key Features](#features) • [Tech Stack](#tech-stack) • [Requirements](#requirements) • [Building & Flavors](#building) • [Permissions](#permissions) • [Project Structure](#project-structure)

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

## Features

### Platform & UI

- Multi-server Jellyfin support with auto-discovery
- Token-based and Quick Connect authentication
- Multi-user support with per-server user switching
- Material 3 UI with dynamic theming from artwork
- Expressive animations and transitions across music and media components using spring-based motion specifications
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
- Video filter controls: Adjust brightness, contrast, saturation, and sharpness in-player (libmpv & LibVLC)
- Enhanced Video Stats Overlay presenting real-time stream bitrate, frame rate, and dropped frames
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
- Trickplay thumbnail seeking (Jellyfin trickplay sprite sheets) with offline caching support to view seeking preview sprites during offline playback
- Frame rate matching for display refresh rate sync
- Adaptive bitrate streaming
- Intro skip and next episode auto-play
- Chromecast support via Google Cast SDK
- Media session integration for lock screen and notifications

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
- Downloads: connections preference, max cache size (with unlimited/0 option support)
- Visual: dynamic theming, streaming quality
- Security: PIN lock
- Kids: mode toggle, max content rating

---

## Tech Stack

| Category         | Technologies                                             |
| ---------------- | -------------------------------------------------------- |
| Language         | Kotlin                                                   |
| UI               | Jetpack Compose, Material 3, Material 3 Adaptive         |
| TV               | Android TV Material, Leanback                            |
| Navigation       | Navigation 3                                             |
| DI               | Hilt                                                     |
| Storage          | Room, DataStore                                          |
| Background       | WorkManager, Coroutines, StateFlow                       |
| Video Players    | Media3/ExoPlayer, libmpv, LibVLC                         |
| Audio Effects    | Android Equalizer, LoudnessEnhancer                      |
| Media Session    | Media3 Session, Media3 Cast                              |
| Casting          | Google Play Services Cast Framework                      |
| Networking       | OkHttp, Jellyfin SDK                                     |
| Serialization    | kotlinx.serialization                                    |
| Images           | Coil (with BlurHash support)                             |
| Text Recognition | ML Kit                                                   |
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
feature/editor/          Metadata, artwork, and subtitle editor for media items
feature/admin/           Admin dashboard for server status, active devices, logs, and tasks
```

---

## License

This project is licensed under the GNU General Public License v3.0 — see the `LICENSE` file for details.
