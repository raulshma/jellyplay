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
   tools/fdroid/verify-apk.sh app/build/outputs/apk/phoneFdroid/release/*arm64-v8a*release.apk
   tools/fdroid/verify-apk.sh app/build/outputs/apk/phoneFdroid/release/*x86_64*release.apk
   ```
   `verify-apk.sh` proves the fdroid APK carries no cast/RAR/updater code and
   no forbidden permissions (dex + merged-manifest + permissions diff).
4. **Tag and push** per the normal release flow (tag `vX.Y.Z`; Play/GitHub
   publish unchanged). Record `git rev-parse vX.Y.Z` — it pins the fdroid
   `commit:` hashes. When the stable draft is published,
   [`fdroid-release.yml`](../../.github/workflows/fdroid-release.yml)
   automatically builds and attaches the signed fdroid binaries
   (`jellyplay-vX.Y.Z-fdroid-<code>.apk`) to that release — **the release is
   not done until that lane is green** (F-Droid's buildserver byte-compares
   against these; see §6).
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
- **NDK requirement** — RESOLVED (fdroidserver buildserver provisioning,
  2026-11-11): the buildserver preinstalls NO NDK; AGP auto-installs the
  requested version into its writable `/opt/android-sdk/ndk`. The repo pins
  `ndkVersion = "28.2.13676358"` (app/build.gradle.kts) from 0.11.10 —
  identical to AGP 9.5's unpinned default, so the v0.11.9 tag (frozen,
  pre-pin) converges on the same version on both sides — and
  `fdroid-release.yml` installs it explicitly. With an NDK present, release
  packaging runs llvm-strip over the bundled AAR `.so` files for
  `debugSymbolLevel = "SYMBOL_TABLE"` (`app/build.gradle.kts:240`); strip
  output bytes depend on the NDK version, hence the pin + lockstep workflow
  variable.
- **Screenshot orientation mix** — `video_player.png` is landscape
  (2280x1080) because the source WebP is; the other four are portrait
  1080x2280. If the fdroiddata review objects, swap in `media_detail`
  (portrait) instead.

## 5. Out of scope follow-ups

- **TV app on F-Droid** — the `com.raulshma.jellyplay.tv` applicationId is a
  separate package; a later MR adds it (fdroid metadata + TV screenshots) once
  the phone app is accepted.

## 6. Reproducible builds (`Binaries:`)

DECIDED (2026-11-11) — the fdroiddata metadata carries
[`Binaries:`](./com.raulshma.jellyplay.yml) pointing at developer-signed
reference binaries, from the **first** release. This is only available at
inclusion: the default F-Droid flow signs releases with the **F-Droid key**
and that lineage locks at first merge — switching to `Binaries:` afterwards
would break every installed user's update chain. See
[ADR 0014](../adr/0014-fdroid-reproducible-binaries.md) for the decision
record.

How it works: fdroidserver expands one template per build
(`%v` → versionName, `%c` → the build's versionCode), downloads our APK,
strips its signature via apksigcopier onto its own buildserver-built unsigned
APK, and requires `apksigner validate` to pass — i.e. the buildserver build
and our reference binary must be byte-identical in content. A mismatch marks
the build FAILED: the fdroid release is blocked (GitHub/Play unaffected)
until fixed; users are never shipped divergent bytes.

The producing lane is
[`fdroid-release.yml`](../../.github/workflows/fdroid-release.yml) and it
mirrors the buildserver environment point-for-point:

| Buildserver (fdroidserver) | `fdroid-release.yml` |
| --- | --- |
| debian/trixie64, `default-jdk-headless` → daemon JDK 21 | Temurin 21 |
| no NDK preinstalled; AGP auto-installs the requested version into writable `/opt/android-sdk/ndk` | explicit `sdkmanager "ndk;28.2.13676358"` (== the `ndkVersion` pin == AGP 9.5's unpinned default, so the pre-pin v0.11.9 tag converges too) |
| `prebuild:` seds + `rm apps/desktop` from the metadata | identical `sed` + `rm -rf` |
| `gradlew-fdroid`: `org.gradle.daemon=false`, JVM auto-download off | `--no-daemon -Porg.gradle.java.installations.auto-download=false`, foojay removed by the sed |
| `SOURCE_DATE_EPOCH` = `git log -n1 --pretty=%ct` (common.py `get_source_date_epoch`) | same expression |
| `LC_ALL=C.UTF-8` (buildserver `setup-env-vars`) | same |

Rules that keep the two channels disjoint:

- fdroid binaries are named `jellyplay-v<version>-fdroid-<code>.apk` — the
  store updater's matcher requires a `-phone-`/`-tv-` flavor prefix
  (`GitHubReleasesApiImpl.selectAsset`), so it never sees them; the unit
  test `selectAsset ignores fdroid channel binaries` pins this. The
  corollary is load-bearing: the fdroid lane must **never** publish a
  `-universal.apk`-suffixed file — the matcher's last-resort tier matches
  ANY universal suffix regardless of flavor.
- fdroid builds never pass `-PversionCode`; the code comes from
  `version.properties` + abiIndex (11091/11092), matching the metadata's
  `VercodeOperation`.
- `version.properties` and the tag must agree (`v<versionName> == tag`); the
  lane asserts it.
- A weekly watchdog (`fdroid-determinism.yml`) builds the same commit at two
  paths and byte-compares, so self-inflicted nondeterminism surfaces within
  a week rather than at the buildserver. (First catch, 2026-11-11: the
  AboutLibraries export downloads license texts at build time — the GPL-3.0
  body is a gnu.org fetch — so the fdroid lane now ships a COMMITTED
  `app/src/fdroid/assets/aboutlibraries.json` and the export wiring is
  store-only; kmp-build drift-checks the committed file against the live
  dependency graph every PR.)
