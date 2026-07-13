# Choosing a video engine

JellyPlay bundles **three independent video engines** that you can switch
between on a per-device basis. This guide explains the trade-offs so you
can pick the right one for your content, device, and network.

## The three engines at a glance

| Engine | Strengths | Best for |
| ------ | --------- | -------- |
| **ExoPlayer / Media3** | Best HLS / DASH streaming, low latency, modern codec support, official AndroidX | Streaming services-style content, modern TVs, fast networks |
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

**Cons**

- ❌ Less flexible than mpv for shader-based post-processing
- ❌ Limited ASS/SSA subtitle styling on some Android versions
- ❌ Some niche codecs (RV40, VP6) are not supported

**Best for:** 99% of users. Use this unless you have a specific reason
not to.

## libmpv

[libmpv](https://github.com/mpv-player/mpv) is the Android port of the
legendary [mpv](https://mpv.io/) player. JellyPlay bundles
[libmpv-android](https://github.com/JarneDeprez/mpv-android) by Jarne
Deprez.

**Pros**

- ✅ Plays *anything* — every codec mpv supports, including niche ones
- ✅ Frame-accurate seeking
- ✅ Best-in-class **ASS/SSA** subtitle rendering
- ✅ Supports mpv's `~/.config/mpv/mpv.conf` for advanced configuration
- ✅ Shader packs (Anime4K, FSRCNNX, etc.) for upscaling and
  deinterlacing
- ✅ SVP (Smooth Video Project) integration for frame interpolation

**Cons**

- ❌ Slightly higher CPU usage (less aggressive hardware decoding)
- ❌ Larger APK size (~25 MB)
- ❌ No built-in `MediaSession` (JellyPlay provides its own)
- ❌ Trickplay thumbnails load slightly slower

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
- ✅ Brightness / contrast / saturation / sharpness filter controls
  (in JellyPlay's player)

**Cons**

- ❌ Trickplay sprite sheets are not natively supported
- ❌ Less frequent Android-specific updates
- ❌ Some streaming features (like HLS LL-HLS) are not as smooth as
  ExoPlayer

**Best for:** users with media files in unusual containers, network
stream playback, and anyone who wants the "VLC experience" on Android.

## How to switch engines

1. Open **Settings → Player → Engine**
2. Pick **ExoPlayer**, **libmpv**, or **LibVLC**
3. Tap **Apply** — JellyPlay reloads the player

The choice is per-device, so you can use ExoPlayer on your phone and
libmpv on your NVIDIA Shield at the same time.

## External player

If none of the bundled engines work for a specific file, JellyPlay can
launch an **external player** (MX Player, VLC, mpv for Android, etc.):

1. Open the media detail page
2. Tap the kebab menu → **Open in external player**
3. Pick the player of your choice
4. Watch via the external app

JellyPlay reports the playback progress back to your Jellyfin server
even when using an external player.

## Hardware vs. software decoding

Each engine has a **decoder mode** setting in **Settings → Player →
Decoder**:

- **Hardware** *(default)* — uses the device's GPU/DSP for video
  decoding. Lower battery, smoother 4K HDR.
- **Software** — CPU-only decoding. Needed for some exotic codecs or
  when hardware decoders have bugs on specific devices.

If you see **green artifacts**, **macroblocking**, or **black frames**
on certain files, try switching to software decoding.

## Recommended combinations

| Use case | Engine | Decoder | Notes |
| -------- | ------ | ------- | ----- |
| **Streaming services-style content (HLS/DASH)** | ExoPlayer | Hardware | Best perf, lowest battery |
| **Anime fansubs with .ass subtitles** | libmpv | Hardware | Best ASS rendering |
| **Local 4K HDR remuxes** | ExoPlayer | Hardware | Direct play, frame-rate matching |
| **Low-end TV box (ONN, generic)** | libmpv | Hardware | Often more stable than ExoPlayer |
| **Exotic MKV with audio in AC-3** | LibVLC | Hardware | Best fallback |
| **Slow WiFi, stutter issues** | ExoPlayer | Hardware | Adaptive bitrate (HLS/DASH) |
| **Color-graded cinema (REC.2020, HDR10+)** | libmpv | Hardware | mpv's tone mapping is excellent |
| **Screencasts / unusual framerates** | libmpv | Hardware | mpv handles weird framerates well |
| **Offline downloaded content** | ExoPlayer | Hardware | Lowest CPU usage on plane mode |

## Subtitle Style Tester

A standalone screen (`:feature:subtitle-tester`) lets you preview subtitle
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

## Next steps

- 🚀 [Install JellyPlay →](./setup.md)
- 📺 [Set up JellyPlay on Android TV →](./android-tv-setup.md)
- ⬇️ [Configure offline downloads →](./offline-downloads.md)
