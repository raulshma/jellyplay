package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionType.CONTINUE_WATCHING
import com.raulshma.jellyplay.core.model.HomeSectionType.LATEST_MEDIA
import com.raulshma.jellyplay.core.model.HomeSectionType.NEXT_UP
import com.raulshma.jellyplay.core.model.HomeSectionType.RECENTLY_ADDED
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pure unit tests for the section ordering + Continue-Watching/Next-Up merge
 * extracted from HomeViewModel. No Android, no coroutines.
 */
class OrderHomeSectionsUseCaseTest {

    private val useCase = OrderHomeSectionsUseCase()

    @Test
    fun orders_byConfiguredOrder_unknownTypesLast() {
        val sections = listOf(
            section(LATEST_MEDIA),
            section(CONTINUE_WATCHING),
            section(RECENTLY_ADDED),
        )
        val order = listOf(CONTINUE_WATCHING, RECENTLY_ADDED, LATEST_MEDIA)

        val result = useCase(sections, order, mergeContinueWatchingAndNextUp = false)

        assertEquals(
            listOf(CONTINUE_WATCHING, RECENTLY_ADDED, LATEST_MEDIA),
            result.map { it.type },
        )
    }

    @Test
    fun unknownTypes_keepOriginalRelativeOrder_afterKnownOnes() {
        // LATEST_MEDIA is not in the order list → should land after known types,
        // preserving its input position relative to other unknowns.
        val sections = listOf(
            section(LATEST_MEDIA),
            section(RECENTLY_ADDED),
        )
        val order = listOf(RECENTLY_ADDED)

        val result = useCase(sections, order, mergeContinueWatchingAndNextUp = false)

        assertEquals(listOf(RECENTLY_ADDED, LATEST_MEDIA), result.map { it.type })
    }

    @Test
    fun merge_off_whenNoNextUp_passesThrough() {
        val cw = section(CONTINUE_WATCHING, items = listOf(item("cw1")))
        val sections = listOf(cw)

        val result = useCase(sections, order = emptyList(), mergeContinueWatchingAndNextUp = false)

        assertEquals(1, result.size)
        assertEquals(listOf("cw1"), result[0].items.map { it.id })
    }

    @Test
    fun merge_on_appendsNextUpToContinueWatching_deDupedByItem() {
        val cw = section(
            CONTINUE_WATCHING,
            items = listOf(item("cw1"), item("shared")),
        )
        val nextUp = section(
            NEXT_UP,
            items = listOf(item("shared"), item("next1")),
        )
        val sections = listOf(cw, nextUp)

        val result = useCase(sections, order = emptyList(), mergeContinueWatchingAndNextUp = true)

        val types = result.map { it.type }
        assertFalse(types.contains(NEXT_UP), "Next Up section should be dropped on merge")
        val mergedCw = result.first { it.type == CONTINUE_WATCHING }
        // De-duped by id; NEXT_UP's "shared" should not re-append.
        assertEquals(listOf("cw1", "shared", "next1"), mergedCw.items.map { it.id })
    }

    @Test
    fun merge_on_withNextUpButNoCw_relablesNextUpAsCw() {
        val nextUp = section(NEXT_UP, items = listOf(item("next1")))
        val sections = listOf(nextUp)

        val result = useCase(sections, order = emptyList(), mergeContinueWatchingAndNextUp = true)

        assertEquals(listOf(CONTINUE_WATCHING), result.map { it.type })
        assertEquals(listOf("next1"), result[0].items.map { it.id })
    }

    @Test
    fun merge_on_withNeitherCwNorNextUp_passesThroughOrdered() {
        val sections = listOf(section(LATEST_MEDIA), section(RECENTLY_ADDED))

        val result = useCase(sections, order = emptyList(), mergeContinueWatchingAndNextUp = true)

        assertEquals(2, result.size)
        assertTrue(result.all { it.type != CONTINUE_WATCHING && it.type != NEXT_UP })
    }

    @Test
    fun merge_on_preservesOtherSectionsUntouched() {
        val cw = section(CONTINUE_WATCHING, items = listOf(item("cw1")))
        val nextUp = section(NEXT_UP, items = listOf(item("next1")))
        val latest = section(LATEST_MEDIA, items = listOf(item("lat1")))
        val sections = listOf(latest, cw, nextUp)

        val result = useCase(sections, order = emptyList(), mergeContinueWatchingAndNextUp = true)

        val latestResult = result.first { it.type == LATEST_MEDIA }
        assertEquals(listOf("lat1"), latestResult.items.map { it.id })
    }

    private fun section(
        type: HomeSectionType,
        items: List<MediaItem> = emptyList(),
    ) = HomeSection(
        id = type.name,
        title = type.displayName,
        type = type,
        items = items,
    )

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)
}
