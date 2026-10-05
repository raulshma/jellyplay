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
pick the **`jellyplay-v<version>-tv-<abi>.apk`** file (not the phone one;
`-tv-universal.apk` covers every ABI). The TV APK:

- Registers a Leanback launcher tile (appears in the "Your apps" row)
- Forces landscape layout
- Enables D-pad-first focus traversal
- Adds system search integration so the TV remote's search/voice input
  finds your content
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
adb install -r jellyplay-v<version>-tv-<abi>.apk
```

For network ADB (no USB cable):

```bash
adb connect <tv-ip-address>:5555
adb install -r jellyplay-v<version>-tv-<abi>.apk
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

- **Settings → Playback → Player Engine** — try **libmpv** for the broadest codec
  support and best ASS/SSA subtitle rendering on TV
- **Settings → Playback → Orientation** — set to **Sensor landscape** if you
  use a swiveling mount
- **Settings → Screensaver** — enable Android TV Daydream with the Ken
  Burns effect on your library artwork
- **Settings → System → Setup Wizard** — re-run if your server URL or user
  changed
- **Settings → Playback → Decoder** — set to **Hardware** unless you see
  frame drops; switch to **Software** for exotic codecs

## NVIDIA Shield tips

The Shield is a high-end device and JellyPlay runs buttery smooth on it.
For 4K HDR content:

- Set streaming quality to **Direct play** whenever possible
- Enable **Refresh Rate Match** in Settings → Playback
- The Shield's Tegra X1+ has excellent HEVC hardware decoding; no
  special configuration required

## ONN 4K & budget box tips

Budget boxes benefit from a few tweaks:

- **Settings → Appearance → Performance Mode** — disables animations
- **Settings → Playback → Player Engine** → **libmpv** is more robust than
  ExoPlayer on low-RAM devices
- **Settings → Appearance → Hide episode thumbnails** — lightens the home
  screen on the weakest boxes

## Fire TV specific notes

- JellyPlay is **not** published to the Amazon Appstore (yet) — sideload
  via the methods above
- JellyPlay requires **Android 9 (Fire OS 7) or later** — Fire TV Stick
  4K, Fire TV Stick Lite, Fire TV Cube, and Fire TV Omni QLED all
  qualify, and the Leanback launcher tile works on them
- Older 1st/2nd-gen Fire TV Sticks run Fire OS 5/6, below the minimum —
  JellyPlay does not support them and no APK is published for their ABI

## Next steps

- 🎬 [Pick the right video engine →](./player-engines.md)
- 📡 [Connect Jellyseerr for movie requests from the couch →](./jellyseerr-integration.md)
- 👯 [Start a watch party with friends →](./syncplay-guide.md)
