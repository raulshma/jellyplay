package com.raulshma.jellyplay.desktop.update

import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.PendingAppUpdate
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.compareVersions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Pure-logic tests for the desktop auto-update client
 * (docs/adr/desktop-auto-update.md): the installed-version classification
 * from the generated build-info channel, the dev-build suppression in
 * [DesktopAppUpdateRepository], the browser-link picker (with the
 * Android-APK false-attach guard), and the Koin load-order contract the
 * Main.kt override relies on. The network feed itself is the shared
 * GitHubReleasesApiImpl (tested in :shared:core:network); nothing here
 * touches it.
 */
class DesktopAppUpdateTest {

    // ------------------------------------------- DesktopInstalledVersion

    @Test
    fun releaseChannel_withNumericVersion_isRelease() {
        val v = DesktopInstalledVersion.fromBuildInfo(versionName = "0.10.9", channel = "release")
        assertIs<DesktopInstalledVersion.Release>(v)
        assertEquals("0.10.9", v.versionName)
    }

    @Test
    fun releaseChannel_keepsPreReleaseDisplayVersionWhole() {
        // Alpha lanes publish versionName=0.11.0-alpha.1 (packageVersion stays
        // 0.11.0 for jpackage); the display version IS what feeds the
        // comparison, and compareVersions orders it semver-correctly.
        val v = DesktopInstalledVersion.fromBuildInfo(versionName = "0.11.0-alpha.1", channel = "release")
        assertIs<DesktopInstalledVersion.Release>(v)
        assertEquals("0.11.0-alpha.1", v.versionName)
    }

    @Test
    fun devChannel_isAlwaysDevBuild_evenWithRealVersion() {
        // The dev-machine fallback (no -PjellyplayVersion) writes
        // versionName=0.1.0 + channel=dev; that build must never offer updates.
        assertIs<DesktopInstalledVersion.DevBuild>(
            DesktopInstalledVersion.fromBuildInfo(versionName = "0.1.0", channel = "dev"),
        )
    }

    @Test
    fun missingOrPlaceholderVersion_isDevBuild_evenOnReleaseChannel() {
        assertIs<DesktopInstalledVersion.DevBuild>(
            DesktopInstalledVersion.fromBuildInfo(versionName = null, channel = "release"),
        )
        assertIs<DesktopInstalledVersion.DevBuild>(
            DesktopInstalledVersion.fromBuildInfo(versionName = "  ", channel = "release"),
        )
        assertIs<DesktopInstalledVersion.DevBuild>(
            DesktopInstalledVersion.fromBuildInfo(versionName = "dev", channel = "release"),
        )
    }

    @Test
    fun unknownChannel_isDevBuild() {
        assertIs<DesktopInstalledVersion.DevBuild>(
            DesktopInstalledVersion.fromBuildInfo(versionName = "1.2.3", channel = "beta"),
        )
        assertIs<DesktopInstalledVersion.DevBuild>(
            DesktopInstalledVersion.fromBuildInfo(versionName = "1.2.3", channel = null),
        )
    }

    @Test
    fun releaseVersions_orderAgainstFeedTags_semverCorrectly() {
        // The comparison the repository runs (shared compareVersions) — pinned
        // here for the exact desktop release flows:
        val installed = DesktopInstalledVersion.fromBuildInfo("0.10.9", "release")
            as DesktopInstalledVersion.Release
        assertTrue(compareVersions("0.10.10", installed.versionName) > 0, "newer stable wins")
        assertEquals(0, compareVersions("0.10.9", installed.versionName), "same version is up to date")
        assertTrue(compareVersions("0.10.8", installed.versionName) < 0, "older tag never wins")

        val alpha = DesktopInstalledVersion.fromBuildInfo("0.11.0-alpha.1", "release")
            as DesktopInstalledVersion.Release
        assertTrue(compareVersions("0.11.0-alpha.2", alpha.versionName) > 0, "next alpha wins")
        assertTrue(compareVersions("0.11.0", alpha.versionName) > 0, "stable beats its own alphas")
        assertTrue(compareVersions("0.10.9", alpha.versionName) < 0, "older stable is not an update")
    }

    // --------------------------------- DesktopAppUpdateRepository (decorator)

    private class FakeDelegate(private val result: Result<AppUpdateInfo>) : AppUpdateRepository {
        var downloadCalls = 0
        var pendingCalls = 0
        var cleanupCalls = 0

        override suspend fun checkForUpdate(): Result<AppUpdateInfo> = result

        override suspend fun downloadUpdate(
            info: AppUpdateInfo,
            onProgress: (Float, Long, Long) -> Unit,
        ): Result<File> {
            downloadCalls++
            return Result.success(File("unused"))
        }

        override suspend fun getPendingUpdate(): PendingAppUpdate? {
            pendingCalls++
            return null
        }

        override fun cleanupDownloadedUpdate() {
            cleanupCalls++
        }
    }

    private fun info(
        version: String = "0.11.0",
        available: Boolean = true,
        htmlUrl: String = "https://github.com/raulshma/jellyplay/releases/tag/v0.11.0",
        assetName: String? = null,
        assetUrl: String? = null,
    ) = AppUpdateInfo(
        latestVersion = version,
        htmlUrl = htmlUrl,
        releaseNotes = "notes",
        isUpdateAvailable = available,
        downloadAssetUrl = assetUrl,
        downloadAssetName = assetName,
        releaseSize = 0L,
    )

    @Test
    fun devBuild_checkSupposesUpToDate_evenWhenDelegateReportsAnUpdate() = runTest {
        val delegate = FakeDelegate(Result.success(info(available = true)))
        val repo = DesktopAppUpdateRepository(delegate) { DesktopInstalledVersion.DevBuild }
        val result = repo.checkForUpdate().getOrThrow()
        assertTrue(!result.isUpdateAvailable, "dev build must never surface an update")
        assertEquals("0.11.0", result.latestVersion, "version metadata stays intact")
    }

    @Test
    fun releaseBuild_checkPassesDelegateResultThrough() = runTest {
        val delegate = FakeDelegate(Result.success(info(available = true)))
        val repo = DesktopAppUpdateRepository(delegate) {
            DesktopInstalledVersion.Release("0.10.9")
        }
        assertTrue(repo.checkForUpdate().getOrThrow().isUpdateAvailable)

        val upToDateDelegate = FakeDelegate(Result.success(info(available = false)))
        assertTrue(
            !DesktopAppUpdateRepository(upToDateDelegate) {
                DesktopInstalledVersion.Release("0.11.0")
            }.checkForUpdate().getOrThrow().isUpdateAvailable,
        )
    }

    @Test
    fun checkFailure_passesThrough_untouchedBySuppression() = runTest {
        val failure = Result.failure<AppUpdateInfo>(java.io.IOException("offline"))
        val repo = DesktopAppUpdateRepository(FakeDelegate(failure)) { DesktopInstalledVersion.DevBuild }
        assertTrue(repo.checkForUpdate().isFailure, "dev suppression must not eat real failures")
    }

    @Test
    fun downloadPendingCleanup_delegateUntouched() = runTest {
        val delegate = FakeDelegate(Result.success(info()))
        val repo = DesktopAppUpdateRepository(delegate) { DesktopInstalledVersion.DevBuild }
        repo.downloadUpdate(info()) { _, _, _ -> }
        repo.getPendingUpdate()
        repo.cleanupDownloadedUpdate()
        assertEquals(1, delegate.downloadCalls)
        assertEquals(1, delegate.pendingCalls)
        assertEquals(1, delegate.cleanupCalls)
    }

    // ------------------------------------------------- DesktopUpdateLinks

    @Test
    fun releasePageUrl_winsOverAssets() {
        val link = DesktopUpdateLinks.pick(
            info(
                htmlUrl = "https://github.com/raulshma/jellyplay/releases/tag/v0.11.0",
                assetName = "jellyplay-desktop-windows-v0.11.0.msi",
                assetUrl = "https://example.com/jellyplay-desktop-windows-v0.11.0.msi",
            ),
        )
        assertEquals("https://github.com/raulshma/jellyplay/releases/tag/v0.11.0", link)
    }

    @Test
    fun desktopInstallerAsset_usedWhenPageUrlBlank() {
        val link = DesktopUpdateLinks.pick(
            info(
                htmlUrl = "",
                assetName = "jellyplay-desktop-macos-v0.11.0.dmg",
                assetUrl = "https://github.com/raulshma/jellyplay/releases/download/v0.11.0/jellyplay-desktop-macos-v0.11.0.dmg",
            ),
        )
        assertEquals(
            "https://github.com/raulshma/jellyplay/releases/download/v0.11.0/jellyplay-desktop-macos-v0.11.0.dmg",
            link,
        )
    }

    @Test
    fun androidApkAsset_neverBecomesADesktopLink() {
        // The ADR's false-attach guard: GitHubReleasesApiImpl.selectAsset's
        // last-resort branch can attach an Android -universal.apk to a desktop
        // result. An APK must never be handed to a desktop user. (The URLs are
        // allow-listed shapes — the extension guard, not the allow-list, is
        // what must reject these.)
        for (ext in listOf(".apk", ".apk.part")) {
            val link = DesktopUpdateLinks.pick(
                info(
                    htmlUrl = "",
                    assetName = "jellyplay-v0.11.0-phone-universal$ext",
                    assetUrl = "https://github.com/raulshma/jellyplay/releases/download/v0.11.0/jellyplay-v0.11.0-phone-universal$ext",
                ),
            )
            assertNull(link, "APK asset ($ext) must not become a desktop download link")
        }
    }

    @Test
    fun hostilePageUrl_failsClosedToNull_notToTheAssetBranch() {
        // A release html_url off the compiled-in allow-list (here: a
        // moved repo's attacker org) must not reach the browser — and must not
        // silently fall through to the asset branch either: both URLs come
        // from the same untrusted info. The caller degrades to the
        // compiled-in RELEASES_PAGE_URL snackbar.
        val link = DesktopUpdateLinks.pick(
            info(
                htmlUrl = "https://github.com/attacker/jellyplay/releases/tag/v0.11.0",
                assetName = "jellyplay-desktop-windows-v0.11.0.msi",
                assetUrl = "https://github.com/raulshma/jellyplay/releases/download/v0.11.0/jellyplay-desktop-windows-v0.11.0.msi",
            ),
        )
        assertNull(link, "a hostile release page must never become a desktop link")
    }

    @Test
    fun hostileAssetUrl_failsClosedToNull() {
        // Same pin for the installer-asset branch: an off-allow-list URL is
        // rejected even though the name ends in an installer extension.
        val link = DesktopUpdateLinks.pick(
            info(
                htmlUrl = "",
                assetName = "jellyplay-desktop-windows-v0.11.0.msi",
                assetUrl = "https://evil.example.com/jellyplay-desktop-windows-v0.11.0.msi",
            ),
        )
        assertNull(link, "a hostile installer URL must never become a desktop link")
    }

    @Test
    fun nullAndEmptyInfo_pickNothing() {
        assertNull(DesktopUpdateLinks.pick(null))
        assertNull(DesktopUpdateLinks.pick(info(htmlUrl = "", assetName = null, assetUrl = null)))
    }

    // --------------------------------- Koin load-order contract (override)

    private interface ProbeService
    private class EarlyProbe : ProbeService
    private class LateProbe : ProbeService

    @Test
    fun allowOverride_laterModuleWinsTheMapping() {
        // The exact contract Main.kt relies on: with allowOverride(true), the
        // definition loaded LAST (desktopAppUpdateModule, after
        // desktopDataModule) wins the mapping.
        val early = module { single<ProbeService> { EarlyProbe() } }
        val late = module { single<ProbeService> { LateProbe() } }
        try {
            val app = startKoin { allowOverride(true); modules(early, late) }
            assertTrue(app.koin.get<ProbeService>() is LateProbe)
        } finally {
            stopKoin()
        }
    }
}
