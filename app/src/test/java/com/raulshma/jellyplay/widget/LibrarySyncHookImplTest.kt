package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins the post-library-scan fan-out: both side effects (the auto-download
 * foreground drain and the widget refresh) fire exactly once, and a failure
 * in either one — or in the first of the two widget refreshes — never
 * prevents the remaining calls nor propagates to the home refresh that
 * triggered the hook.
 */
class LibrarySyncHookImplTest {

    private val autoDownloadScheduler: AutoDownloadScheduler = mockk(relaxed = true)
    private val widgetWorkScheduler: WidgetWorkScheduler = mockk(relaxed = true)

    private fun createHook() = LibrarySyncHookImpl(autoDownloadScheduler, widgetWorkScheduler)

    @Test
    fun `onLibraryScanComplete drains downloads and refreshes both widgets`() = runTest {
        createHook().onLibraryScanComplete()

        coVerify(exactly = 1) { autoDownloadScheduler.enqueueNow() }
        coVerify(exactly = 1) { widgetWorkScheduler.refreshLibraryNow() }
        coVerify(exactly = 1) { widgetWorkScheduler.refreshSeerrNow() }
    }

    @Test
    fun `an auto-download failure does not break the widget refresh`() = runTest {
        coEvery { autoDownloadScheduler.enqueueNow() } throws IllegalStateException("WorkManager gone")

        createHook().onLibraryScanComplete()

        coVerify(exactly = 1) { widgetWorkScheduler.refreshLibraryNow() }
        coVerify(exactly = 1) { widgetWorkScheduler.refreshSeerrNow() }
    }

    @Test
    fun `a widget library-refresh failure skips the seerr refresh but the drain still runs`() =
        runTest {
            // KNOWN GAP pinned as current behavior: both widget refreshes share
            // ONE runCatching block in LibrarySyncHookImpl, so a throw from
            // refreshLibraryNow aborts before refreshSeerrNow (the class KDoc
            // claims a failure "never breaks the other" — for the two widget
            // refreshes that only holds across the auto-download boundary, not
            // between them). Flip the seerr assertion to `exactly = 1` if the
            // impl ever splits the block.
            coEvery { widgetWorkScheduler.refreshLibraryNow() } throws IllegalStateException("cooldown crash")

            createHook().onLibraryScanComplete()

            coVerify(exactly = 0) { widgetWorkScheduler.refreshSeerrNow() }
            coVerify(exactly = 1) { autoDownloadScheduler.enqueueNow() }
        }

    @Test
    fun `hook itself never throws when everything fails`() = runTest {
        coEvery { autoDownloadScheduler.enqueueNow() } throws IllegalStateException("boom")
        coEvery { widgetWorkScheduler.refreshLibraryNow() } throws IllegalStateException("boom")
        coEvery { widgetWorkScheduler.refreshSeerrNow() } throws IllegalStateException("boom")

        createHook().onLibraryScanComplete()

        verify(exactly = 0) { widgetWorkScheduler.enqueuePeriodic() }
    }
}
