# tools/fdroid — F-Droid release tooling

## verify-repro.sh

Replicates the **fdroiddata MR CI** (gitlab.com/fdroid/fdroiddata
`.gitlab-ci.yml`) locally, job for job, inside the official buildserver Docker
image with fdroidserver at the CI's pin. It stages a scratch fdroiddata from
the metadata draft (`docs/fdroid/<appid>.yml`) and, for the build modes,
drives the same `fdroid build --test` the MR CI runs — with `Binaries:`
metadata fdroidserver itself downloads the published reference APK and
byte-compares it (apksigcopier signature copy + `apksigner validate`), the
exact gate F-Droid applies to every release. See `docs/fdroid/README.md` §6
and `docs/adr/0014-fdroid-reproducible-binaries.md`.

### Usage

```bash
tools/fdroid/verify-repro.sh meta-check --tag v0.11.13        # ~10 min: ALL MR CI *test* jobs
tools/fdroid/verify-repro.sh env-check  --tag v0.11.13        # ~35 min: tag build must byte-match the published reference
tools/fdroid/verify-repro.sh local      --tag v0.11.13        # ~35 min: build HEAD (or --commit SHA) vs the published reference
tools/fdroid/verify-repro.sh clean                            # drop the work dir + ref clone + docker Gradle volumes
```

Requires docker access (docker group) and git. Everything machine-specific is
derived at runtime — repo root, GitHub slug from the origin remote, app id
from the draft filename, base versionCode from `version.properties` — so the
script hardcodes no user, host path or repository name, and its default work
dir lives under `~/.cache/fdroid-verify` (override: `--work` or
`FDROID_VERIFY_HOME`). The fdroiddata pieces the CI jobs run from the MR
branch (tools scripts, JSON schema, all top-level metadata ymls) live in a
refreshed sparse clone under the work dir.

### MR CI parity

| fdroiddata MR CI job | Replicated by | Notes |
| --- | --- | --- |
| `fdroid lint` | `meta-check` | |
| `fdroid rewritemeta` + diff gate | `meta-check` | proves the draft is in fdroidserver canonical byte form |
| `schema validation` (check-jsonschema + fdroiddata schema) | `meta-check` | |
| `checkupdates --auto` + diff gate | `meta-check` | exercises UpdateCheckMode/UpdateCheckData/VercodeOperation/AutoUpdateMode for real |
| `git redirect` (rewrite-git-redirects.py) | `meta-check` | runs in the ref clone with our file added — MR-branch semantics |
| `tools check scripts` (6 tools) | `meta-check` | same as above; keyalias-collision needs the full metadata yml set, hence the sparse clone |
| `check source code` (check-fastlane.py) | `meta-check` | report-only in CI (reviewers read it); reports printed with severities |
| `fdroid build` (fetchsrclibs + real build + `Binaries:` compare) | `env-check` / `local` | plus the CI's env setup: dist-upgrade, pinned OpenJDK 21, production buildserver gitconfig, `sdkmanager` extras |
| `fdroid build` post-build tail (throwaway sign, build-contains-signatures, publish, audit-gradle) | `env-check` / `local` | with `Binaries:` metadata (no `signatures/` dir) CI skips publish — mirrored |
| `check apk` (scanner --exit-code, signing-key DN, manifest, ABI, R8) | `env-check` / `local` | scanner + debug-key are CI's only hard gates; manifest/ABI/R8 are reviewer reports, printed |
| `fdroid lint config` / `YAML 1.2` | — | config.yml only; not touched by an app metadata MR |
| `strip EXIF` / `repo/index.html` / `weblate merge conflict` / `PUBLISH` | — | triggered by image-file or config.yml changes only |
| `checkupdates_filter` | — | bot-only path guard |
| `check_git_repos` | — | scheduled infrastructure job |

### Modes and verdicts

- **`meta-check`** — run after any draft edit. Every CI job prints PASS/FAIL;
  the verdict is green only when every gate is green (check-fastlane findings
  are reviewer reports, not gates).
- **`env-check`** — builds the TAG commit cloned from the public remote and
  must end in `PASS`. This proves the machine faithfully reproduces the
  buildserver; run it FIRST, or no `local` verdict is trustworthy.
- **`local`** — builds a (usually pre-merge) commit. An UNPUBLISHED commit can
  never byte-match the published reference: AGP stamps the built commit into
  `META-INF/version-control-info.textproto`. The verdict logic accounts for
  that:
  - `PASS` — byte-identical (only possible once the commit is pushed and
    matches the reference's tag).
  - `PASS-EXPECTED-DELTA` — the ONLY difference in the whole APK is the
    `revision:` field of that textproto. Every dex/resource/lib byte is
    identical → the commit is safe for F-Droid reproducible builds. Unpushed
    commits are built from a `file://` mount of this checkout automatically.
  - `FAIL` — anything else; the fdroidserver diff detail is printed.
- Post-build signals are parsed into the build verdict:
  `REPRODUCIBLE-SUCCESS` (fdroidserver produced the `.binary.apk` — reference
  signature copied and validated) strengthens a PASS; any
  `POST-CI GATE FAIL` line (scanner, signing key, audit-gradle) fails the
  mode.

`env-check` and `local` deliberately use SEPARATE docker Gradle volumes:
their fdroid-graph inputs are identical, so a shared cache would let the
second run replay cached outputs and pass vacuously. Fresh volumes force full
re-execution. Run them ONE AT A TIME: each build runs a Gradle daemon
(-Xmx6g) plus a Kotlin compiler daemon (-Xmx4g) and R8 inside the container,
so two concurrent runs exhaust the RAM of a 16 GB host.

### Known limitations

- Only validates the arm64 (`base + 1`) build by default — pass
  `--vercode` with the x86_64 code (`base + 2`) for the second half of the
  metadata pair.
- The whole-repo tools scripts see fdroiddata master's top-level
  `metadata/*.yml` but not other apps' localized subdirectories; those
  checks are no-ops on a clean master, which is what the CI sees too.
- The build runs as root in the container while the CI build job drops to the
  `vagrant` user — proven byte-irrelevant: the env-check build byte-matches
  the published reference.
- The `SOURCE_DATE_EPOCH` a scratch fdroiddata produces differs from the
  fork clone's (fdroidserver derives it from the fdroiddata git log as a
  fallback); it is proven byte-irrelevant — the env-check passes with a
  different epoch than the reference was published with.

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
