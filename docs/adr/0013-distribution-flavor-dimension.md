# 0013 — The distribution flavor dimension is the F-Droid seam

- **Status:** accepted
- **Date:** 2026-11-09
- **Scope:** `app` (`DistributionPlatform` flavor objects, productFlavors),
  `shared/core/data` (push distributor, RAR reading seam), `shared/core/ui`
  (cast integration, theme fonts), `shared/feature/settings` (updater)

## Context

JellyPlay ships to three surfaces — Play Store, GitHub APKs, and (new) F-Droid.
F-Droid's inclusion policy rules out three things the store builds use: Google
Play Services (the Cast framework dependency), non-free-licensed bundled code
(junrar's UnRAR license fails their bar), and the self-update path
(`REQUEST_INSTALL_PACKAGES` plus downloading and installing APKs at runtime).
None of these can be patched out by the fdroiddata metadata; the source tree
must build both shapes from one codebase.

The app already carries a `platform` flavor dimension (`phone` / `tv`) in
`app/build.gradle.kts`, so a second `distribution` dimension (`store` /
`fdroid`) slots into the existing variant machinery: the fdroid build task is
`assemblePhoneFdroidRelease`, and F-Droid's per-ABI versionCodes
(`base + abiIndex` on top of the committed `version.properties` code) are
property-driven through the existing `splits.abi` block.

The seams were already shaped for this: every capability F-Droid rejects is
reached through an interface registered in Koin, not through direct
instantiation at call sites — `GoogleCastIntegration`, `RarEntryReader`,
`ThemeFontRegistry`, and `AppUpdateCoordinator`. App product flavors can only
carve the `:app` module's own sources; they cannot remove a class from a
library module (`shared/*`), so the flavor boundary must sit behind those
interfaces with one same-FQCN `DistributionPlatform` object per app flavor
supplying the (possibly no-op) implementations.

## Decision

Add a `distribution` flavor dimension (`store` / `fdroid`) to `:app`. The
`fdroid` flavor excludes the GMS cast dependency, the junrar dependency, and
the self-updater wiring; each seam keeps the same FQCN on both sides:
a per-flavor `DistributionPlatform` object in `:app` registers the flavor's
implementation (real, stub, or omitted-at-the-UI-layer) for
`GoogleCastIntegration`, `RarEntryReader` (via Koin), `ThemeFontRegistry`
(fdroid bundles the 10 OFL TTFs the store build fetches from Google's
downloadable-fonts provider), and `AppUpdateCoordinator` (fdroid drops the
`REQUEST_INSTALL_PACKAGES` install path but keeps the read-only
api.github.com What's New feed).

Library modules stay flavor-blind: they depend on interfaces in shared
modules, never on `BuildConfig`-style distribution flags. Packaging-level
drops (junrar, cast) happen at the `:app` dependency edge so R8 sees no
reachable path and strips the rest.

## Considered options

- **Move the whole cast/updater/RAR surface into `:app`** so flavors could
  delete the files outright — rejected: it drags shared-module collaborators
  (session graph, settings, download workers) into `:app`, inverting the
  module direction the KMP carve-outs established.
- **Ship one store build and mark it NonFreeDep on F-Droid** — rejected: the
  anti-feature flags the whole app as dependently non-free while the
  non-free parts are three small, cleanly seamed capabilities; F-Droid
  reviewers also prefer a genuinely clean build for first inclusion.
- **Variable-only fonts** (keep the GMS downloadable-fonts provider on both
  flavors and let fdroid fall back) — rejected: the fallback would change
  typography per device without the GMS provider, breaking visual parity; the
  2.5 MB bundled TTF cost is the price of identical rendering.

## Consequences

- The store debug variant carries roughly 2.5 MB of unused font resources
  (the OFL TTFs live in a flavor source set store doesn't consume — accepted
  cost of same-FQCN seams over resource carving).
- Test source sets split: JVM/Robolectric tests for distribution-dependent
  behavior live under `test`/`testStore` rather than one shared folder, so a
  test asserting cast wiring cannot compile against the fdroid variant.
- The CI matrix is unchanged for releases (store remains the release lane);
  the fdroid lane is a local/`verify-apk.sh` smoke check until the first
  fdroiddata build proves the toolchain (see `docs/fdroid/README.md` §4).
- Versioning stays single-sourced in committed `version.properties`
  (`major*100000 + minor*1000 + patch*10`); F-Droid derives its per-ABI codes
  (`+1` arm64-v8a, `+2` x86_64) via `VercodeOperation` in the fdroiddata
  metadata, so no extra bump exists for fdroid releases.
