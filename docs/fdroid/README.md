# F-Droid distribution runbook

How JellyPlay ships to F-Droid, what the `fdroid` distribution flavor changes,
and how to cut an F-Droid release without re-deriving anything.

The reference fdroiddata metadata draft lives next to this file:
[`com.raulshma.jellyplay.yml`](./com.raulshma.jellyplay.yml).

## 1. Why a `fdroid` flavor exists, and what differs

F-Droid's inclusion policy forbids: Google Play Services / proprietary SDKs
(deps and code), non-free-licensed bundled code (the UnRAR license fails their
bar), and the `REQUEST_INSTALL_PACKAGES` permission path. The store/desktop
builds legitimately use all three, so the repo carries a `distribution`
product-flavor dimension (`store` / `fdroid`, alongside the existing
`platform` dimension `phone` / `tv` in `app/build.gradle.kts`) and the seams
live behind same-FQCN flavor objects — see
[ADR 0013](../adr/0013-distribution-flavor-dimension.md).

| Capability | store / Play + GitHub | fdroid |
| --- | --- | --- |
| Google Cast (`play-services-cast`) | yes | removed |
| In-app self-updater (`REQUEST_INSTALL_PACKAGES` + `api.github.com` download path) | yes | removed (F-Droid delivers updates; the read-only What's New feed stays) |
| Fonts | Google downloadable-fonts provider (GMS) | 10 OFL-licensed TTF families bundled in the APK |
| RAR comic archives (`junrar`, UnRAR license) | yes | dropped — CBZ unaffected |
| Android Auto metadata | yes | yes (inert `gms.car` string marker — no GMS dependency; `verify-apk.sh` check 1b allowlists exactly that marker) |
| Player engines (mpv / VLC / ffmpeg-decoder / ASS) | yes | yes — all on Maven Central, conditionally allowed per F-Droid policy |

## 2. Release flow (F-Droid portion)

The versioning scheme: committed `version.properties`
(`jellyplay.versionName=0.11.9`, `jellyplay.versionCode=11090`), scheme
`major*100000 + minor*1000 + patch*10`. F-Droid gets one APK per ABI with
`versionCode = base + abiIndex`: `arm64-v8a = +1`, `x86_64 = +2`
(v0.11.9 → 11091 and 11092).

1. **Bump `version.properties` in the release commit** on the
   `release/vX.Y.Z` branch (name and code; the branch name still drives
   `release.yml`). This is part of the release skill flow — see
   `.agents/skills/release/SKILL.md` step 4.
2. **Write the F-Droid changelogs** for both ABI codes:
   `fastlane/metadata/android/en-US/changelogs/<code>.txt` — one file per code
   (`11091.txt`, `11092.txt`), identical content, ≤500 chars, plain text.
   First release already has these; every later release adds a fresh pair.
3. **Verify locally** before pushing the tag:
   ```bash
   ./gradlew :app:assemblePhoneFdroidRelease -PabiFilter=arm64-v8a
   ./gradlew :app:assemblePhoneFdroidRelease -PabiFilter=x86_64
   tools/fdroid/verify-apk.sh app/build/outputs/apk/phone/fdroid/*release*.apk
   ```
   `verify-apk.sh` proves the fdroid APK carries no cast/RAR/updater code and
   no forbidden permissions (dex + merged-manifest + permissions diff).
4. **Tag and push** per the normal release flow (tag `vX.Y.Z`; Play/GitHub
   publish unchanged). Record `git rev-parse vX.Y.Z` — it pins the fdroid
   `commit:` hashes.
5. **fdroiddata submission** (after the tag is public):
   - fork <https://gitlab.com/fdroid/fdroiddata>, branch `com.raulshma.jellyplay`;
   - copy `docs/fdroid/com.raulshma.jellyplay.yml` to the fdroiddata repo root;
   - replace both `commit: TO-FILL-v0.11.9-tag-commit` placeholders with the
     tag hash from step 4;
   - sanity-check metadata:
     ```bash
     fdroid readmeta
     fdroid checkupdates com.raulshma.jellyplay   # expect the two vercodes
     fdroid build com.raulshma.jellyplay -t v0.11.9 --on-server -v   # if you can
     ```
     and confirm the flavor fragment still matches: fdroidserver turns
     `gradle: [phoneFdroid]` into the task `assemblePhoneFdroidRelease`
     (task-name fragment joined into `assemble…Release`) — see the comments in
     the yml.
   - open the MR titled `Adding com.raulshma.jellyplay`, paste the reviewer
     notes from §3 into the MR description, and include the `verify-apk.sh`
     output for both ABIs.

## 3. Reviewer-risk notes (paste into the MR description)

- **TMDB community API key** — hardcoded at
  `shared/core/network/src/jvmShared/kotlin/com/raulshma/jellyplay/core/network/api/TmdbApiClientImpl.kt:52`,
  talks to `api.themoviedb.org` (free API) for artwork/info enrichment and
  builds `youtube.com` trailer links. Triggered only when browsing/enriching
  library items. Same community key other open-source Jellyfin clients ship;
  users can override it in settings.
- **OpenSubtitles shared consumer key** —
  `OpenSubtitlesSubtitleProvider.kt:519` calls `https://api.opensubtitles.com`
  with the same registered consumer key the official Jellyfin OpenSubtitles
  plugin ships (see the in-source comment, lines 526–534). Users authenticate
  with their own opensubtitles.com username/password.
- **What's New feed** — `GitHubReleasesApiImpl.kt:243,247` GETs
  `https://api.github.com/repos/raulshma/jellyplay/releases`, read-only, in ALL
  builds. The self-update download/install path is STORE-ONLY: the fdroid build
  removes `REQUEST_INSTALL_PACKAGES` and the updater (`verify-apk.sh` output
  attached proves both).
- **Cleartext HTTP for LAN** — `android:usesCleartextTraffic="true"`
  (`app/src/main/AndroidManifest.xml:56`) so self-hosted Jellyfin servers over
  plain HTTP on the LAN work. No other cleartext traffic is intended.
- **PCAPdroid first-run expectations** — on a clean install with a packet
  logger you should see: SSDP multicast discovery (DLNA/UPnP "play on device",
  `ACCESS_LOCAL_NETWORK`/`CHANGE_WIFI_MULTICAST_STATE`, manifest comments at
  lines 6–13) and TMDB/GitHub calls only when the matching feature is used.
  UnifiedPush has no hardcoded endpoint — it talks to whichever distributor app
  the user installed (e.g. ntfy, `AndroidJellyPushDistributor`). LRCLIB
  (`lrclib.net`, `LrcLibApi.kt:111`, synced lyrics) and Wyzie
  (`sub.wyzie.io`, `WyzieSubtitleProvider.kt:234`, subtitle search) fire only
  on feature use.
- **junrar absent** — the fdroid APK must not contain `com.github.junrar`
  classes (UnRAR license is non-free by F-Droid standards); CBZ is unaffected.
  `verify-apk.sh` dex-scan output attached.
- **Android Auto media marker** — the fdroid merged manifest intentionally
  carries ONE `com.google.android.gms.*` string: the inert
  `com.google.android.gms.car.application` meta-data (`src/phone/AndroidManifest.xml`).
  It is a string marker, not a dependency — no GMS artifact or code rides it
  (`verify-apk.sh` proves the dex is gms-free) and Android Auto head units
  list the app's media service only when it is declared. Precedent:
  jellyfin-android (on F-Droid) declares the same marker.
- **OFL font bundling** — the fdroid build bundles 10 OFL-licensed TTF families
  instead of Google's GMS downloadable-fonts provider; licenses are
  OFL-allowed on F-Droid.

## 4. Known open verification items

- **AAR jniLibs ABI inventory** — RESOLVED (published AARs inspected on
  Maven Central, 2026-11-10): `io.github.abdallahmehiz:mpv-android-lib:0.1.12`,
  `org.videolan.android:libvlc-all:3.7.7`,
  `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` and
  `io.github.peerless2012:ass-kt:0.5.1` all package `arm64-v8a` + `x86_64`
  (plus armeabi-v7a/x86, unused under our abiFilter); `ass-media:0.5.1` is
  pure-JVM — the native libass rides ass-kt. Licenses: MIT (mpv wrapper;
  the bundled libmpv.so is GPLv2+, compatible with this GPLv3 app),
  LGPL-2.1 (libvlc), GPL-3.0 (jellyfin ffmpeg build), MIT (ass) — all
  F-Droid-acceptable free licenses.
- **fdroidserver flavor-name semantics** — RESOLVED (fdroidserver master
  `build.py`, 2026-11-10): `build_local` computes
  `assemble` + ''.join(first-letter-capitalized `gradle:` fragments) + `Release`
  → `gradle: [phoneFdroid]` yields `assemblePhoneFdroidRelease`. Also proven
  locally: `./gradlew :app:assemblePhoneFdroidRelease -PabiFilter=arm64-v8a`
  builds clean and `verify-apk.sh` passes on the artifact.
- **JDK on the build server** — RESOLVED: no `jvmToolchain` pin exists
  anywhere in the repo and the release lane already builds the signed APKs
  on Temurin 17 (`.github/workflows/release.yml:130`), so the daemon JDK
  requirement is 17 — buildserver defaults (bookworm: openjdk-17, trixie:
  openjdk-21) satisfy it and NO `sudo:` step is needed. (An earlier draft
  here claimed "requires JDK 21"; that was never verified and is
  contradicted by release.yml.)
- **NDK requirement** — no `ndkVersion` is declared anywhere; the release
  buildType sets `debugSymbolLevel = "SYMBOL_TABLE"`
  (`app/build.gradle.kts:125`) but packaging skips symbol extraction with a
  warning when no NDK is present. Confirm whether the build server's NDK
  presence changes the built APK (affects reproducibility, not acceptance).
- **Screenshot orientation mix** — `video_player.png` is landscape
  (2280x1080) because the source WebP is; the other four are portrait
  1080x2280. If the fdroiddata review objects, swap in `media_detail`
  (portrait) instead.

## 5. Out of scope follow-ups

- **Reproducible builds** — not attempted for the first MR; revisit once the
  first build succeeds upstream.
- **TV app on F-Droid** — the `com.raulshma.jellyplay.tv` applicationId is a
  separate package; a later MR adds it (fdroid metadata + TV screenshots) once
  the phone app is accepted.
