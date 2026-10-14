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
 * jvmShared getHomeSections): emission order,
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
                continueReadingResult = Result.success(listOf(item("book1").copy(mediaType = MediaType.BOOK))),
                nextUpResult = Result.success(listOf(item("nu1", seriesId = "s1"), item("cw1"))),
                upcomingResult = Result.success(listOf(item("up1"))),
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
                HomeSectionType.CONTINUE_READING,
                HomeSectionType.NEXT_UP,
                HomeSectionType.UPCOMING,
                HomeSectionType.MY_MEDIA,
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
        assertEquals(listOf("nu1"), sections[2].items.map { it.id })
        assertEquals(listOf("book1"), sections[1].items.map { it.id })

        // Upcoming rides verbatim between Next Up and the latest fan-out; the
        // My Media tiles assemble from the same folders read the fan-out uses.
        assertEquals("upcoming", sections[3].id)
        assertEquals("Upcoming", sections[3].title)
        assertEquals(listOf("up1"), sections[3].items.map { it.id })
        assertEquals("my_media", sections[4].id)
        assertEquals(listOf("f1", "f2"), sections[4].items.map { it.id })
        assertEquals(MediaType.FOLDER, sections[4].items[0].mediaType)

        // One Latest row per library, id/title from the descriptor templates.
        assertEquals("latest_f1", sections[5].id)
        assertEquals("Latest Movies", sections[5].title)
        assertEquals("f1", sections[5].libraryId)
        assertEquals("latest_f2", sections[6].id)

        // Recently Added dedupes across folders and drops CW overlaps.
        assertEquals(listOf("a", "b"), sections[7].items.map { it.id })
        assertEquals("continue_watching", sections[0].id)
        assertEquals("r1", sections[8].items.single().id)
        assertEquals("cw1", sections[8].seedItem?.id)
        assertEquals("p1", sections[9].items.single().id)
    }

    @Test
    fun `upcoming emits verbatim between next up and latest media`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.NEXT_UP, HomeSectionType.UPCOMING)),
                nextUpResult = Result.success(listOf(item("nu1", seriesId = "s1"))),
                upcomingResult = Result.success(listOf(item("up1"), item("up2"))),
            ),
        )
        val sections = output.result.sections
        assertEquals(
            listOf(HomeSectionType.NEXT_UP, HomeSectionType.UPCOMING),
            sections.map { it.type },
        )
        assertEquals(listOf("up1", "up2"), sections[1].items.map { it.id }, "upcoming is watch-state-blind: no filters")
        assertNull(output.firstError)
        assertTrue(output.result.failedSectionTypes.isEmpty())
    }

    @Test
    fun `my media assembles library tiles and drops hidden and overridden libraries`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(
                    enabledSections = setOf(HomeSectionType.MY_MEDIA),
                    myMediaHiddenLibraryIds = setOf("f-hidden"),
                    libraryHomeSectionOverrides = mapOf("f-overridden" to setOf(HomeSectionType.MY_MEDIA)),
                ),
                foldersResult = Result.success(
                    listOf(
                        folder("f1", "Movies", "movies"),
                        folder("f-hidden", "Hidden", "tvshows"),
                        folder("f-overridden", "Overridden", "music"),
                        folder("f2", "Books", "books"),
                    ),
                ),
            ),
        )
        val sections = output.result.sections
        assertEquals(listOf(HomeSectionType.MY_MEDIA), sections.map { it.type })
        val tiles = sections.single().items
        // The hidden-set member AND the per-library override member drop; the
        // survivors keep folder order as FOLDER-typed tiles keyed by library id.
        assertEquals(listOf("f1", "f2"), tiles.map { it.id })
        assertEquals(listOf("Movies", "Books"), tiles.map { it.name })
        tiles.forEach { assertEquals(MediaType.FOLDER, it.mediaType) }
    }

    @Test
    fun `my media drops the row when every library is hidden and surfaces a folders failure`() {
        val allHidden = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(
                    enabledSections = setOf(HomeSectionType.MY_MEDIA),
                    myMediaHiddenLibraryIds = setOf("f1"),
                ),
                foldersResult = Result.success(listOf(folder("f1", "A"))),
            ),
        )
        assertTrue(allHidden.result.sections.isEmpty(), "all tiles hidden — the row drops")
        assertTrue(allHidden.result.failedSectionTypes.isEmpty())

        val ioError = RuntimeException("folders down")
        val failed = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.MY_MEDIA)),
                foldersResult = Result.failure(ioError),
            ),
        )
        assertEquals(setOf(HomeSectionType.MY_MEDIA), failed.result.failedSectionTypes)
        assertEquals(ioError, failed.firstError)

        // Disabled → no row, no failure, whatever the folders result.
        val disabled = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(enabledSections = emptySet()),
                foldersResult = Result.failure(ioError),
            ),
        )
        assertTrue(disabled.result.sections.isEmpty())
        assertTrue(disabled.result.failedSectionTypes.isEmpty())
    }

    @Test
    fun `plugin rows emit between discover rows and pinned rows`() {
        // PLUGIN_ROW is not user-configurable, so OrderHomeSectionsUseCase's
        // stable sort lands it (like PINNED) after the standard sections —
        // this emission order IS its position: curated plugin rows ahead of
        // the user's pins.
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = query,
                discoverSections = listOf(
                    HomeSection(
                        id = "discover_r1",
                        title = "My Discover Row",
                        type = HomeSectionType.DISCOVER,
                        items = listOf(item("d1")),
                    ),
                ),
                pluginRowSections = listOf(
                    HomeSection(
                        id = "jellyplay_seasonal",
                        title = "Spooky Season",
                        type = HomeSectionType.PLUGIN_ROW,
                        items = emptyList(),
                    ),
                ),
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

        assertEquals(
            listOf(
                HomeSectionType.DISCOVER,
                HomeSectionType.PLUGIN_ROW,
                HomeSectionType.PINNED,
            ),
            output.result.sections.map { it.type },
        )
        assertNull(output.firstError)
        assertTrue(output.result.failedSectionTypes.isEmpty())
    }

    @Test
    fun `continue reading emits between continue watching and next up and honors hidden cw ids`() {
        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = HomeSectionQuery(
                    enabledSections = setOf(
                        HomeSectionType.CONTINUE_WATCHING,
                        HomeSectionType.CONTINUE_READING,
                        HomeSectionType.NEXT_UP,
                    ),
                    hiddenCwItemIds = setOf("book-hidden"),
                ),
                continueWatchingResult = Result.success(listOf(item("cw1"))),
                continueReadingResult = Result.success(
                    listOf(
                        item("book1").copy(mediaType = MediaType.BOOK),
                        item("book-hidden").copy(mediaType = MediaType.BOOK),
                    ),
                ),
                nextUpResult = Result.success(listOf(item("nu1", seriesId = "s1"))),
            ),
        )

        val sections = output.result.sections
        assertEquals(
            listOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.CONTINUE_READING,
                HomeSectionType.NEXT_UP,
            ),
            sections.map { it.type },
        )
        assertEquals("continue_reading", sections[1].id)
        assertEquals("Continue Reading", sections[1].title)
        // The same hiddenCwItemIds set covers both resume rows.
        assertEquals(listOf("book1"), sections[1].items.map { it.id })
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
                    enabledSections = setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
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
                // MY_MEDIA shares the failed folders read, so it starves too.
                HomeSectionType.MY_MEDIA,
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
