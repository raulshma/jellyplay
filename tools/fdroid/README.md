# tools/fdroid — F-Droid release tooling

## verify-apk.sh

Post-build assertions for the F-Droid (`fdroid` distribution flavor) APK.
Run it against any `assemblePhoneFdroid*` artifact before submitting to
F-Droid; the CI lane (`kmp-build.yml`, "F-Droid flavor smoke build" step)
runs it automatically after `./gradlew :app:assemblePhoneFdroidRelease -PabiFilter=arm64-v8a`.

### Usage

```bash
tools/fdroid/verify-apk.sh <apk>
tools/fdroid/verify-apk.sh --expect-version-code 11091 app-phoneFdroid-arm64-v8a-release.apk
MIN_FONT_FILES=25 tools/fdroid/verify-apk.sh app.apk   # raise the font floor
```

Tool discovery: uses `aapt2`/`aapt`/`dexdump` from the highest installed
`$ANDROID_HOME`/`$ANDROID_SDK_ROOT` build-tools version (fall back
`~/Android/Sdk`); the dex/font/asset checks degrade gracefully to
`unzip`+`strings`+`grep` when build-tools is absent — except the manifest
checks, which need aapt and fail with a clear message instead of passing
vacuously.

### What each check protects

| Check | F-Droid policy it protects |
|---|---|
| Manifest: no `REQUEST_INSTALL_PACKAGES` | F-Droid rejects APKs requesting install packages; on JellyPlay only the store line's GitHub-release updater legitimately needs it, and `app/src/fdroid/AndroidManifest.xml` strips it (`tools:node="remove"`). A hit means the overlay is missing or `src/main` gained a new requestor. |
| Manifest: no `com.google.android.gms` beyond the Auto media marker | "Non-free dependency" review: the fdroid APK must carry no Google Play services identifiers anywhere in the merged manifest (components, meta-data — incl. the cast OPTIONS_PROVIDER — providers). The ONE allowlisted exception is the inert `com.google.android.gms.car.application` Android Auto marker (`src/phone/AndroidManifest.xml`): a string only, no GMS artifact behind it, required for Auto head units to list the app's media service; jellyfin-android ships the same marker on F-Droid. |
| Dex: no `Lcom/google/android/gms/` in every `classes*.dex` | Even reflection-only GMS usage must not survive into the fdroid artifact; the dex scan catches classes the manifest cannot show. |
| Dex: no `Lcom/github/junrar/` | Vendored RAR extraction code with incomplete license provenance is a classic mechanical F-Droid rejection. |
| `res/font/` >= `MIN_FONT_FILES` (default 20) | fdroid theming must bundle its TTF families instead of GMS downloadable fonts — otherwise the theme silently degrades for users with no Play services. |
| `assets/aboutlibraries.json` present | The settings screen renders licenses from this asset; the fdroid lane ships it as a COMMITTED source asset (`app/src/fdroid/assets/aboutlibraries.json`, CI-drift-checked against the live dependency graph — the build-time export downloads license texts, which is nondeterministic and broke the reproducible-builds byte-compare). |
| `--expect-version-code N` | F-Droid's `VercodeOperation` aligns the APK versionCode with the metadata base + per-ABI index; a mismatch is the most common mechanical rejection. CI derives `N` from `version.properties` (+1 for the arm64 abiIndex). |

During the migration waves, the GMS checks are EXPECTED to fail — the tree
still contains GMS-dependent cast code until the cast/updater seams make it
store-only. The script is deliberately strict; there is no escape hatch, so
the fdroid lane cannot silently regress once those seams land.

### Known limitations

- **The junrar dex check can false-negative on STORE builds.** It greps for
  `Lcom/github/junrar/` descriptors in the dex string tables, and R8 renaming
  can rewrite those away in minified artifacts. This only weakens the check
  on store builds (where junrar is shipped by design); the F-Droid guarantee
  is structural, not scan-based — the junrar dependency is not on the fdroid
  classpath at all, so there is nothing to hide.
- **Font counting is extension-based on purpose.** Minified release builds
  shorten resource paths (`res/font/space_grotesk_regular.ttf` → `res/2Y.ttf`),
  so a `res/font/`-prefix count would false-negative on exactly the artifacts
  the lane ships. The check counts `res/*.ttf` by extension instead, which
  matches both minified and unminified/debug APKs.
- **Do not build store and fdroid releases in one Gradle invocation.** The
  store variants' per-variant aboutlibraries export tasks still write the
  same gitignored `app/src/main/assets/aboutlibraries.json`, so two STORE
  variants in one invocation race on one file (the fdroid variant no longer
  exports at all — it ships the committed `app/src/fdroid/assets/` asset).
  Build one variant at a time — CI and fdroidserver both do.

### versionCode scheme

`version.properties` at the repo root is the canonical source:
`versionCode = major*100_000 + minor*1_000 + patch*10` (0.11.9 -> 11090),
plus an ABI index (arm64-v8a -> 1, x86_64 -> 2) when built with
`-PabiFilter=<abi>`. CI's `github.run_number` codes are unaffected: an
explicit `-PversionCode=` always wins. See the version-resolution block in
`app/build.gradle.kts`.
