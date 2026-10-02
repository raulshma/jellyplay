package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaSourceType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_version_grouping_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_version_placeholder_badge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [buildVersionPickerOptions] — the version picker's pure selection
 * state: one row per media source, the pending pick marked, quality badges
 * only where the name doesn't already carry the quality, and Grouping /
 * Placeholder sources distinguished from real files.
 */
class VersionPickerOptionsTest {

    private fun source(
        id: String,
        name: String = "",
        height: Int? = null,
        type: MediaSourceType = MediaSourceType.DEFAULT,
    ) = MediaSource(
        id = id,
        name = name,
        type = type,
        mediaStreams = if (height == null) emptyList() else listOf(
            com.raulshma.jellyplay.core.model.MediaStream(
                index = 0,
                type = com.raulshma.jellyplay.core.model.StreamType.VIDEO,
                height = height,
            ),
        ),
    )

    private fun detail(vararg sources: MediaSource) = MediaDetail(
        item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE),
        mediaSources = sources.toList(),
    )

    @Test
    fun `one option per source with nothing selected when no pick is pending`() {
        val options = buildVersionPickerOptions(
            detail(source("a", height = 2160), source("b", height = 1080)),
            selectedSourceId = null,
        )

        assertEquals(2, options.size)
        assertEquals("4K SDR", options[0].label)
        assertEquals("HD SDR", options[1].label)
        assertFalse(options.any { it.isSelected })
    }

    @Test
    fun `the pending pick is the selected row`() {
        val options = buildVersionPickerOptions(
            detail(source("a", height = 2160), source("b", height = 1080)),
            selectedSourceId = "b",
        )

        assertTrue(options.first { it.sourceId == "b" }.isSelected)
        assertFalse(options.first { it.sourceId == "a" }.isSelected)
    }

    @Test
    fun `a named source keeps its name as the label and the quality as the badge`() {
        val options = buildVersionPickerOptions(
            detail(source("a", name = "Theatrical Cut", height = 2160)),
            selectedSourceId = null,
        )

        assertEquals("Theatrical Cut", options.single().label)
        assertEquals("4K SDR", options.single().qualityBadge)
    }

    @Test
    fun `an unnamed source folds the quality into the label without a badge`() {
        val options = buildVersionPickerOptions(
            detail(source("a", height = 2160)),
            selectedSourceId = null,
        )

        assertEquals("4K SDR", options.single().label)
        assertNull(options.single().qualityBadge)
    }

    @Test
    fun `grouping and placeholder sources carry their type badge`() {
        val options = buildVersionPickerOptions(
            detail(source("g", type = MediaSourceType.GROUPING), source("p", type = MediaSourceType.PLACEHOLDER)),
            selectedSourceId = null,
        )

        assertEquals(
            Res.string.detail_version_grouping_badge,
            options[0].typeBadge,
        )
        assertEquals(
            Res.string.detail_version_placeholder_badge,
            options[1].typeBadge,
        )
        assertNull(options.first { it.sourceId == "g" }.qualityBadge)
    }
}
