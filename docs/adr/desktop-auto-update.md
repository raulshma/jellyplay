# ADR: Desktop auto-update strategy for v1 (Windows msi / Linux deb / macOS dmg)

- **Status:** Accepted (wave 10A release engineering); **amended 2026-09-12 — client half implemented** (see *Implementation* below; the sentinel mechanics described in the Decision are now replaced).
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

## Implementation (2026-09-12, phase X)

The client half of option 2 is live; the Decision's unblocking path was taken
app-side so no `shared/core/data` binding changed shape:

- `apps/desktop/.../update/DesktopAppUpdate.kt` — `desktopAppUpdateModule`
  REPLACES `desktopDataModule`'s sentinel-bound `AppUpdateRepository` single
  (Main.kt loads it last with `allowOverride(true)`; Koin 4 dropped the
  per-definition override flag). The real installed version comes from the
  generated `desktop-build.properties` classpath resource, which gained a
  `channel` line: `release` only when the build passed an explicit
  `-PjellyplayVersion` (i.e. a CI release lane), else `dev`. Dev builds are
  suppressed at the repository decorator (`DesktopAppUpdateRepository` pins
  `isUpdateAvailable = false`) — that is the sentinel's old job done with an
  explicit generated flag instead of a fake version number. A dev build has
  no release channel behind it, so "up to date" is the honest answer.
- Update states surfaced by the About row (shared
  `ShellSessionController.updateCheckMessage` mapping, unchanged): **update
  available** (version + browser handoff, see below) / **up to date** /
  **check failed** (network/parse error message). No launch-time auto-check —
  the pref-gated manual row stays the only surface, exactly as the Decision
  scoped it.
- On "update available" the row opens `info.htmlUrl` (the release page) via
  AWT `Desktop.browse` off the event thread; headless/locked-down sessions
  degrade to a snackbar naming
  `https://github.com/raulshma/jellyplay/releases`. `downloadAssetUrl` is
  only trusted when the chosen asset is an `.msi/.deb/.rpm/.dmg` — the shared
  asset picker's last-resort branch can attach an Android `-universal.apk`,
  which must never become a desktop link (`DesktopUpdateLinks`).
- Windows-first: the MSI's fixed `upgradeUuid` makes a downloaded-and-run MSI
  a true in-place major upgrade. macOS dmg / Linux deb+rpm get the same
  check + browser handoff; the user runs the installer themselves.
- **Still true: never silent download-and-install.** `downloadUpdate` /
  `getPendingUpdate` remain Android-only-reachable (the desktop UI gates on
  `isUpdateAvailable`); the appdata `updates` dir stays empty. Signed-manifest
  verification does not exist in v1 — no server infrastructure for it, and
  the user's browser + OS signature surface (SmartScreen/Gatekeeper) are the
  trust boundary.

**Server-side contract** (what a published release must satisfy for desktop
clients to offer it): the feed is the GitHub Releases endpoint the shared
seam already targets (`api.github.com/repos/raulshma/jellyplay/releases/latest`);
the tag must strip to a semver-comparable `vX.Y.Z` (optional `-pre.N` —
`compareVersions` orders pre-releases below their release, so a stable
`vX.Y.Z` beats its own alphas); the release must be the repo's **latest**
(GitHub hides pre-releases from `/releases/latest`, which is why the alpha
lane publishes `prerelease: true`); installers attached as
`jellyplay-desktop-<platform>-v<version>.<msi|deb|rpm|dmg>` (the
kmp-release.yml naming). Unit tests pin the classification, suppression, and
link-picking decisions (`apps/desktop/src/test/.../update/DesktopAppUpdateTest.kt`).

## Cutting a desktop release

Checklist, in order. Items marked *(manual)* need a human or credentials the
repo does not have.

1. **Version + tag.** No in-repo version bump exists — `packageVersion` is
   CI-driven. Preview/alpha: push `release-alpha/vX.Y.Z`; kmp-release.yml
   computes `vX.Y.Z-alpha.N` (bumping the ordinal per existing tags) and
   passes `-PjellyplayVersion=<X.Y.Z> -PjellyplayVersionName=<display>` to
   the build. Stable: tag `vX.Y.Z` (the Android stable lane owns `v*`).
2. **CI artifacts.** The `desktop-package` matrix (ubuntu/windows/macos)
   runs `:apps:desktop:packageDistributionForCurrentOS` per OS and uploads
   version-tagged installers: `jellyplay-desktop-<platform>-v<display>.msi |
   .deb | .rpm | .dmg`; the `release` job attaches them (plus the web bundle)
   to the GitHub release.
3. *(manual)* **Boot each installer on each OS** (packaging smoke below)
   BEFORE publishing — a broken installer published as `latest` becomes every
   desktop client's "update".
4. *(manual)* **Signing — placeholders only, do NOT fake.** No certs exist in
   this repo. When acquired, wire (e.g. a new `sign-installers` CI step):
   - Windows MSI (Authenticode):
     `signtool sign /fd SHA256 /td SHA256 /tr "$TIMESTAMP_URL" /f "$WINDOWS_PFX_PATH" /p "$WINDOWS_PFX_PASSWORD" <file>.msi`
     — env: `WINDOWS_PFX_PATH`, `WINDOWS_PFX_PASSWORD` (EV tokens instead use
     the store/thumbprint form: `/sha1 "$WINDOWS_CERT_THUMBPRINT"`), plus
     `TIMESTAMP_URL` (e.g. `http://timestamp.digicert.com`).
   - macOS: `codesign --deep --force --options runtime --sign "$MACOS_SIGNING_IDENTITY" JellyPlay.app`
     → `ditto -c --keepSeed --separation . out.dmg` →
     `xcrun notarytool submit out.dmg --key "$APP_STORE_CONNECT_API_KEY_PATH" --key-id "$APP_STORE_CONNECT_KEY_ID" --issuer "$APP_STORE_CONNECT_ISSUER" --wait`
     → `xcrun stapler staple out.dmg`. Env: `MACOS_SIGNING_IDENTITY`,
     `APP_STORE_CONNECT_*` (or a stored notarytool keychain profile).
   - Linux: deb/rpm carry no signature in this flow (optional detached
     GPG: `gpg --detach-sign --armor <file>.deb`, publish `.asc` beside it).
5. **Feed publish.** For desktop clients to offer the release it must be the
   repo's *latest*: stable releases (not pre-releases) satisfy
   `/releases/latest`. Alpha-lane pre-releases are preview-only by design —
   desktop clients on stable will not see them. If stable releases should
   carry desktop installers automatically, extend release.yml with the same
   `desktop-package` matrix (kmp-release.yml's job is the template); until
   then attach the artifacts to the stable release *(manual)*.
6. **Post-publish.** Run one packaged Windows build of the PREVIOUS version
   and confirm About → "Check for updates" announces the new version and
   opens the release page (the only E2E assertion this design has).

## Manual per-OS test checklist

Real-machine passes REQUIRED before shipping; macOS and Linux were NOT
executed for this phase (Windows-only host). Per OS:

- **Launch:** installer runs to completion (no SmartScreen/Gatekeeper
  surprises beyond the documented unsigned warnings); app launches from the
  OS menu/Start entry, not just the dev command line.
- **Tray:** tray icon renders; Show restores/focuses the window; Quit exits
  the process (no lingering mpv/JVM processes).
- **Menus/title bar:** minimize/maximize/close, F11 fullscreen toggle,
  Ctrl+R refresh, Ctrl+Q quit, About dialog opens (this is also where the
  update-check row lives).
- **Playback:** video (embedded mpv surface — Windows verified; macOS/Linux
  need the bundled-libmpv path confirmed from the installed app-resources
  dir, not the dev `tools/mpv` path) and audio (queue start, next/prev).
- **Packaging smoke:** appdata dirs land under the OS-appropriate home
  (`%APPDATA%` / `~/Library/Application Support` / `~/.local/share`
  equivalent — `DesktopPaths` decides); installed build reports
  `channel=release` semantics (About → check reaches the feed and reports
  "up to date" on an equal version, or offers a newer one); a dev `gradlew
  run` build reports "up to date" regardless of feed state.
- **Update handoff (only after a real release exists):** About → "Check for
  updates" on a build older than `latest` opens the release page in the
  default browser; with no browser available it shows the releases-page
  snackbar.
