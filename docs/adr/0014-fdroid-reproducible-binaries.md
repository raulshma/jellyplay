# 0014 — F-Droid releases ship developer-signed reproducible binaries

- **Status:** accepted
- **Date:** 2026-11-11
- **Scope:** `app` (`ndkVersion` pin), `.github/workflows/fdroid-release.yml`,
  `.github/workflows/fdroid-determinism.yml`, fdroiddata metadata
  (`Binaries:` field, MR !56862)

## Context

The fdroiddata inclusion MR (upstream `!56862`) can ship one of two ways.
Default F-Droid behavior: their buildserver builds the app and signs it with
the **F-Droid key** — simple, but the signing lineage locks in at the first
merged release, and it can never be revisited: switching to developer-signed
`Binaries:` afterwards would break every installed user's update chain.
The alternative, enabled at inclusion time only, is `Binaries:` — the
metadata points at developer-signed APKs on GitHub, and fdroidserver's
buildserver still builds the same commit, strips the signature off our APK
(apksigcopier), copies it onto its own unsigned build, and requires
`apksigner validate` to pass: our APK and their build must be byte-identical
in content. A mismatch marks the build FAILED — the release is blocked,
never silently divergent.

Once enabled, our CI must produce binaries that stay byte-identical to
whatever F-Droid's buildserver produces for every future release. The
verified mechanics (fdroidserver master, 2026-11-11): `Binaries:` is one
templated line expanded per build (`%v` → versionName, `%c` → versionCode,
`build.py` around line 1220-1250), and the buildserver environment is
debian/trixie64 — `default-jdk-headless` (JDK 21) as the Gradle daemon, **no
NDK preinstalled** (the provisioning script only makes
`/opt/android-sdk/ndk` writable so AGP auto-installs the version the build
requests), `LC_ALL=C.UTF-8`, Gradle daemon disabled and JVM
auto-provisioning off (`gradlew-fdroid` shim), and
`SOURCE_DATE_EPOCH` = `git log -n1 --pretty=%ct` of the build checkout.

Internal determinism was already proven (2026-11-10): two clean-clone builds
of the pinned commit at different paths produced byte-identical unsigned
APKs (AGP pins zip timestamps; version catalog, `version.properties`,
baseline profiles, and the AboutLibraries export are all committed or
deterministic).

## Decision

Enable `Binaries:` in the fdroiddata metadata from the first release:

```yaml
# Scalar: Binaries is TYPE_STRING in fdroidserver's metadata schema; a YAML
# list fails the ^https?:// FieldValidator.
Binaries: https://github.com/raulshma/jellyplay/releases/download/v%v/jellyplay-v%v-fdroid-%c.apk
```

Supporting pieces, all in this repo:

- **`ndkVersion = "28.2.13676358"`** pinned in `app/build.gradle.kts` — the
  buildserver resolves the AGP default when unpinned, and the AGP default
  (28.2.13676358 for AGP 9.5) silently moves on AGP upgrades; llvm-strip
  output bytes depend on the NDK version, so the pin keeps CI and the
  buildserver stripping identically. The workflow installs the same version
  explicitly; the two must move in lockstep.
- **`fdroid-release.yml`** — per-release lane that mirrors the buildserver
  (JDK 21, metadata `prebuild:` seds + `rm apps/desktop`, `--no-daemon`,
  JVM auto-download off, `SOURCE_DATE_EPOCH`, `LC_ALL`), signs with the
  project release key (v2, same key as the GitHub store channel), runs
  `tools/fdroid/verify-apk.sh`, renames to the exact `Binaries:` expansion
  `jellyplay-v<version>-fdroid-<code>.apk`, and attaches to the release.
- **`fdroid-determinism.yml`** — weekly watchdog: same commit, two checkout
  paths, byte-compare. Nondeterminism regressions surface here within a
  week, not at the buildserver where they block a release.

Channel coexistence is naming-based and test-pinned
(`GitHubReleasesApiImplTest`): the store updater's asset matcher requires a
`-phone-`/`-tv-` flavor prefix, so `fdroid-11091` APKs are invisible to it
in every tier — and because the last-resort tier matches ANY
`-universal.apk` suffix regardless of flavor, the fdroid lane must never
publish a universal-named APK (it always ends in the versionCode).

## Considered options

- **Default F-Droid signing for the first release, revisit later** —
  rejected: the lineage locks at first merge; the option disappears the day
  the MR lands, and the reverse migration (F-Droid key → project key) breaks
  every installed client's update path.
- **Local buildserver proof before merge** (vagrant/dockered fdroidserver,
  one-off byte-compare) — deferred: heavy setup, and the failure mode it
  de-risks is a delayed release, not user harm; the CI parity lane plus the
  determinism watchdog cover the same drift surface continuously.
- **Per-ABI reference binaries under the existing
  `jellyplay-v%v-phone-%c.apk` names** — rejected: collides with the store
  updater's matcher conventions and its ABI-suffix universe; the `-fdroid-`
  segment keeps the two channels' name spaces disjoint by construction.

## Consequences

- Every release now has a hard external invariant: the F-Droid buildserver
  must byte-match our binaries. A mismatch blocks the fdroid release (and
  only that channel) until fixed — GitHub/Play continue unaffected.
- The release skill gains one non-negotiable: the fdroid binaries must be
  green on `fdroid-release.yml` before the tag is considered shipped.
- Toolchain changes (AGP upgrade, Gradle upgrade, Kotlin bump, new
  AAR with native libs) now carry reproducibility risk; the determinism
  watchdog catches self-inflicted drift weekly, and the `ndkVersion`/AGP
  pin pairing contains the strip surface.
- Versioning contract tightens: `version.properties` must be bumped in the
  release commit such that `v<versionName>` tag equals it (the lane asserts
  this), and the run_number CI code space must never leak into fdroid builds
  (they never pass `-PversionCode`).
