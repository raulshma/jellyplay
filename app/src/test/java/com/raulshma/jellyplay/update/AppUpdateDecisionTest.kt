package com.raulshma.jellyplay.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure JVM tests for the app-update decision rules split out of `MainViewModel`.
 * No Android, coroutines, or I/O — just the suppression, selection,
 * auto-download, and dismiss-version logic.
 */
class AppUpdateDecisionTest {

    private fun info(
        version: String,
        isUpdateAvailable: Boolean = true,
        downloadAssetUrl: String? = "https://gh/app.apk",
    ) = AppUpdateInfo(
        latestVersion = version,
        htmlUrl = "https://gh",
        releaseNotes = "",
        isUpdateAvailable = isUpdateAvailable,
        downloadAssetUrl = downloadAssetUrl,
        downloadAssetName = "app.apk",
        releaseSize = 1000L,
    )

    // ── isRecentlyDismissed ───────────────────────────────────────────

    @Test
    fun `isRecentlyDismissed true when same version within the suppression window`() {
        assertTrue(
            AppUpdateDecision.isRecentlyDismissed(
                version = "1.2.3",
                dismissedVersion = "1.2.3",
                dismissedAtMs = 1_000L,
                nowMs = 1_000L + AppUpdateDecision.DISMISSED_UPDATE_SUPPRESS_MS,
            )
        )
    }

    @Test
    fun `isRecentlyDismissed false when the version differs`() {
        assertFalse(
            AppUpdateDecision.isRecentlyDismissed(
                version = "1.2.4",
                dismissedVersion = "1.2.3",
                dismissedAtMs = 1_000L,
                nowMs = 2_000L,
            )
        )
    }

    @Test
    fun `isRecentlyDismissed false when no version was ever dismissed`() {
        assertFalse(
            AppUpdateDecision.isRecentlyDismissed(
                version = "1.2.3",
                dismissedVersion = null,
                dismissedAtMs = 0L,
                nowMs = 1_000L,
            )
        )
    }

    @Test
    fun `isRecentlyDismissed false after the suppression window elapses`() {
        assertFalse(
            AppUpdateDecision.isRecentlyDismissed(
                version = "1.2.3",
                dismissedVersion = "1.2.3",
                dismissedAtMs = 1_000L,
                nowMs = 1_000L + AppUpdateDecision.DISMISSED_UPDATE_SUPPRESS_MS + 1L,
            )
        )
    }

    @Test
    fun `isRecentlyDismissed false on negative elapsed (clock skew or future-stamped dismissal)`() {
        assertFalse(
            AppUpdateDecision.isRecentlyDismissed(
                version = "1.2.3",
                dismissedVersion = "1.2.3",
                dismissedAtMs = 5_000L,
                nowMs = 1_000L, // dismissal stamped in the future
            )
        )
    }

    // ── pickUpdateToSurface ─────────────────────────────────────────────────

    @Test
    fun `pickUpdateToSurface returns pending when remote is null`() {
        val pending = info("1.0.0")
        assertEquals(pending, AppUpdateDecision.pickUpdateToSurface(pending = pending, remote = null))
    }

    @Test
    fun `pickUpdateToSurface returns remote when pending is null`() {
        val remote = info("1.0.0")
        assertEquals(remote, AppUpdateDecision.pickUpdateToSurface(pending = null, remote = remote))
    }

    @Test
    fun `pickUpdateToSurface returns null when both are null`() {
        assertNull(AppUpdateDecision.pickUpdateToSurface(pending = null, remote = null))
    }

    @Test
    fun `pickUpdateToSurface prefers the newer remote version`() {
        val pending = info("1.0.0")
        val remote = info("1.1.0")
        assertEquals(remote, AppUpdateDecision.pickUpdateToSurface(pending = pending, remote = remote))
    }

    @Test
    fun `pickUpdateToSurface keeps the pending APK on a version tie so the download is not wasted`() {
        val pending = info("1.1.0")
        val remote = info("1.1.0")
        assertEquals(pending, AppUpdateDecision.pickUpdateToSurface(pending = pending, remote = remote))
    }

    @Test
    fun `pickUpdateToSurface keeps pending when pending is newer`() {
        val pending = info("1.2.0")
        val remote = info("1.1.0")
        assertEquals(pending, AppUpdateDecision.pickUpdateToSurface(pending = pending, remote = remote))
    }

    // ── shouldAutoDownload ────────────────────────────────────────────

    @Test
    fun `shouldAutoDownload true only when enabled and an asset url exists`() {
        assertTrue(AppUpdateDecision.shouldAutoDownload(autoDownloadEnabled = true, info = info("1.0.0")))
    }

    @Test
    fun `shouldAutoDownload false when disabled`() {
        assertFalse(AppUpdateDecision.shouldAutoDownload(autoDownloadEnabled = false, info = info("1.0.0")))
    }

    @Test
    fun `shouldAutoDownload false when no downloadable asset`() {
        assertFalse(
            AppUpdateDecision.shouldAutoDownload(
                autoDownloadEnabled = true,
                info = info("1.0.0", downloadAssetUrl = null),
            )
        )
    }

    // ── dismissedVersion ──────────────────────────────────────────────

    @Test
    fun `dismissedVersion extracts from UpdateAvailable, Downloading, and Downloaded`() {
        assertEquals("1.2.3", AppUpdateDecision.dismissedVersion(UpdateState.UpdateAvailable(info("1.2.3"))))
        assertEquals("1.2.3", AppUpdateDecision.dismissedVersion(UpdateState.Downloading(info("1.2.3"), 0.5f, 500L, 1000L)))
        assertEquals("1.2.3", AppUpdateDecision.dismissedVersion(UpdateState.Downloaded(info("1.2.3"), File("x.apk"))))
    }

    @Test
    fun `dismissedVersion is null for non-dismissible states`() {
        assertNull(AppUpdateDecision.dismissedVersion(UpdateState.Idle))
        assertNull(AppUpdateDecision.dismissedVersion(UpdateState.Checking))
        assertNull(AppUpdateDecision.dismissedVersion(UpdateState.Error("boom")))
        assertNull(AppUpdateDecision.dismissedVersion(UpdateState.NoUpdate(info("1.2.3"))))
    }
}
