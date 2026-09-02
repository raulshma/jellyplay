package com.raulshma.jellyplay.core.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Tests [DownloadPauseReason] — the typed vocabulary that previously lived as
 * `const val` strings on DownloadRepositoryImpl's companion and leaked across
 * to the worker. Verifies the persisted-value round-trip the `pausedReason`
 * column relies on.
 */
class DownloadPauseReasonTest {

    @Test
    fun `persisted values match the historical column strings`() {
        // Must stay exactly these strings — existing rows in the DB use them.
        assertEquals("USER", DownloadPauseReason.USER.persistedValue)
        assertEquals("NETWORK", DownloadPauseReason.NETWORK.persistedValue)
    }

    @Test
    fun `fromPersisted round-trips each value`() {
        assertEquals(DownloadPauseReason.USER, DownloadPauseReason.fromPersisted("USER"))
        assertEquals(DownloadPauseReason.NETWORK, DownloadPauseReason.fromPersisted("NETWORK"))
    }

    @Test
    fun `fromPersisted returns null for unknown or null input`() {
        assertNull(DownloadPauseReason.fromPersisted("UNKNOWN"))
        assertNull(DownloadPauseReason.fromPersisted(null))
        assertNull(DownloadPauseReason.fromPersisted(""))
    }

    @Test
    fun `retry budget constant is the documented value`() {
        // Migration/KDoc text references this number; guard against silent bumps.
        assertEquals(3, DOWNLOAD_MAX_AUTO_RETRY)
    }
}
