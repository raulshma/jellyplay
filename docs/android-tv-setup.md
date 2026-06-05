# JellyPlay on Android TV & Amazon Fire TV

JellyPlay ships a dedicated **TV flavor** with a Leanback launcher, a
Compose for TV user interface, and 10-foot D-pad navigation. This guide
walks you through installing JellyPlay on:

- Android TV (Sony, TCL, Hisense, Philips, NVIDIA Shield, etc.)
- Google TV (Chromecast with Google TV)
- Amazon Fire TV (Fire TV Stick 4K, Fire TV Stick Lite, Fire TV Cube,
  Fire TV Omni QLED)
- ONN 4K Streaming Box and other budget Android TV boxes

## Which APK to download

From the [Releases](https://github.com/raulshma/jellyplay/releases) page,
pick the **`*-tv-*.apk`** file (not the phone one). The TV APK:

- Registers a Leanback launcher tile (appears in the "Your apps" row)
- Forces landscape layout
- Enables D-pad-first focus traversal
- Hides the on-screen keyboard in favor of the TV remote input
- Includes screensaver (Daydream) support with Ken Burns effect

## Sideload using Downloader (easiest)

The **Downloader** app (free on the Amazon Appstore and Google Play) is
the most popular way to sideload APKs on TV devices.

1. Install **Downloader** from your TV's app store.
2. Launch Downloader and enter the URL bar:
   - Either paste the direct APK link from
     [Releases](https://github.com/raulshma/jellyplay/releases), **or**
   - Use the JellyPlay Downloader short-code (will be published in
     release notes once available)
3. When the download finishes, Android will prompt you to allow
   installation from Downloader. Enable it in **Settings → Security**.
4. Tap **Install**, then **Open**.

## Sideload using ADB (developers)

If you have `adb` on your computer:

```bash
# 1. Enable Developer Options on your TV: Settings → Device Preferences → About → Build (tap 7 times)
# 2. Enable USB debugging: Settings → Device Preferences → Developer options → USB debugging
# 3. Connect TV to computer via USB
# 4. Confirm the RSA fingerprint prompt on the TV

adb devices                              # confirm device shows up
adb install -r app-tv-<version>.apk
```

For network ADB (no USB cable):

```bash
adb connect <tv-ip-address>:5555
adb install -r app-tv-<version>.apk
```

## Sideload using a USB stick

1. Copy the APK to a USB stick formatted as FAT32/exFAT.
2. Plug the stick into your TV.
3. Open a file manager app (e.g. **FX File Explorer** from the Play Store
   — the built-in file manager often can't see USB storage).
4. Navigate to the stick and tap the APK to install.

## Recommended TV settings

Once JellyPlay is installed, open it and tweak the following for the best
lean-back experience:

- **Settings → Player → Engine** — try **libmpv** for the broadest codec
  support and best ASS/SSA subtitle rendering on TV
- **Settings → Player → Orientation** — set to **Sensor landscape** if you
  use a swiveling mount
- **Settings → Screensaver** — enable Android TV Daydream with the Ken
  Burns effect on your library artwork
- **Settings → Onboarding** — re-run if your server URL or user
  changed
- **Settings → Player → Decoder** — set to **Hardware** unless you see
  frame drops; switch to **Software** for exotic codecs

## NVIDIA Shield tips

The Shield is a high-end device and JellyPlay runs buttery smooth on it.
For 4K HDR content:

- Set streaming quality to **Direct play** whenever possible
- Enable **Refresh rate switching** in Settings → Player
- The Shield's Tegra X1+ has excellent HEVC hardware decoding; no
  special configuration required

## ONN 4K & budget box tips

Budget boxes benefit from a few tweaks:

- **Settings → Visual → Performance Mode** — disables animations
- **Settings → Player → Engine** → **libmpv** is more robust than
  ExoPlayer on low-RAM devices
- Reduce home-section thumbnail resolution in **Settings → Visual**

## Fire TV specific notes

- JellyPlay is **not** published to the Amazon Appstore (yet) — sideload
  via the methods above
- The Leanback launcher tile works on Fire TV OS 6+ (Fire TV Stick 4K,
  Fire TV Stick Lite, Fire TV Cube 2nd gen, Fire TV Omni QLED)
- On older Fire TV (1st/2nd gen Stick), use the `*-arm-*` APK for the
  correct ABI

## Next steps

- 🎬 [Pick the right video engine →](./player-engines.md)
- 📡 [Connect Jellyseerr for movie requests from the couch →](./seerr-integration.md)
- 👯 [Start a watch party with friends →](./syncplay-guide.md)
