package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranscodeReasonsRefresherTest {

    @Test
    fun refresh_transcode_clearsImmediatelyThenAppliesAfterWait() = runTest {
        var fetches = 0
        val refresher = TranscodeReasonsRefresher(this) {
            fetches++
            listOf("ContainerNotSupported")
        }
        var cleared = 0
        var applied: List<String>? = null

        refresher.refresh(
            "i1",
            isTranscode = true,
            isCurrent = { true },
            clear = { cleared++ },
            onReasons = { applied = it },
        )

        // The stale-reasons drop is synchronous; the fetch waits for the
        // server to register the session.
        assertEquals(1, cleared)
        runCurrent()
        assertEquals(null, applied)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(listOf("ContainerNotSupported"), applied)
    }

    @Test
    fun refresh_nonTranscode_clearsAndNeverFetches() = runTest {
        var fetches = 0
        val refresher = TranscodeReasonsRefresher(this) {
            fetches++
            emptyList()
        }
        var cleared = 0

        refresher.refresh(
            "i1",
            isTranscode = false,
            isCurrent = { true },
            clear = { cleared++ },
            onReasons = {},
        )
        advanceTimeBy(10_000)

        assertEquals(1, cleared)
        assertEquals(0, fetches)
    }

    @Test
    fun refresh_nullItemId_clearsAndNeverFetches() = runTest {
        var fetches = 0
        val refresher = TranscodeReasonsRefresher(this) {
            fetches++
            emptyList()
        }
        var cleared = 0

        refresher.refresh(
            null,
            isTranscode = true,
            isCurrent = { true },
            clear = { cleared++ },
            onReasons = {},
        )
        advanceTimeBy(10_000)

        assertEquals(1, cleared)
        assertEquals(0, fetches)
    }

    @Test
    fun refresh_emptyFirstFetch_retriesOnceBeforeGivingUp() = runTest {
        var fetches = 0
        val refresher = TranscodeReasonsRefresher(this) {
            fetches++
            if (fetches == 1) emptyList() else listOf("VideoBitrateNotSupported")
        }
        var applied: List<String>? = null

        refresher.refresh(
            "i1",
            isTranscode = true,
            isCurrent = { true },
            clear = {},
            onReasons = { applied = it },
        )
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(null, applied)

        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(2, fetches)
        assertEquals(listOf("VideoBitrateNotSupported"), applied)
    }

    @Test
    fun refresh_staleItem_neverAppliesReasons() = runTest {
        val refresher = TranscodeReasonsRefresher(this) { listOf("ContainerNotSupported") }
        var applied = false

        refresher.refresh(
            "i1",
            isTranscode = true,
            isCurrent = { false },
            clear = {},
            onReasons = { applied = true },
        )
        advanceTimeBy(10_000)

        assertFalse(applied)
    }

    @Test
    fun cancel_preventsPendingFetchFromLanding() = runTest {
        val refresher = TranscodeReasonsRefresher(this) { listOf("ContainerNotSupported") }
        var applied = false

        refresher.refresh(
            "i1",
            isTranscode = true,
            isCurrent = { true },
            clear = {},
            onReasons = { applied = true },
        )
        advanceTimeBy(1_000)
        refresher.cancel()
        advanceTimeBy(10_000)

        assertFalse(applied)
    }

    @Test
    fun refresh_replacesInFlightLookupWithTheNewestResolution() = runTest {
        var fetches = 0
        val refresher = TranscodeReasonsRefresher(this) {
            fetches++
            listOf("reason-$fetches")
        }
        var applied: List<String>? = null

        refresher.refresh(
            "i1",
            isTranscode = true,
            isCurrent = { true },
            clear = {},
            onReasons = { applied = it },
        )
        advanceTimeBy(1_000)
        // A re-resolve (quality change) replaces the first fetch mid-flight.
        refresher.refresh(
            "i1",
            isTranscode = true,
            isCurrent = { true },
            clear = {},
            onReasons = { applied = it },
        )
        advanceTimeBy(10_000)

        // Only the replacement's lookup ran to completion and landed.
        assertEquals(1, fetches)
        assertEquals(listOf("reason-1"), applied)
        assertTrue(applied!!.isNotEmpty())
    }
}
