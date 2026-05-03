# JellyPlay

JellyPlay is a modern Android Jellyfin client built with Kotlin, Jetpack Compose, and a modular architecture. It focuses on a polished media browsing and playback experience across phone and TV form factors.

## Project Overview

JellyPlay is structured as a multi-module Android app with shared core layers, feature modules, and a Compose-based UI stack.

The app currently targets:

- Android phones with a bottom navigation experience
- TV devices with a D-pad-friendly experience
- Adaptive layouts for larger screens

## Features

### Platform & UI

- Multi-server Jellyfin support
- Token-based authentication
- Material 3 Expressive UI
- Dynamic theming from media artwork
- Predictive back gestures
- Edge-to-edge immersive layouts
- Adaptive layouts for phone, tablet, and TV

### Search & Browse

- Global Jellyfin search across movies, shows, music, albums, and more
- Filters for genre, year, and media type
- Home sections such as Continue Watching, Next Up, Latest, and Favorites
- Library browsing with pagination and folder filtering

### Video Player

- Direct play, direct stream, and transcoding support
- Resume playback and progress reporting
- Audio track selection
- Subtitle selection and styling
- Playback speed control
- Gesture controls for seek, brightness, and volume
- Chapter navigation
- Media session support for lock-screen and notification controls

### Audio Player

- Music browsing for artists, albums, tracks, and genres
- Queue management
- Shuffle and repeat modes
- Playback speed control
- Album art and progress display

### Downloads & Offline

- Video downloads with persistence and progress tracking
- Pause and resume support
- Offline playback of completed downloads

### Library & Live TV

- Media detail pages with cast, crew, metadata, and related items
- Person detail pages with filmography browsing
- Live TV channel browsing
- EPG guide support

### System & Quality-of-Life

- DataStore-backed preferences
- Room persistence for servers, users, and downloads
- Widgets for Now Playing and Continue Watching
- Quick settings tile launcher
- PIN lock protection
- Kids mode content filtering
- Smart playlists and mood playlists
- Night Mode Audio and Dialogue Boost

## Tech Stack

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Navigation 3
- Hilt
- Room
- DataStore
- WorkManager
- Coroutines and StateFlow
- Media3 / ExoPlayer

### Networking & Media

- Jellyfin API integration
- Moshi for JSON serialization
- Coil for image loading
- Media session integration

### Architecture

- Multi-module design
- MVVM-style state handling
- Core/data/domain-style separation through shared modules

## Requirements

- Android 8.0 (API 28) or later
- JDK 17
- Android Studio with a recent Android SDK
- A Jellyfin server for authentication and playback

## Building

### Build Commands

Build the app with Gradle:

```bash
./gradlew assembleDebug
```

Build a release variant:

```bash
./gradlew assembleRelease
```

## Project Structure

```text
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

## CI/CD

The repository includes GitHub Actions workflows for automated build and release tasks.

## License

This project is licensed under the GNU General Public License v3.0. See the `LICENSE` file for details.
