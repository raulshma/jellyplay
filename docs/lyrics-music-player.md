# Music player & synced lyrics

JellyPlay isn't just for video — the audio player is a first-class
experience for music lovers. From synchronized lyrics to a 10-band
equalizer, here's how to get the most out of your music library.

## Music home

The **Music** home section is a Spotify-style browse experience with:

- **Favorite Artists** — the artists you actually listen to
- **Latest Albums** — newest additions to your music library
- **Recently played** — quick access to what you last streamed
- **Top Rated Albums** — your highest-rated records
- **Favorite Tracks** — the songs you've hearted

Mood and smart playlists live on their own screens, reachable from the
music browse tabs (see below).

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

## Smart playlists

Build your own playlists with rule-based criteria:

- **Criteria** — combine rules like Genre is *Rock*, Year is
  *≥ 2010*, or Tag is *workout*; every rule must match
- **Sort by**: Play count (descending), Recently added, Random shuffle
- **Limit**: cap the result count (50 by default)

Results are computed from your live library, so a smart playlist always
reflects the music currently on your server.

## Endless radio (auto-mix)

Seed an endless mix from **any track, album, or artist** — the ⋮ menu's
"Start radio" on a music detail screen. JellyPlay plays a Jellyfin
instant mix for the seed, then keeps the music going: when you're within
3 tracks of the queue's end it fetches a fresh batch (up to 20 tracks
you haven't heard in this radio yet) and appends it, so playback never
runs dry at the end of the queue.

Details worth knowing:

- The active radio shows a **chip in the queue sheet**; tap it to stop
  the radio (the current queue keeps playing — it just stops growing).
- Starting any new queue (another album, playlist, or mix) silently
  ends the radio — a radio never refills a queue it didn't seed.
- Offline servers deactivate the radio after 3 failed refills in a row
  instead of hammering the connection.

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

- 10 frequency bands (60 Hz to 16 kHz)
- 13 presets: Flat, Bass Boost, Treble Boost, Rock, Pop, Jazz,
  Classical, Electronic, Hip Hop, Vocal, Acoustic, Podcast, Latin
- **Save as custom** — name your own preset

### Night Mode

`LoudnessEnhancer`-based compression that makes quiet sections louder
and loud sections quieter. Ideal for late-night listening when you
don't want to wake the house. Strength: Off / Low / Moderate / High.

### Dialogue Boost

Equalizer pre-shape that emphasizes vocal frequencies (1-4 kHz) to
make podcasts and audiobooks more intelligible. Toggle it from the
audio player's menu. Strength: Off / Low / Moderate / High.

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

3D-audio virtualizer (Off / Low / Moderate / High strength), plus six
reverb presets (Small Room, Medium Room, Large Room, Medium Hall,
Large Hall, Plate) for headphones.

### Gapless playback & crossfade

- **Gapless** — for classical and concept albums where silence
  between tracks is wrong. JellyPlay pre-buffers the next track.
- **Crossfade** — fade-out the current track while fading-in the
  next, with a duration of Off, 2, 3, 5, 8, or 12 seconds

## Ambient Mode

While playing music, tap the **⋯** menu and select **Ambient Mode**.
The album art scales up to a full-screen background with animated
color blobs derived from the artwork's dominant colors, perfect for
party or focus backgrounds. Tap anywhere to exit.

## Sleep timer

From the audio player, tap the moon icon to set a sleep timer:

- 15, 30, 45, 60, or 90 minutes
- End of episode

JellyPlay fades out smoothly in the last 10 seconds.

## Widgets & shortcuts

JellyPlay adds four home-screen widgets:

- **Now Playing** — album art + title / artist / playback controls
  (4x1 size)
- **Continue Watching** — video items you're mid-watch on (4x2 size)
- **Library Recommendations** — suggested picks from your library
- **Seerr Recommendations** — trending picks via your Seerr instance

App shortcuts (long-press the launcher icon):

- Continue watching
- Search
- Play music
- Downloads
- Surprise me
- Settings

After you listen, a dynamic **Continue listening** shortcut appears
too, resuming your last audio session.

## Next steps

- 🎬 [Configure the video player engines →](./player-engines.md)
- ⬇️ [Download music for offline listening →](./offline-downloads.md)
