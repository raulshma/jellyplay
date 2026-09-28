package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [buildVersionTrackOptions] — the player Version sheet's pure row
 * builder: the media-source id rides [TrackOption.id] (the select handler's
 * routing key), the positional index is the lazy-list key, and the selection
 * marker tracks the session's current source.
 */
class VersionOptionsTest {

    private fun source(id: String, name: String = "", height: Int? = null) = MediaSource(
        id = id,
        name = name,
        mediaStreams = if (height == null) emptyList() else listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, height = height),
        ),
    )

    @Test
    fun `each row carries its media source id and a positional index`() {
        val options = buildVersionTrackOptions(
            mediaSources = listOf(source("a"), source("b"), source("c")),
            currentSourceId = "b",
        )

        assertEquals(listOf("a", "b", "c"), options.map { it.id })
        assertEquals(listOf(0, 1, 2), options.map { it.index })
    }

    @Test
    fun `the current source is the selected row`() {
        val options = buildVersionTrackOptions(
            mediaSources = listOf(source("a"), source("b")),
            currentSourceId = "b",
        )

        assertTrue(options.first { it.sourceIdOrThrow() == "b" }.isSelected)
        assertFalse(options.first { it.sourceIdOrThrow() == "a" }.isSelected)
    }

    @Test
    fun `a named source labels as name with the quality suffix`() {
        val options = buildVersionTrackOptions(
            mediaSources = listOf(source("a", name = "4K REMUX", height = 2160)),
            currentSourceId = null,
        )

        assertEquals("4K REMUX · 4K SDR", options.single().label)
    }

    @Test
    fun `an unnamed source labels with the quality ladder only`() {
        val options = buildVersionTrackOptions(
            mediaSources = listOf(source("a", height = 2160)),
            currentSourceId = null,
        )

        assertEquals("4K SDR", options.single().label)
        assertNull(options.single().language)
    }

    @Test
    fun `nothing is selected when the session has no current source`() {
        val options = buildVersionTrackOptions(
            mediaSources = listOf(source("a"), source("b")),
            currentSourceId = null,
        )

        assertFalse(options.any { it.isSelected })
    }

    private fun TrackOption.sourceIdOrThrow(): String = requireNotNull(id)
}
