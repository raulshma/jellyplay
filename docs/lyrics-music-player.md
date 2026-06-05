# Music player & synced lyrics

JellyPlay isn't just for video — the audio player is a first-class
experience for music lovers. From synchronized lyrics to a 10-band
equalizer, here's how to get the most out of your music library.

## Music home

The **Music** home section is a Spotify-style browse experience with:

- **Continue listening** — pick up where you left off in long mixes
- **Recently played** — quick access to your last 50 albums and tracks
- **Frequent artists** — surface the artists you actually listen to
- **Recommended albums** — based on your top genres and play history
- **Mood playlists** — 10 curated presets (see below)
- **Smart playlists** — criteria-based playlists you can fully customise

To switch the home to **music-first** layout, open
**Settings → Onboarding → Home Layout** (or in
**Settings → Home** on the fly).

## Mood playlists

JellyPlay ships with 10 mood presets that auto-generate from your
library:

| Mood | What it picks |
| ---- | ------------- |
| 😄 Happy Vibes | High-energy, major-key, recently added |
| 🛋️ Chill Out | Acoustic, downtempo, low BPM |
| ⚡ Energetic | Electronic, dance, high BPM |
| 🧠 Deep Focus | Instrumental, ambient, no lyrics |
| 💪 Workout | 140+ BPM, electronic / hip-hop / rock |
| 😢 Melancholy | Minor key, slow, low energy |
| 💖 Romantic | Love songs, mid-tempo, classic |
| 🎉 Party Time | Dance, pop, high energy |
| 😴 Sleep | Ambient, classical, <60 BPM |
| 🌃 Late Night Drive | Synthwave, lo-fi, chillhop |

You can edit any of these in **Music → Mood Playlists → ⋯ → Edit**
to add / remove tracks or change the seed criteria.

## Smart playlists

Build your own playlists with rule-based criteria:

- **Match all** of: Genre is *Rock* AND Year is *≥ 2010*
- **Match any** of: Tag is *workout* OR Tag is *cardio*
- **Sort by**: Play count (descending), Recently added, Random shuffle
- **Limit**: Top 50, 100, 250, or unlimited
- **Auto-refresh**: rebuild on app launch, on library scan, or manually

Smart playlists update in real time when you import new music or
JellyPlay detects a library scan.

## Synced lyrics

JellyPlay fetches **time-synced lyrics** from the
[LRCLIB](https://lrclib.net/) open-source database. When lyrics are
available, they appear in a karaoke-style view with the current line
highlighted.

- 🟢 **Synced lyrics** — words highlight in time with playback
- 🟡 **Plain lyrics** — unsynced lyrics display as a static text
- 🔴 **No lyrics** — the lyrics button is hidden

Tap the **Lyrics** icon in the audio player to toggle the view. Tap
any line to seek to that point in the track.

On the audio player's **Karaoke Mode** (in the more menu), the lyrics
take the full screen and the album art fades to a background blur.

## Equalizer & audio effects

Open **Settings → Audio** to access:

### 10-band equalizer

- 10 frequency bands (32 Hz to 16 kHz)
- 12 presets: Flat, Bass Boost, Treble Boost, Vocal, Rock, Pop, Jazz,
  Classical, Electronic, Hip-Hop, Acoustic, Vocal Booster
- **Save as custom** — name your own preset

### Night Mode

`LoudnessEnhancer`-based compression that makes quiet sections louder
and loud sections quieter. Ideal for late-night listening when you
don't want to wake the house. Strength: Low / Medium / High.

### Dialogue Boost

Equalizer pre-shape that emphasizes vocal frequencies (1-4 kHz) to
make podcasts and audiobooks more intelligible. Strength: Low / Medium
/ High.

### Audio normalization (ReplayGain)

Matches perceived volume across tracks so you don't have to constantly
adjust the volume when an old quiet album is followed by a modern loud
one. Supports both track-level and album-level ReplayGain tags, with
a fallback to computed loudness analysis.

### Channel mix

- Stereo (default)
- Mono (useful for one-ear listening)
- Invert left / invert right (great for verifying channel mapping)

### Virtualizer & Reverb

3D-audio virtualizer with 5 strength levels, plus 4 reverb presets
(Room, Hall, Cathedral, Outdoor) for headphones.

### Gapless playback & crossfade

- **Gapless** — for classical and concept albums where silence
  between tracks is wrong. JellyPlay pre-buffers the next track.
- **Crossfade** — fade-out the current track while fading-in the
  next, with a configurable duration (1-12 seconds)

## Ambient Mode

While playing music, tap the **⋯** menu and select **Ambient Mode**.
The album art scales up to a full-screen background with animated
color blobs derived from the artwork's dominant colors, perfect for
party or focus backgrounds. Tap anywhere to exit.

## Sleep timer

From the audio player, tap the moon icon to set a sleep timer:

- 5, 10, 15, 30, 45, 60 minutes
- End of current track
- End of current album
- Custom duration

JellyPlay fades out smoothly in the last 10 seconds.

## Widgets & shortcuts

JellyPlay adds two home-screen widgets:

- **Now Playing** — large album art + title / artist / playback
  controls (4x2 size)
- **Continue Listening** — 4 most recently played tracks, tap to
  resume

App shortcuts (long-press the launcher icon):

- Continue watching
- Search
- Play music
- Downloads
- Continue listening

## Next steps

- 🎬 [Configure the video player engines →](./player-engines.md)
- ⬇️ [Download music for offline listening →](./offline-downloads.md)
- ⚖️ [JellyPlay vs Plex vs Emby vs Kodi →](./why-jellyplay-vs-plex-emby.md)
