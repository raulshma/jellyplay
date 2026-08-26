# ADR: Desktop auto-update strategy for v1 (Windows msi / Linux deb / macOS dmg)

- **Status:** Accepted (wave 10A release engineering)
- **Date:** 2026-08-27
- **Scope:** `:apps:desktop` packaged distributions; the About screen's update check.

## Context

Desktop v1 ships as **unsigned** jpackage installers (MSI with a fixed
`upgradeUuid`, deb, rpm, dmg) built from `:apps:desktop` nativeDistributions.
There is no code-signing certificate, no notarization, and no store presence.

What already exists (AppUpdate split, Wave xB): the About row calls
`AppUpdateRepository.checkForUpdate()` against the GitHub Releases API and can
report `AppUpdateInfo` (`latestVersion`, `htmlUrl` release page,
`downloadAssetUrl`). On desktop this seam is deliberately **inert**: the JVM
actual registers `DESKTOP_SELF_UPDATE_VERSION = "999999.0.0"` as the current
version (see `DesktopDataModule.kt`), so `isUpdateAvailable` can never be true
and the download/install path (an Android-APK-shaped flow) is unreachable by
construction. The sentinel — not `"dev"` — is required because
`compareVersions` folds non-numeric segments to 0, which would make every
release look newer, and the asset picker's last-resort branch would attach an
Android universal APK to a false-positive desktop update.

## Options considered

1. **No update channel at all** — remove/darken the About row.
   Cheapest, but the seam already resolves and the check is pref-gated; users
   would have to watch the repo manually.
2. **Manual check + open the release page (chosen direction for v1)** — keep
   the check, and when a real update exists open `htmlUrl`/`downloadAssetUrl`
   through the existing AWT browse path (`PlatformIntents.jvm.kt`
   `openUrl`, precedent: `java.awt.Desktop.getDesktop().browse`). The user
   downloads the installer themselves and installs via the normal OS flow
   (MSI major-upgrade handles in-place replacement via the fixed
   `upgradeUuid`).
3. **In-app silent download + auto-install** — full self-update (download to
   the appdata `updates` dir, verify hash, launch installer, restart).
   Requires signing to be worth the risk; unsigned binaries plus silent
   execution is the textbook supply-chain alert trigger, and SmartScreen /
   Gatekeeper warnings already greet unsigned installers.
4. **OS store channels** — MSIX + winget/Store on Windows, Sparkle-like
   bundler (e.g. AutoUpdate via Homebrew/Sparkle) on macOS. Real solution for
   signed distribution, but a signing identity, notarization pipeline and
   package-format migration are prerequisites this wave does not have.

## Decision

**Keep the existing inert About update-check seam exactly as it is, and define
the unblocking path as "open the release page / download URL in the user's
browser via the existing AWT `Desktop.browse` seam — never silent
download-and-install."** The `999999.0.0` sentinel stays until a real release
channel exists, so today a successful check always reports "You're up to
date" and no browse is ever triggered. When the first real desktop installer
is published, unblocking is a one-line change to the version supplier plus an
`openUrl(info.htmlUrl)` call — no new plumbing, no repository shape change.

## Consequences

- v1 users update by downloading installers manually; the app tells them a
  release exists but never runs one.
- Unsigned installers remain exposed to SmartScreen/Gatekeeper warnings; that
  is an accepted v1 reality documented here rather than hidden.
- No delta updates: each release is a full installer (~155 MB on Windows;
  MSIs embed timestamps so exact bytes do not reproduce).
- `AppUpdateRepository.downloadUpdate`/`getPendingUpdate` remain
  Android-only-reachable on desktop; the appdata `updates` dir stays empty.

## Revisit triggers

- **First signed release** (cert + MSI Authenticode / macOS codesign +
  notarization): re-evaluate option 3 with hash verification.
- **MSIX or store adoption** on Windows: store plumbing replaces the manual
  download loop; the About row's check stays as the discovery mechanism.
- If download sizes or release cadence make manual updates painful, add
  winget manifest publishing (option 4-lite) before any silent-auto-install
  work.
