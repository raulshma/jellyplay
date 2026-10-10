# Choosing a video engine

JellyPlay bundles **three independent video engines** that you can switch
between on a per-device basis. This guide explains the trade-offs so you
can pick the right one for your content, device, and network.

> **TL;DR** — ExoPlayer is the default and the right choice for almost
> everyone. Switch to **libmpv** for anime / exotic files / demanding
> color work, or **LibVLC** as a fallback for unusual containers and
> network streams. Live TV and background music use dedicated engines
> that are not user-switchable (see [Other playback paths](#other-playback-paths)).

## The three engines at a glance

| Engine | Strengths | Best for |
| ------ | --------- | -------- |
| **ExoPlayer / Media3** *(default)* | Best HLS / DASH streaming, low latency, modern codec support, official AndroidX | Streaming services-style content, modern TVs, fast networks |
| **libmpv** | Best codec coverage, ASS/SSA subtitle rendering, shader support, frame-accurate seeking | Anime / fansubs, exotic codecs, low-end devices, color-graded content |
| **LibVLC** | Broad format support, hardware decoding on most chipsets, well-tested | Legacy files, MKV containers with weird tracks, network streams (SMB, NFS) |

## ExoPlayer / Media3

[Media3 / ExoPlayer](https://developer.android.com/media/media3) is
Google's official media playback library for Android. It's the default
engine in JellyPlay and the best choice for most users.

**Pros**

- ✅ HLS, DASH, SmoothStreaming, RTSP out of the box
- ✅ Hardware decoding with the FFmpeg extension (DTS, TrueHD, E-AC-3)
- ✅ Built-in `MediaSession` integration for lock screen and
  notification controls
- ✅ Excellent track selection, gapless playback, and trickplay
- ✅ Maintained by Google, frequent updates
- ✅ Adaptive bitrate (live quality switch + bandwidth estimate)
- ✅ In-sink audio DSP: channel mix, dynamics, ReplayGain/normalization,
  dialogue boost, night mode

**Cons**

- ❌ Less flexible than mpv for shader-based post-processing
- ❌ No user style override on ASS/SSA subtitles (only SRT/VTT)
- ❌ No audio passthrough, no audio delay tweak
- ❌ Some niche codecs (RV40, VP6) are not supported

**Best for:** 99% of users. Use this unless you have a specific reason
not to.

## libmpv

[libmpv](https://github.com/mpv-player/mpv) is the Android port of the
legendary [mpv](https://mpv.io/) player. JellyPlay bundles the
`io.github.abdallahmehiz:mpv-android-lib` packaging by AbdallahMehiz.

**Pros**

- ✅ Plays *anything* — every codec mpv supports, including niche ones
- ✅ Frame-accurate seeking
- ✅ Best-in-class **ASS/SSA** subtitle rendering — including user style
  override (`--ass-override=force`)
- ✅ Advanced configuration via the in-app *Advanced MPV Configuration*
  (Android additionally reads `<app files>/mpv/mpv.conf`)
  - **Subtitle precedence:** any `sub-*` styling key you set in the
    *Advanced MPV Configuration* (or, on Android, in `mpv.conf`) wins —
    the app skips writing that key, at startup and on live subtitle-style
    changes alike. Example: `sub-color="#FF00FF00"` in your conf keeps your
    green captions even with custom subtitle styling enabled in-app.
    **Quoting:** in a hand-written `mpv.conf`, mpv treats everything after
    an unquoted `#` as a comment — color values like `sub-color=#FF0000`
    parse as empty and are silently dropped, so wrap them in quotes
    (`sub-color="#FF0000"`). Values in the in-app *Advanced MPV
    Configuration* go straight to mpv's option API, where `#` is not
    special — but quotes are **not** stripped there: they become part of
    the value, so write values unquoted (`sub-color=#FF0000`).
    **ASS/SSA subtitles:** style keys like `sub-color` only
    affect ASS subs when your config also sets `sub-ass-override=force`
    (which also stops the app's default `sub-ass-override=scale` write);
    with `scale` or `yes`, ASS subs keep their embedded styling.
    Functional keys the player drives at runtime (`sub-visibility`,
    `sub-delay`, `secondary-sub-delay`, `sub-use-margins`,
    `sub-ass-force-margins`, and the font-provider options) stay app-owned
    so in-app subtitle toggle/sync keep working.
    **In-app notice:** when a custom config participates, the subtitle
    hub's *Style* tab (and the standalone subtitle tester) show a notice
    card listing the owned keys, the ASS-track case (ASS subs keep their
    embedded styling; the card points at the *ASS Styling* choice, and
    calls out when the config itself owns `sub-ass-override`), the
    app-owned keys it names (subtitle on/off and subtitle delay — the
    rest of the always-app-owned functional set is enforced without being
    listed), and — when
    mpv.conf values were dropped by the parser — the quoting fix. The card
    reads the same ownership snapshot the engines gate their writes with
    (`MediaEngine.subtitleStyleOwnership`), so it can never disagree with
    what is actually skipped.
  - **Platform note:** the desktop builds run mpv with `config=no`, so
    `~/.config/mpv/mpv.conf` is not read there — only the in-app
    *Advanced MPV Configuration* participates in the precedence above.
- ✅ Shader packs (Anime4K, FSRCNNX, etc.) for upscaling and
  deinterlacing
- ✅ SVP (Smooth Video Project) integration for frame interpolation
- ✅ Audio passthrough and audio-delay control
- ✅ Per-frame video filters (the only engine that exposes both audio
  *and* video runtime filters alongside full audio effects)

**Cons**

- ❌ Slightly higher CPU usage (less aggressive hardware decoding)
- ❌ Larger APK size (~25 MB)
- ❌ No built-in `MediaSession` (JellyPlay provides its own)
- ❌ Trickplay thumbnails load slightly slower
- ❌ No adaptive bitrate / live quality switching
- ❌ No mini-player mode

**Best for:** anime fansub enthusiasts, low-end devices (surprisingly
often *more* stable than ExoPlayer on weak hardware), users with
exotic media files, and anyone who loves mpv's color management.

## LibVLC

[LibVLC](https://www.videolan.org/vlc/libvlc.html) is the embedded
engine from the VLC media player.

**Pros**

- ✅ Excellent format support (containers and codecs)
- ✅ Mature, well-tested code
- ✅ Hardware decoding on a wide range of chipsets
- ✅ Network streaming (SMB, NFS, SFTP, HTTP, FTP) built-in
- ✅ Brightness / contrast / saturation filter controls
  (in JellyPlay's player)
- ✅ Audio passthrough and audio-delay control
- ✅ Native renderer (cast) item support

**Cons**

- ❌ No runtime audio effects — dialogue boost, night mode,
  normalization, and channel mixing are unavailable (LibVLC exposes no
  audio session for live effects; only startup `--audio-filter`s)
- ❌ Limited subtitle styling: no free-form colors, no border-style
  options, no ASS override
- ❌ Trickplay sprite sheets are not natively supported
- ❌ No cue support, no mini-player mode
- ❌ Less frequent Android-specific updates
- ❌ Some streaming features (like HLS LL-HLS) are not as smooth as
  ExoPlayer

**Best for:** users with media files in unusual containers, network
stream playback, and anyone who wants the "VLC experience" on Android.

## Capability comparison

The table below is the authoritative feature set for each engine. When a
cell is ✗, the engine silently ignores the related control (so the
setting simply won't appear in the player UI).

| Capability | ExoPlayer | libmpv | LibVLC | External |
| ---------- | :-------: | :----: | :----: | :------: |
| Picture-in-Picture | ✅ | ✅ | ✅ | ✗ |
| Mini-player mode | ✅ | ✗ | ✗ | ✗ |
| Chapter cues | ✅ | ✅ | ✗ | ✗ |
| Audio delay | ✗ | ✅ | ✅ | ✗ |
| Subtitle delay | ✅ | ✅ | ✅ | ✗ |
| Audio passthrough (SPDIF/HDMI) | ✗ | ✅ | ✅ | ✗ |
| Subtitle style (SRT/VTT) | ✅ | ✅ | ✅ | ✗ |
| Subtitle vertical position | ✅ | ✅ | ✅ | ✗ |
| ASS/SSA *rendering* | ✅ | ✅ | ✗ | ✗ |
| ASS/SSA *user style override* | ✗ | ✅ | ✗ | ✗ |
| Image subtitles (PGS/VobSub sidecars) | ✗ | ✅ | ✗ | ✗ |
| Font family | ✅ | ✅ | ✅ | ✗ |
| Free-form subtitle colors | ✗ | ✅ | ✗ | ✗ |
| Border styles | ✅ | ✅ | ✗ | ✗ |
| Dialogue boost | ✅ | ✅ | ✗ | ✗ |
| Night mode | ✅ | ✅ | ✗ | ✗ |
| Audio normalization | ✅ | ✅ | ✗ | ✗ |
| Channel mixing | ✅ | ✅ | ✗ | ✗ |
| Video filters (brightness/contrast/etc.) | ✗ | ✅ | ✅ | ✗ |
| Live quality switch (ABR) | ✅ | ✗ | ✗ | ✗ |
| Bandwidth estimate | ✅ | ✗ | ✗ | ✗ |

**Subtitle nuance.** ExoPlayer and mpv both *render* ASS/SSA subtitles.
Only **mpv** also applies *your* style overrides (colors, borders,
Force) on top of ASS/SSA tracks (`--ass-override=force`). ExoPlayer
renders ASS as-authored — your style overrides take effect on SRT/VTT
only. LibVLC does not render ASS at all. For the same reason, the
free-form (hex) color picker is offered on **libmpv only**: it requires
ASS user style override so the picked color also holds on ASS tracks,
not just SRT/VTT.

Image subtitles delivered as files (bitmap PGS `.sup`, VobSub) play on
mpv only, via its libav decoders — this gates offline side-loading:
downloads bundle such sidecars, but engines without the capability skip
them at playback instead of failing silently at render time. Embedded
image tracks are unaffected: direct-play hands them to the engine's own
demuxer (again mpv-only), and transcode burn-in serves every engine.
VobSub is bundled as the full `.idx`+`.sub` pair (the palette alone or
the bitmap alone renders nothing); PGS `.sup` sidecars are
self-contained.

## How to switch engines

1. Open **Settings → Playback → Player Engine**
   (labeled **Preferred Player**)
2. Pick **ExoPlayer**, **mpv**, or **LibVLC**
3. Tap to apply — JellyPlay reloads the player

The choice is **per-device and manual** — JellyPlay does not auto-pick
an engine based on the file's codec or stream type. You can use
ExoPlayer on your phone and libmpv on your NVIDIA Shield at the same
time. If you hit a file no bundled engine handles well, use an
[external player](#external-player) instead of switching engines.

> **No silent fallback.** If the chosen engine fails to decode a file,
> JellyPlay reports the error rather than secretly swapping engines
> behind your back. Re-resolving the stream (Direct Play ↔ Transcode)
> reuses the *same* engine type.

## Per-engine tuning (advanced)

Below the engine picker, **Settings → Playback → Engine Config** exposes
knobs specific to the active engine. Defaults are sane for almost
everyone; reach for these only if a particular file misbehaves.

- **ExoPlayer** — video scaling mode, frame-rate strategy, preferred
  video MIME types, skip-silence, audio offload mode, back-buffer
  duration, decoder fallback.
- **libmpv** — video output (`gpu-next` default), scaler (bilinear
  default),
  hardware-decode override, audio output, demuxer max bytes, frame drop,
  skip-loop-filter, interpolation, deband.
- **LibVLC** — audio output, video output, network caching,
  time-stretch, skip-loop-filter, decoder threads, drop-late-frames.

> On low-RAM devices, JellyPlay automatically trims the mpv / VLC
> demuxer buffer — no setting to change.

## External player

If none of the bundled engines work for a specific file, JellyPlay can
hand playback to an **external player** (MX Player, VLC, mpv for
Android, etc.):

1. Set **Settings → Playback → Preferred Player → External** — you can
   also pick your preferred external app there
2. Start playback on any video; JellyPlay launches the external app
   with the stream and steps aside

Internally this selects the **External** engine type, which is a no-op
in-app placeholder — playback happens entirely in the third-party app.
JellyPlay still reports the playback progress back to your Jellyfin
server via the session, even when using an external player.

## Hardware vs. software decoding

Each engine has a **decoder mode** setting in **Settings → Playback →
Decoder**:

- **Hardware** *(default)* — uses the device's GPU/DSP for video
  decoding. Lower battery, smoother 4K HDR.
- **Software** — CPU-only decoding. Needed for some exotic codecs or
  when hardware decoders have bugs on specific devices.

This setting is honored by all three engines. If you see **green
artifacts**, **macroblocking**, or **black frames** on certain files,
try switching to software decoding.

## Recommended combinations

| Use case | Engine | Decoder | Notes |
| -------- | ------ | ------- | ----- |
| **Streaming services-style content (HLS/DASH)** | ExoPlayer | Hardware | Best perf, lowest battery |
| **Anime fansubs with .ass subtitles** | libmpv | Hardware | Best ASS rendering + style override |
| **Local 4K HDR remuxes** | ExoPlayer | Hardware | Direct play, frame-rate matching |
| **Low-end TV box (ONN, generic)** | libmpv | Hardware | Often more stable than ExoPlayer |
| **Exotic MKV with audio in AC-3** | LibVLC | Hardware | Best fallback |
| **Slow WiFi, stutter issues** | ExoPlayer | Hardware | Adaptive bitrate (HLS/DASH) |
| **Color-graded cinema (REC.2020, HDR10+)** | libmpv | Hardware | mpv's tone mapping is excellent |
| **Screencasts / unusual framerates** | libmpv | Hardware | mpv handles weird framerates well |
| **Offline downloaded content** | ExoPlayer | Hardware | Lowest CPU usage on plane mode |
| **Tweaking audio sync / passthrough** | libmpv or LibVLC | Hardware | ExoPlayer has no audio delay control |
| **Network share playback (SMB/NFS)** | LibVLC | Hardware | Native network demuxers |

## Subtitle Style Tester

A standalone screen (`:shared:feature:subtitle-tester`) lets you preview subtitle
styling with a switchable-engine live render over a bundled color host clip,
then **Apply** the config to your real preference. Because subtitle rendering
differs across engines (e.g. only mpv honours full ASS style override), the
tester lets you see how the same config renders on each engine before
committing it.

**Open it from:**
- **Settings → Language & Subtitles → Subtitle style tester**, or
- The **in-player Subtitle Style sheet → Open tester** button.

The tester runs fully offline (bundled sample clip + sample subtitle presets)
and never disturbs your running playback — it owns its own isolated engine
instance.

## Other playback paths

The three switchable engines above cover **on-demand video**. Two other
playback paths exist and are **not** user-switchable:

- **Live TV** uses a dedicated live engine (Media3 ExoPlayer — HLS for
  transcodes, progressive TS for direct streams — always joins at the
  live edge) regardless of your Preferred Player
  setting. If a live stream can't direct-play, it falls back to a
  transcode path automatically.
- **Background music / audio** runs on its own Media3
  `MediaLibraryService` with a separate audio-only player, lock-screen
  controls, and the full audio-effects chain. It does not use the video
  engine tree at all.

## Next steps

- 🚀 [Install JellyPlay →](./setup.md)
- 📺 [Set up JellyPlay on Android TV →](./android-tv-setup.md)
- ⬇️ [Configure offline downloads →](./offline-downloads.md)
