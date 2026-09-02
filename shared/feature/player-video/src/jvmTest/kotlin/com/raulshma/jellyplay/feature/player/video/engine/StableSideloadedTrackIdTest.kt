package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins [resolveStableSideloadedTrackId] against the id shapes observed in a
 * real trace: side-loaded configuration ids surfacing merge-prefixed
 * (`"3:provider:WYZIE:x"`) and container-demuxed formats sharing the same
 * `"{n}:{m}"` shape (`"0:4"`).
 */
class StableSideloadedTrackIdTest {

    private val configIds = setOf(
        "external:7",
        "provider:WYZIE:1962271058",
        "streaming:WYZIE:1962492725",
    )

    @Test
    fun unprefixedConfigId_passesThrough() {
        assertEquals(
            "provider:WYZIE:1962271058",
            resolveStableSideloadedTrackId("provider:WYZIE:1962271058", configIds),
        )
    }

    @Test
    fun mergePrefixedConfigId_strippedToStableSuffix() {
        // The observed device shape: MergingMediaSource child index + config id.
        assertEquals(
            "provider:WYZIE:1962271058",
            resolveStableSideloadedTrackId("3:provider:WYZIE:1962271058", configIds),
        )
        assertEquals("external:7", resolveStableSideloadedTrackId("1:external:7", configIds))
    }

    @Test
    fun unknownPrefixedFormat_passesThroughUnstripped() {
        // Container-demuxed ids ("0:4") share the {n}:{m} shape but their
        // suffix is not one of ours — must NOT be mangled.
        assertEquals("0:4", resolveStableSideloadedTrackId("0:4", configIds))
        assertEquals("9:not-ours", resolveStableSideloadedTrackId("9:not-ours", configIds))
    }

    @Test
    fun blankOrNull_returnsNull() {
        assertNull(resolveStableSideloadedTrackId(null, configIds))
        assertNull(resolveStableSideloadedTrackId("", configIds))
    }

    @Test
    fun emptyConfigs_prefixStillStrippedOnlyOnMatch() {
        // No known configs at all (no side-loads): everything passes through.
        assertEquals("0:4", resolveStableSideloadedTrackId("0:4", emptySet()))
    }
}
