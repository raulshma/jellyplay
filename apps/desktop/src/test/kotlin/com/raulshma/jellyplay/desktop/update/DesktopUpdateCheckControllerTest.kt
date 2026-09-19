package com.raulshma.jellyplay.desktop.update

import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.PendingAppUpdate
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Pins the DesktopUpdateCheckController's ADR contract
 * (docs/adr/desktop-auto-update.md — accepted v1: **manual check + open the
 * release page**, never silent download-and-install):
 *
 *  - the check touches ONLY [AppUpdateRepository.checkForUpdate] — the fake
 *    below FAILS the test on download/pending/cleanup, so any drift toward
 *    an in-app install surfaces here, not on a user's machine;
 *  - the browser handoff happens ONLY on UpdateAvailable, off the event
 *    thread (Dispatchers.IO inside runCheck), through the injected
 *    [DesktopUpdateCheckController.openInBrowser] seam — so no test needs an
 *    AWT desktop;
 *  - the snackbar wording pairs the version with the handoff outcome
 *    ([com.raulshma.jellyplay.desktop.update.desktopUpdateText], moved here
 *    from DesktopAppRoot with the controller).
 */
class DesktopUpdateCheckControllerTest {

    /**
     * ADR-trap fake: every NON-check member of the repository throws — the
     * controller structurally holds no download/install surface, and if one
     * ever appears this fake turns the regression into a failing test.
     */
    private class ADRTrapRepository(private val result: Result<AppUpdateInfo>) : AppUpdateRepository {
        override suspend fun checkForUpdate(): Result<AppUpdateInfo> = result

        override suspend fun downloadUpdate(
            info: AppUpdateInfo,
            onProgress: (Float, Long, Long) -> Unit,
        ): Result<File> = error("ADR desktop-auto-update: desktop must never download an update")

        override suspend fun getPendingUpdate(): PendingAppUpdate? =
            error("ADR desktop-auto-update: desktop must never stage a pending update")

        override fun cleanupDownloadedUpdate(): Unit =
            error("ADR desktop-auto-update: desktop must never touch the updates dir")
    }

    private fun info(available: Boolean = true) = AppUpdateInfo(
        latestVersion = "0.11.0",
        htmlUrl = "https://github.com/raulshma/jellyplay/releases/tag/v0.11.0",
        releaseNotes = "notes",
        isUpdateAvailable = available,
        downloadAssetUrl = null,
        downloadAssetName = null,
        releaseSize = 0L,
    )

    @Test
    fun updateAvailable_browserOpened_snackbarAnnouncesTheHandoff() = runTest {
        val openedUrls = mutableListOf<String>()
        val messages = mutableListOf<String>()
        DesktopUpdateCheckController(
            scope = this,
            repository = ADRTrapRepository(Result.success(info(available = true))),
            showMessage = { messages.add(it) },
            openInBrowser = { openedUrls.add(it); true },
            ioDispatcher = EmptyCoroutineContext,
        ).checkForUpdate()
        advanceUntilIdle()

        assertEquals(
            listOf("https://github.com/raulshma/jellyplay/releases/tag/v0.11.0"),
            openedUrls,
            "the release PAGE url is what the browser receives (DesktopUpdateLinks.pick)",
        )
        assertEquals(listOf("Version 0.11.0 is available — opening the release page in your browser."), messages)
    }

    @Test
    fun updateAvailable_noBrowserReachable_snackbarNamesTheReleasesPage() = runTest {
        val openedUrls = mutableListOf<String>()
        val messages = mutableListOf<String>()
        DesktopUpdateCheckController(
            scope = this,
            repository = ADRTrapRepository(Result.success(info(available = true))),
            showMessage = { messages.add(it) },
            openInBrowser = { openedUrls.add(it); false },
            ioDispatcher = EmptyCoroutineContext,
        ).checkForUpdate()
        advanceUntilIdle()

        assertEquals(1, openedUrls.size, "the handoff was ATTEMPTED (headless AWT degrades, it is not skipped)")
        assertTrue(
            messages.single().contains(DesktopUpdateLinks.RELEASES_PAGE_URL),
            "the fallback message must point at the releases page: ${messages.single()}",
        )
    }

    @Test
    fun upToDate_noBrowserHandoff_snackbarIsTheUpToDateWording() = runTest {
        val openedUrls = mutableListOf<String>()
        val messages = mutableListOf<String>()
        DesktopUpdateCheckController(
            scope = this,
            repository = ADRTrapRepository(Result.success(info(available = false))),
            showMessage = { messages.add(it) },
            openInBrowser = { openedUrls.add(it); true },
            ioDispatcher = EmptyCoroutineContext,
        ).checkForUpdate()
        advanceUntilIdle()

        assertTrue(openedUrls.isEmpty(), "an up-to-date check must never open anything")
        assertEquals(listOf("You're up to date"), messages)
    }

    @Test
    fun failedCheck_noBrowserHandoff_snackbarCarriesTheReason() = runTest {
        val openedUrls = mutableListOf<String>()
        val messages = mutableListOf<String>()
        DesktopUpdateCheckController(
            scope = this,
            repository = ADRTrapRepository(Result.failure(java.io.IOException("offline"))),
            showMessage = { messages.add(it) },
            openInBrowser = { openedUrls.add(it); true },
            ioDispatcher = EmptyCoroutineContext,
        ).checkForUpdate()
        advanceUntilIdle()

        assertTrue(openedUrls.isEmpty(), "a failed check must never open anything")
        assertEquals(listOf("Update check failed: offline"), messages)
    }

    @Test
    fun updateAvailable_neverDownloadsOrInstalls_theCheckIsTheOnlyRepositoryTouch() = runTest {
        // The ADR's core promise, stated as a test: even on a genuinely
        // available update, the flow is check → browser, and nothing else —
        // ADRTrapRepository fails the test on ANY download/pending/cleanup
        // call, so reaching the snackbar proves the silent-install path stays
        // absent.
        val messages = mutableListOf<String>()
        DesktopUpdateCheckController(
            scope = this,
            repository = ADRTrapRepository(Result.success(info(available = true))),
            showMessage = { messages.add(it) },
            openInBrowser = { true },
            ioDispatcher = EmptyCoroutineContext,
        ).checkForUpdate()
        advanceUntilIdle()

        assertTrue(
            messages.single().contains("0.11.0"),
            "the available-update wording survived the no-install constraint",
        )
    }
}
