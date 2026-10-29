package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [JellyPlayContractMismatch.supportedRangeLabel] — the range
 * presentation fold the settings screen's error banner and the admin
 * dashboard's one-line hint both consume (the fold's former verbatim
 * duplicates in feature/settings and feature/admin).
 */
class JellyPlayContractMismatchTest {

    /** A single-version range renders as the bare version, no dash. */
    @Test
    fun supportedRangeLabel_singleVersionRange() {
        val mismatch = JellyPlayContractMismatch(
            servedContractVersion = 2,
            supportedRange = 1..1,
            pluginVersion = "1.4.0",
        )

        assertEquals("1", mismatch.supportedRangeLabel)
    }

    /** A real range renders `first–last` with the en-dash separator. */
    @Test
    fun supportedRangeLabel_range() {
        val mismatch = JellyPlayContractMismatch(
            servedContractVersion = 9,
            supportedRange = 1..2,
            pluginVersion = "1.4.0",
        )

        assertEquals("1–2", mismatch.supportedRangeLabel)
    }
}
