package com.raulshma.jellyplay.core.model

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pure-JVM tests — [HomeFreshness] is a passive-`now` policy object (callers
 * pass the clock in, same idiom as `TtlCache`'s injectable clock), so the
 * boundary checks need no Android or Robolectric.
 */
class HomeFreshnessTest {

    @Test
    fun isRoomSnapshotFresh_snapshot23h59mOld_isFresh() {
        val fetchedAt = 0L
        val now = HomeFreshness.ROOM_SWR_STALE_MS - 60_000L // 23h59m later
        assertTrue(HomeFreshness.isRoomSnapshotFresh(fetchedAt, now))
    }

    @Test
    fun isRoomSnapshotFresh_snapshot24h01mOld_isStale() {
        val fetchedAt = 0L
        val now = HomeFreshness.ROOM_SWR_STALE_MS + 60_000L // 24h01m later
        assertFalse(HomeFreshness.isRoomSnapshotFresh(fetchedAt, now))
    }

    @Test
    fun isRoomSnapshotFresh_snapshotExactlyAtCeiling_isStale() {
        // Fresh iff `now - fetchedAt < ROOM_SWR_STALE_MS`: exactly 24h old has
        // reached the ceiling, so the snapshot must not instant-paint.
        val fetchedAt = 0L
        val now = HomeFreshness.ROOM_SWR_STALE_MS
        assertFalse(HomeFreshness.isRoomSnapshotFresh(fetchedAt, now))
    }

    @Test
    fun exportedConstants_areAllPositive() {
        // A zero/negative TTL or interval would make caches never hold (or
        // never expire) — cheap sanity that future edits can't land one silently.
        assertTrue(HomeFreshness.NETWORK_SUBCALL_TTL_MS > 0)
        assertTrue(HomeFreshness.REPO_MEMORY_TTL_MS > 0)
        assertTrue(HomeFreshness.ROOM_SWR_STALE_MS > 0)
        assertTrue(HomeFreshness.DISCOVER_TTL_MS > 0)
        assertTrue(HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS > 0)
        assertTrue(HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS > 0)
        assertTrue(HomeFreshness.REFRESH_INTERVAL_BACKGROUND_MS > 0)
        assertTrue(HomeFreshness.MIN_REFRESH_INTERVAL_MS > 0)
        assertTrue(HomeFreshness.USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS > 0)
    }
}
