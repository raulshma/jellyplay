package com.raulshma.jellyplay.desktop.update

import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.core.network.github.GitHubRepoAllowList
import java.io.File
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop auto-update, client half (docs/adr/desktop-auto-update.md — the
 * accepted v1 design: **manual check + open the release page**, never silent
 * download-and-install). This file REPLACES the old `999999.0.0` sentinel
 * mechanics from `desktopDataModule` with a real update check:
 *
 *  - The installed version comes from the generated `desktop-build.properties`
 *    classpath resource ([DesktopInstalledVersion.read]) — the same resource
 *    the About screen's `DesktopAppMetaProvider` reads. Builds produced with
 *    an explicit `-PjellyplayVersion` (the CI release lanes; see
 *    `jellyplayReleaseChannel` in apps/desktop/build.gradle.kts) are
 *    [DesktopInstalledVersion.Release]; every dev/IDE build is
 *    [DesktopInstalledVersion.DevBuild] and stays "up to date" BY CONSTRUCTION
 *    — a dev build has no release to update to, and the old sentinel existed
 *    precisely because `compareVersions` folds a non-numeric version ("dev")
 *    to 0.0.0 and would false-positive against every tag.
 *  - The feed is the GitHub Releases API the shared seam already targets —
 *    `GitHubReleasesApiImpl.LATEST_RELEASE_URL`
 *    (`https://api.github.com/repos/raulshma/jellyplay/releases/latest`).
 *    **Server-side contract** (what a published release must satisfy for the
 *    desktop check to work): the tag must strip to a semver-comparable
 *    version (`vX.Y.Z`, optional `-pre.N` suffix — `compareVersions` orders
 *    pre-releases below their release); the release must be the repo's
 *    "latest" (GitHub marks pre-releases off `/releases/latest`, which is why
 *    the alpha lane stays `prerelease: true`); desktop installers are
 *    attached as release-page assets named
 *    `jellyplay-desktop-<platform>-v<version>.<msi|deb|rpm|dmg>` (the
 *    kmp-release.yml naming). v1 has NO signed manifest: the user downloads
 *    the installer through their browser and the OS signature surface
 *    (SmartScreen/Gatekeeper) is the trust boundary — hash/manifest
 *    verification is a documented revisit trigger (first signed release).
 *  - When a newer release exists the check opens `info.htmlUrl` (release
 *    page) in the user's browser via AWT `Desktop.browse`
 *    ([DesktopUpdateBrowser]); the Android APK asset picker is deliberately
 *    NOT trusted on desktop ([DesktopUpdateLinks]).
 *
 * Windows-first: the MSI's fixed `upgradeUuid` (nativeDistributions,
 * apps/desktop/build.gradle.kts) makes a downloaded-and-run MSI a true
 * in-place major upgrade. macOS/Linux get the same check + browser handoff
 * (the user runs the dmg/deb themselves); headless/locked-down sessions
 * where AWT cannot browse degrade gracefully to the releases-page URL in the
 * About row's snackbar.
 */

/** How the running desktop build relates to the release feed. */
sealed interface DesktopInstalledVersion {
    /** The version string fed to the version comparison (e.g. `0.10.9`, `0.11.0-alpha.1`). */
    val versionName: String?

    /** A CI release-lane build: participates in the update check for real. */
    data class Release(override val versionName: String) : DesktopInstalledVersion

    /**
     * A dev/IDE build (`gradlew run`, unpackaged distributable): offers no
     * updates, ever — there is no release channel behind it.
     */
    data object DevBuild : DesktopInstalledVersion {
        override val versionName: String? = null
    }

    companion object {
        const val CHANNEL_RELEASE = "release"

        /**
         * Classifies a `desktop-build.properties` record. [channel] must be
         * exactly [CHANNEL_RELEASE] AND [versionName] must carry a digit;
         * anything else (absent resource, blank, the legacy `"dev"` literal,
         * a placeholder) is [DevBuild]. The version string is kept whole —
         * `compareVersions` handles semver pre-release suffixes
         * (`0.11.0-alpha.1` < `0.11.0`), which is exactly what the alpha
         * lanes publish as the display version.
         */
        fun fromBuildInfo(versionName: String?, channel: String?): DesktopInstalledVersion {
            val plausible = !versionName.isNullOrBlank() &&
                versionName != "dev" &&
                versionName.any { it.isDigit() }
            return if (channel == CHANNEL_RELEASE && plausible) {
                Release(versionName)
            } else {
                DevBuild
            }
        }

        /**
         * Reads the generated `desktop-build.properties` from the classpath
         * (written by WriteDesktopBuildInfoTask). Missing/unreadable resource
         * (settings-module-style test classpaths, IDE runs before the first
         * processResources) classifies as [DevBuild] — the quiet answer.
         */
        fun read(): DesktopInstalledVersion {
            val props = runCatching {
                DesktopInstalledVersion::class.java.classLoader
                    .getResourceAsStream("desktop-build.properties")
                    ?.use { stream -> java.util.Properties().apply { load(stream) } }
            }.getOrNull() ?: return DevBuild
            return fromBuildInfo(
                versionName = props?.getProperty("versionName"),
                channel = props?.getProperty("channel"),
            )
        }
    }
}

/**
 * Picks the URL the About row hands to the browser after a successful
 * "update available" check. Pure; unit-tested.
 *
 * The release PAGE (`htmlUrl`) always wins — that is where the CI attaches
 * every per-OS installer. `downloadAssetUrl` is only trusted when the chosen
 * asset is a desktop installer extension: the shared
 * `GitHubReleasesApiImpl.selectAsset` is Android-shaped and its last-resort
 * branch will happily attach an Android `-universal.apk` to a desktop result
 * (the exact false-attach the ADR calls out) — an APK must never become a
 * desktop download link.
 *
 * Whichever URL wins is verified against the compiled-in
 * [GitHubRepoAllowList] before it can reach the browser (both surfaces are
 * server-controlled strings). A violation fails CLOSED to null — never to
 * the other branch — and the caller degrades to the compiled-in
 * [RELEASES_PAGE_URL] snackbar, so a tampered feed can never open a
 * non-GitHub page.
 */
object DesktopUpdateLinks {
    /** Browser-fallback surface when no direct link can be chosen. */
    const val RELEASES_PAGE_URL = "https://github.com/raulshma/jellyplay/releases"

    private val INSTALLER_EXTENSIONS = listOf(".msi", ".deb", ".rpm", ".dmg")

    fun pick(info: AppUpdateInfo?): String? {
        if (info == null) return null
        if (info.htmlUrl.isNotBlank()) {
            return info.htmlUrl.takeIf { GitHubRepoAllowList.isAssetEndpoint(it) }
        }
        val isInstallerAsset = info.downloadAssetName?.lowercase()
            ?.let { name -> INSTALLER_EXTENSIONS.any(name::endsWith) } == true
        val assetUrl = info.downloadAssetUrl
        return if (isInstallerAsset && assetUrl != null && GitHubRepoAllowList.isAssetEndpoint(assetUrl)) {
            assetUrl
        } else {
            null
        }
    }
}

/**
 * The AWT browse half of the ADR's option 2. Headless/locked-down sessions
 * (no `java.awt.Desktop`, no BROWSE action) return `false` so the caller can
 * phrase the snackbar as "download from <releases page>" instead of
 * pretending a browser opened. Callers run this OFF the event thread.
 */
object DesktopUpdateBrowser {
    fun openOrNull(url: String): Boolean {
        if (!java.awt.Desktop.isDesktopSupported()) return false
        val desktop = java.awt.Desktop.getDesktop()
        if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) return false
        return runCatching { desktop.browse(java.net.URI(url)) }.isSuccess
    }
}

/**
 * The desktop `AppUpdateRepository` actual: the shared
 * [AppUpdateRepositoryImpl] wired with the REAL installed version (this is
 * the ADR's "one-line change to the version supplier", done app-side) plus
 * the [DesktopInstalledVersion.DevBuild] suppression — a dev build's
 * `currentVersionName` cannot be made version-comparable, so the decorator
 * pins `isUpdateAvailable` to false instead of resurrecting any sentinel
 * number. Download/pending/cleanup delegate untouched: on desktop the UI
 * gates on `isUpdateAvailable`, so the appdata `updates` dir stays empty
 * (ADR: downloadUpdate/getPendingUpdate remain Android-only-reachable).
 */
class DesktopAppUpdateRepository(
    private val delegate: AppUpdateRepository,
    private val installed: () -> DesktopInstalledVersion,
) : AppUpdateRepository {

    override suspend fun checkForUpdate(): Result<AppUpdateInfo> =
        delegate.checkForUpdate().map { info ->
            if (installed() is DesktopInstalledVersion.DevBuild) {
                info.copy(isUpdateAvailable = false)
            } else {
                info
            }
        }

    override suspend fun downloadUpdate(
        info: AppUpdateInfo,
        onProgress: (Float, Long, Long) -> Unit,
    ): Result<File> = delegate.downloadUpdate(info, onProgress)

    override suspend fun getPendingUpdate() = delegate.getPendingUpdate()

    override fun cleanupDownloadedUpdate() = delegate.cleanupDownloadedUpdate()
}

/** Same directory name as the Android filesDir layout ("updates"). */
private const val UPDATES_DIR = "updates"

/**
 * Fallback current-version string for dev builds, handed to the delegate so
 * the underlying comparator has SOMETHING stable. Never drives a visible
 * decision: [DesktopAppUpdateRepository] suppresses every dev-build result.
 * (Folding to `0.0.0` — i.e. "every release is newer" — is fine here BECAUSE
 * the suppression makes it unobservable; the old sentinel's sin was feeding
 * an unsuppressed comparator.)
 */
private const val DEV_VERSION_NAME = "dev"

/**
 * The Koin override that swaps `desktopDataModule`'s sentinel-bound
 * `AppUpdateRepository` single for the real-version one above. MUST be
 * loaded AFTER `desktopDataModule` (later definition wins) and Main.kt must
 * run its startKoin with `allowOverride(true)` — Koin 4 dropped the
 * per-definition override flag, and the global switch is left on
 * deliberately for this one replacement (the registration guard test ratchets
 * the rest of the module list).
 */
fun desktopAppUpdateModule(dataDir: Path): Module = module {
    single<AppUpdateRepository> {
        val installed = DesktopInstalledVersion.read()
        DesktopAppUpdateRepository(
            delegate = AppUpdateRepositoryImpl(
                gitHubReleasesApi = get(),
                downloadClient = get(NetworkQualifiers.downloadHttpClient),
                // Same "updates" subtree name as the Android filesDir layout.
                updatesDir = File(dataDir.toFile(), UPDATES_DIR),
                currentVersionName = { installed.versionName ?: DEV_VERSION_NAME },
                flavor = "desktop",
                supportedAbis = arrayOf("desktop"),
            ),
            installed = { installed },
        )
    }
}
