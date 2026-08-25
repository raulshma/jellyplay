package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RecommendationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pure home-sections assembly (the ordering/filters half of the
 * jvmShared getHomeSections) extracted for the wasm client: emission order,
 * Next Up exclusion rules, per-library Latest rows, Recently Added insertion
 * position, recommendations fallback, pinned rows last, and the
 * failedSectionTypes accounting.
 */
class HomeSectionsAssemblerTest {

    private fun item(id: String, seriesId: String? = null) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
        seriesId = seriesId,
    )

    private fun folder(id: String, name: String, collectionType: String? = null) =
        LibraryFolder(id = id, name = name, collectionType = collectionType)

    private val query = HomeSectionQuery()

    @Test
    fun `sections emit in canonical order with recently added after latest media`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = query,
                continueWatchingResult = Result.success(listOf(item("cw1"), item("cw2"))),
                nextUpResult = Result.success(listOf(item("nu1", seriesId = "s1"), item("cw1"))),
                foldersResult = Result.success(listOf(folder("f1", "Movies"), folder("f2", "TV"))),
                latestPerFolder = listOf(
                    folder("f1", "Movies") to Result.success(listOf(item("a"), item("cw1"))),
                    folder("f2", "TV") to Result.success(listOf(item("b"))),
                ),
                recommendationsResult = Result.success(RecommendationResult(listOf(item("r1")), item("cw1"))),
                suggestions = emptyList(),
                pinnedSections = listOf(
                    HomeSection(
                        id = "pinned_x",
                        title = "My Pin",
                        type = HomeSectionType.PINNED,
                        items = listOf(item("p1")),
                    ),
                ),
            ),
        )

        val sections = output.result.sections
        assertEquals(
            listOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.NEXT_UP,
                HomeSectionType.LATEST_MEDIA,
                HomeSectionType.LATEST_MEDIA,
                HomeSectionType.RECENTLY_ADDED,
                HomeSectionType.RECOMMENDATIONS,
                HomeSectionType.PINNED,
            ),
            sections.map { it.type },
        )
        assertNull(output.firstError)
        assertTrue(output.result.failedSectionTypes.isEmpty())

        // Next Up drops the Continue Watching duplicate but keeps the rest.
        assertEquals(listOf("nu1"), sections[1].items.map { it.id })

        // One Latest row per library, id/title from the descriptor templates.
        assertEquals("latest_f1", sections[2].id)
        assertEquals("Latest Movies", sections[2].title)
        assertEquals("f1", sections[2].libraryId)
        assertEquals("latest_f2", sections[3].id)

        // Recently Added dedupes across folders and drops CW overlaps.
        assertEquals(listOf("a", "b"), sections[4].items.map { it.id })
        assertEquals("continue_watching", sections[0].id)
        assertEquals("r1", sections[5].items.single().id)
        assertEquals("cw1", sections[5].seedItem?.id)
        assertEquals("p1", sections[6].items.single().id)
    }

    @Test
    fun `next up excludes blocklisted series and hidden continue watching items`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(
                    enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
                    hiddenCwItemIds = setOf("cw1"),
                    nextUpExcludedSeriesIds = setOf("blocked"),
                ),
                continueWatchingResult = Result.success(listOf(item("cw1"), item("cw2"))),
                nextUpResult = Result.success(
                    listOf(item("nu1", seriesId = "blocked"), item("nu2", seriesId = "ok")),
                ),
            ),
        )
        val sections = output.result.sections
        assertEquals(listOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP), sections.map { it.type })
        assertEquals(listOf("cw2"), sections[0].items.map { it.id }, "hidden CW id dropped")
        assertEquals(listOf("nu2"), sections[1].items.map { it.id }, "blocklisted series dropped")
    }

    @Test
    fun `library overrides disable latest media and recently added per folder`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(
                    libraryHomeSectionOverrides = mapOf(
                        "f1" to setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
                        "f2" to setOf(HomeSectionType.LATEST_MEDIA),
                    ),
                ),
                foldersResult = Result.success(listOf(folder("f1", "A"), folder("f2", "B"))),
                latestPerFolder = listOf(
                    folder("f1", "A") to Result.success(listOf(item("a"))),
                    folder("f2", "B") to Result.success(listOf(item("b"))),
                ),
            ),
        )
        // f1: no Latest row and excluded from Recently Added. f2: no Latest
        // row but still feeds Recently Added.
        assertEquals(
            listOf(HomeSectionType.RECENTLY_ADDED),
            output.result.sections.map { it.type },
        )
        assertEquals(listOf("b"), output.result.sections[0].items.map { it.id })
    }

    @Test
    fun `empty recommendations fall back to suggestions`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = query,
                recommendationsResult = Result.success(RecommendationResult(emptyList(), null)),
                suggestions = listOf(item("s1")),
            ),
        )
        val section = output.result.sections.single()
        assertEquals(HomeSectionType.RECOMMENDATIONS, section.type)
        assertEquals(listOf("s1"), section.items.map { it.id })
        assertNull(section.seedItem)
    }

    @Test
    fun `failures record types and surface only when nothing rendered`() {
        val ioError = RuntimeException("boom")
        val partial = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = query,
                continueWatchingResult = Result.failure(ioError),
                nextUpResult = Result.success(emptyList()),
                foldersResult = Result.failure(ioError),
                recommendationsResult = Result.failure(ioError),
            ),
        )
        assertEquals(
            setOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.LATEST_MEDIA,
                HomeSectionType.RECENTLY_ADDED,
                HomeSectionType.RECOMMENDATIONS,
            ),
            partial.result.failedSectionTypes,
        )
        assertEquals(ioError, partial.firstError)
        assertTrue(partial.result.sections.isEmpty(), "nothing rendered — caller throws firstError")

        val perFolderFailure = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.LATEST_MEDIA)),
                foldersResult = Result.success(listOf(folder("f1", "A"))),
                latestPerFolder = listOf(folder("f1", "A") to Result.failure(ioError)),
            ),
        )
        assertEquals(setOf(HomeSectionType.LATEST_MEDIA), perFolderFailure.result.failedSectionTypes)
        assertEquals(ioError, perFolderFailure.firstError)

        // Zero items is NOT a failure.
        val empty = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING)),
                continueWatchingResult = Result.success(emptyList()),
            ),
        )
        assertTrue(empty.result.sections.isEmpty())
        assertTrue(empty.result.failedSectionTypes.isEmpty())
        assertNull(empty.firstError)
    }

    @Test
    fun `recommendations failure with other sections rendered is partial`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = query,
                continueWatchingResult = Result.success(listOf(item("cw1"))),
                recommendationsResult = Result.failure(RuntimeException("rec down")),
            ),
        )
        assertEquals(listOf(HomeSectionType.CONTINUE_WATCHING), output.result.sections.map { it.type })
        assertEquals(setOf(HomeSectionType.RECOMMENDATIONS), output.result.failedSectionTypes)
        assertIs<RuntimeException>(output.firstError)
    }
}
