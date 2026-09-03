package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of [UpdateDismissPeriod] — how long an update the user
 * dismissed with "Later" stays hidden from the launch-time auto-prompt:
 *
 *  - Each period maps to its exact suppress duration in millis; [UpdateDismissPeriod.NEVER]
 *    suppresses indefinitely (`null`).
 *  - [UpdateDismissPeriod.fromName] is an exact-name lookup: `null` and
 *    unknown names fall back to [UpdateDismissPeriod.DEFAULT] so a corrupted or
 *    legacy persisted string can never disable the update prompt entirely.
 *  - [UpdateDismissPeriod.DEFAULT] is [UpdateDismissPeriod.HOURS_24].
 */
class AppUpdateTest {

    @Test
    fun `suppress durations match the documented periods`() {
        assertEquals(12L * 60 * 60 * 1000, UpdateDismissPeriod.HOURS_12.suppressMs)
        assertEquals(24L * 60 * 60 * 1000, UpdateDismissPeriod.HOURS_24.suppressMs)
        assertEquals(3L * 24 * 60 * 60 * 1000, UpdateDismissPeriod.DAYS_3.suppressMs)
        assertEquals(7L * 24 * 60 * 60 * 1000, UpdateDismissPeriod.WEEK_1.suppressMs)
        assertNull(UpdateDismissPeriod.NEVER.suppressMs)
    }

    @Test
    fun `DEFAULT is HOURS_24`() {
        assertEquals(UpdateDismissPeriod.HOURS_24, UpdateDismissPeriod.DEFAULT)
    }

    @Test
    fun `fromName resolves exact persisted names`() {
        assertEquals(UpdateDismissPeriod.HOURS_12, UpdateDismissPeriod.fromName("HOURS_12"))
        assertEquals(UpdateDismissPeriod.NEVER, UpdateDismissPeriod.fromName("NEVER"))
        assertEquals(UpdateDismissPeriod.WEEK_1, UpdateDismissPeriod.fromName("WEEK_1"))
    }

    @Test
    fun `fromName falls back to DEFAULT for null`() {
        assertEquals(UpdateDismissPeriod.DEFAULT, UpdateDismissPeriod.fromName(null))
    }

    @Test
    fun `fromName falls back to DEFAULT for unknown names`() {
        assertEquals(UpdateDismissPeriod.DEFAULT, UpdateDismissPeriod.fromName("FOREVER"))
        assertEquals(UpdateDismissPeriod.DEFAULT, UpdateDismissPeriod.fromName(""))
    }

    @Test
    fun `fromName is case-sensitive on the persisted name`() {
        // Deliberate: the persisted value is always written as `name` by the
        // settings store; a lowercase string is not a legacy spelling.
        assertEquals(UpdateDismissPeriod.DEFAULT, UpdateDismissPeriod.fromName("hours_24"))
    }

    @Test
    fun `AppUpdateInfo update availability is a plain flag carried from the comparator`() {
        val update = AppUpdateInfo(
            latestVersion = "1.2.3",
            htmlUrl = "https://example.com/release",
            releaseNotes = "notes",
            isUpdateAvailable = true,
            downloadAssetUrl = null,
            downloadAssetName = null,
            releaseSize = 0L,
        )
        assertTrue(update.isUpdateAvailable)
        assertNull(update.downloadAssetUrl)
    }
}
