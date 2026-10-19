package com.raulshma.jellyplay.core.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the auto-retry backoff ladder behind [FallbackAsyncImage]'s
 * terminal-error handling. The composable consumes the ladder through
 * [autoRetryBackoffMs]: a full budget must walk it in order (5 s, 15 s, 35 s
 * — the spread rides out a restarting Jellyfin server), a spent budget must
 * park the chain dead (null), and an out-of-range budget must fail safe to
 * "park dead" rather than indexing the schedule.
 */
class MediaImageRetryBackoffTest {

    @Test
    fun fullBudgetWalksScheduleInOrder() {
        assertEquals(5_000L, autoRetryBackoffMs(retriesLeft = 3))
        assertEquals(15_000L, autoRetryBackoffMs(retriesLeft = 2))
        assertEquals(35_000L, autoRetryBackoffMs(retriesLeft = 1))
    }

    @Test
    fun spentBudgetParksDead() {
        assertNull(autoRetryBackoffMs(retriesLeft = 0))
    }

    @Test
    fun outOfRangeBudgetFailsSafeToDead() {
        assertNull(autoRetryBackoffMs(retriesLeft = -1))
        assertNull(autoRetryBackoffMs(retriesLeft = 4))
    }
}
