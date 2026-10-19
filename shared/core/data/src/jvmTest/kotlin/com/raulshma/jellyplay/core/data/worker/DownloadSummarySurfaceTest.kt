package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Pins the download summary surface's ONE rule: the port sees count
 * TRANSITIONS only. Room re-runs the in-flight COUNT on every table
 * invalidation — including the per-2 s progress ticks — so same-count
 * re-emissions are the common case and must collapse before the port (the
 * Android-side memo in DownloadNotificationHelper stays as the second belt,
 * still behind the port). Also pins the port's best-effort contract at the
 * surface: a failed post must not kill the collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSummarySurfaceTest {

    /** Recording fake port; [failNext] simulates a one-shot platform hiccup. */
    private class RecordingNotifier : DownloadProgressNotifier {
        val posts = mutableListOf<Int>()
        var failNext = false

        override fun refreshSummary(inFlightCount: Int) {
            if (failNext) {
                failNext = false
                throw IllegalStateException("post hiccup")
            }
            posts += inFlightCount
        }
    }

    @Test
    fun `posts on transitions only - same-count re-emissions collapse`() = runTest {
        val notifier = RecordingNotifier()
        // A cold flow with repeated values stands in for the Room count flow:
        // every table write re-emits, ticks included, same count and all.
        val counts = flowOf(0, 0, 0, 2, 2, 1, 0)

        val surface = DownloadSummarySurface(counts, notifier, scope = backgroundScope)
        surface.start().join()

        assertEquals(listOf(0, 2, 1, 0), notifier.posts)
    }

    @Test
    fun `start is idempotent - the active collector is not duplicated`() = runTest {
        val notifier = RecordingNotifier()
        val counts = flowOf(0, 1)

        val surface = DownloadSummarySurface(counts, notifier, scope = backgroundScope)
        val first = surface.start()
        val second = surface.start()
        first.join()

        assertSame(first, second)
        assertEquals(listOf(0, 1), notifier.posts)
    }

    @Test
    fun `a failed post does not kill the collector - later transitions still post`() = runTest {
        val notifier = RecordingNotifier().apply { failNext = true }
        val counts = flowOf(0, 1, 2)

        val surface = DownloadSummarySurface(counts, notifier, scope = backgroundScope)
        surface.start().join()

        // The first post (0) hiccuped; the surface kept collecting.
        assertEquals(listOf(1, 2), notifier.posts)
    }
}
