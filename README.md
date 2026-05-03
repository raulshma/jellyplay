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

# Project Overview

JellyPlay is an Android Jellyfin application with multi-server support, adaptive layouts, modern playback controls, offline downloads, and a Compose-based UI.

</div>

---

## Features

### Platform & UI
- Multi-server Jellyfin support
- Token-based authentication
- Material 3 UI
- Dynamic theming from artwork
- Predictive back support
- Edge-to-edge immersive layouts
- Adaptive layouts for phone, tablet, and TV

### Search
- Global Jellyfin search across movies, shows, music, albums, and more
- Filters for genre, year, and media type
- Voice search support

### Video Player
- Direct play, direct stream, and transcoding support
- Resume playback and progress reporting
- Audio and subtitle track selection
- Subtitle styling and offset support
- Playback speed control
- Gesture controls for seek, brightness, and volume
- Chapter navigation
- Media session support for lock screen and notifications

### Subtitle System
- External subtitle loading
- Subtitle codec mapping for common formats
- Subtitle styling persistence
- Preferred subtitle language selection
- Dual subtitle support

### Audio Player
- Music browsing for artists, albums, tracks, and genres
- Queue management
- Shuffle and repeat modes
- Playback speed control
- Album art and playback progress display

### Library & Browsing
- Home sections such as Continue Watching, Next Up, Latest, and Favorites
- Library browsing with pagination and folder filtering
- Media detail pages with cast, crew, metadata, and related items
- Person detail pages with filmography browsing
- Live TV channel browsing and EPG guide support

### Downloads & Offline
- Video downloads with persistence and progress tracking
- Pause and resume support
- Offline playback for completed downloads

### Profiles & Parental Controls
- Multi-user support per server
- Kids mode content filtering
- PIN lock protection

### Notifications & System Integration
- Widgets for Now Playing and Continue Watching
- Quick settings tile launcher
- Foreground playback service
- Notification support for ongoing playback

### Special Features
- Smart playlists
- Mood playlists
- Night Mode Audio
- Dialogue Boost
- Ambient UI

---

## Tech Stack

**Android**
- Kotlin, Jetpack Compose, Material 3
- Navigation 3, Hilt, Room, DataStore, WorkManager
- Coroutines and StateFlow
- Media3 / ExoPlayer
- Jellyfin API integration
- Moshi for JSON serialization
- Coil for image loading
- Media session integration

---

## Requirements

- Android 8.0 (API 28) or later
- JDK 17
- Android Studio with a recent Android SDK
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
# Build the app
./gradlew assembleDebug

# Build a release variant
./gradlew assembleRelease
```

### Web UI Development

The repository currently does not include the separate web UI project from the reference repo, so there is no additional frontend build step here.

---

## CI/CD

A GitHub Actions workflow automates Android CI and release-related build tasks.

---

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Jellyfin API access and media playback |
| `FOREGROUND_SERVICE` | Keeping playback alive in the foreground |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback service operation |
| `POST_NOTIFICATIONS` | Playback and widget-related notifications |

---

## Project Structure

```
app/                     Main Android application module
core/model/              Shared data models
core/designsystem/        Shared theming and UI primitives
core/network/             Jellyfin networking and API access
core/database/            Room database and persistence
core/datastore/           DataStore preferences
core/data/                Repository implementations and data orchestration
core/ui/                  Shared UI helpers and components
feature/auth/             Server selection and authentication
feature/home/             Home screen and browse sections
feature/library/          Library browsing and media collections
feature/search/           Search experience
feature/details/          Media detail and related content screens
feature/player/video/     Video playback UI and controls
feature/player/audio/     Audio playback UI and controls
feature/downloads/        Download management and offline playback
feature/settings/         Settings and preferences UI
feature/music/            Music browsing and album/artist details
feature/livetv/           Live TV browsing and guide screens
```

---

## License

This project is licensed under the GNU General Public License v3.0 — see the `LICENSE` file for details.
