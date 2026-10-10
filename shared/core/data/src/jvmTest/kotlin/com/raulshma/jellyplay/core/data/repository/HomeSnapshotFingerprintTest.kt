package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.JellyPlayRowEntry
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins [HomeSnapshotFingerprint]'s covered field set — the contract behind
 * `HomeSectionsSnapshotStore.persist`'s cheap-path dedup. A
 * change to any fingerprinted field must flip the fingerprint (else a real
 * content change would be skipped for a full dedup window); a change to a
 * non-fingerprinted metadata field must NOT flip it (the documented
 * one-refresh delay for metadata-only edits).
 */
class HomeSnapshotFingerprintTest {

    private fun item(
        id: String = "item-1",
        positionTicks: Long? = null,
        isPlayed: Boolean = false,
        isFavorite: Boolean = false,
        overview: String? = null,
        communityRating: Float? = null,
        name: String = "Item",
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.MOVIE,
        playbackPositionTicks = positionTicks,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        overview = overview,
        communityRating = communityRating,
    )

    private fun section(
        id: String = "section-1",
        title: String = "Continue Watching",
        type: HomeSectionType = HomeSectionType.CONTINUE_WATCHING,
        libraryId: String? = null,
        collectionType: String? = null,
        seedItemId: String? = null,
        items: List<MediaItem> = listOf(item()),
    ) = HomeSection(
        id = id,
        title = title,
        type = type,
        items = items,
        seedItem = seedItemId?.let { item(id = it) },
        libraryId = libraryId,
        collectionType = collectionType,
    )

    private fun result(
        sections: List<HomeSection> = listOf(section()),
        failedSectionTypes: Set<HomeSectionType> = emptySet(),
    ) = HomeSectionsResult(sections = sections, failedSectionTypes = failedSectionTypes)

    @Test
    fun `identical results produce identical fingerprints`() {
        assertEquals(
            HomeSnapshotFingerprint.of(result()),
            HomeSnapshotFingerprint.of(result()),
        )
    }

    @Test
    fun `failed section types are fingerprinted`() {
        assertNotEquals(
            HomeSnapshotFingerprint.of(result()),
            HomeSnapshotFingerprint.of(result(failedSectionTypes = setOf(HomeSectionType.RECOMMENDATIONS))),
        )
    }

    @Test
    fun `section header identity fields are fingerprinted`() {
        val baseline = HomeSnapshotFingerprint.of(result())
        val mutations = listOf(
            "id" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { it.copy(id = "section-2") })
            },
            "title" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { it.copy(title = "Next Up") })
            },
            "type" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { it.copy(type = HomeSectionType.NEXT_UP) })
            },
            "libraryId" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { it.copy(libraryId = "lib-1") })
            },
            "collectionType" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { it.copy(collectionType = "movies") })
            },
            "seedItemId" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { it.copy(seedItem = item(id = "seed-1")) })
            },
        )
        for ((field, mutate) in mutations) {
            assertNotEquals(baseline, HomeSnapshotFingerprint.of(mutate(result())), field)
        }
    }

    @Test
    fun `item user-data fields are fingerprinted`() {
        val baseline = HomeSnapshotFingerprint.of(result())
        val mutations = listOf(
            "itemId" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { s ->
                    s.copy(items = s.items.map { it.copy(id = "item-2") })
                })
            },
            "position" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { s ->
                    s.copy(items = s.items.map { it.copy(playbackPositionTicks = 600_000_000L) })
                })
            },
            "isPlayed" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { s ->
                    s.copy(items = s.items.map { it.copy(isPlayed = true) })
                })
            },
            "isFavorite" to { r: HomeSectionsResult ->
                r.copy(sections = r.sections.map { s ->
                    s.copy(items = s.items.map { it.copy(isFavorite = true) })
                })
            },
        )
        for ((field, mutate) in mutations) {
            assertNotEquals(baseline, HomeSnapshotFingerprint.of(mutate(result())), field)
        }
    }

    @Test
    fun `plugin row entries are fingerprinted`() {
        // A PLUGIN_ROW section with one resolved card + one fallback tile.
        fun pluginResult(entries: List<JellyPlayRowEntry>) = result(
            sections = listOf(
                section(
                    id = "jellyplay_seasonal",
                    title = "Spooky Season",
                    type = HomeSectionType.PLUGIN_ROW,
                    items = emptyList(),
                ).copy(jellyPlayRowEntries = entries),
            ),
        )
        val baselineEntries = listOf(
            JellyPlayRowEntry(title = "Local One", year = "2001", localItem = item(id = "l1")),
            JellyPlayRowEntry(title = "Unmatched", year = "1954"),
        )
        val baseline = HomeSnapshotFingerprint.of(pluginResult(baselineEntries))

        // Same result → same fingerprint.
        assertEquals(baseline, HomeSnapshotFingerprint.of(pluginResult(baselineEntries)))

        // Everything the row renders flips it: the curation order, either
        // fallback-tile text field, entry membership, and the resolved card's
        // user-data fields.
        val mutations = listOf(
            "entryOrder" to baselineEntries.reversed(),
            "entryTitle" to baselineEntries.map { it.copy(title = "Renamed") },
            "entryYear" to baselineEntries.map { it.copy(year = "1999") },
            "entryMembership" to baselineEntries.take(1),
            "localItemId" to baselineEntries.map { it.copy(localItem = it.localItem?.copy(id = "l2")) },
            "localItemPosition" to baselineEntries.map {
                it.copy(localItem = it.localItem?.copy(playbackPositionTicks = 600_000_000L))
            },
            "localItemPlayed" to baselineEntries.map { it.copy(localItem = it.localItem?.copy(isPlayed = true)) },
            "localItemFavorite" to baselineEntries.map { it.copy(localItem = it.localItem?.copy(isFavorite = true)) },
        )
        for ((field, entries) in mutations) {
            assertNotEquals(baseline, HomeSnapshotFingerprint.of(pluginResult(entries)), field)
        }
    }

    @Test
    fun `item list membership is fingerprinted`() {
        val withExtraItem = result(
            sections = listOf(section(items = listOf(item(), item(id = "item-2")))),
        )
        assertNotEquals(HomeSnapshotFingerprint.of(result()), HomeSnapshotFingerprint.of(withExtraItem))

        val withNoItems = result(sections = listOf(section(items = emptyList())))
        assertNotEquals(HomeSnapshotFingerprint.of(result()), HomeSnapshotFingerprint.of(withNoItems))
    }

    @Test
    fun `section and item order are fingerprinted`() {
        val twoSections = result(
            sections = listOf(section(id = "a"), section(id = "b")),
        )
        assertNotEquals(
            HomeSnapshotFingerprint.of(twoSections),
            HomeSnapshotFingerprint.of(twoSections.copy(sections = twoSections.sections.reversed())),
        )

        val twoItems = result(
            sections = listOf(section(items = listOf(item(id = "a"), item(id = "b")))),
        )
        assertNotEquals(
            HomeSnapshotFingerprint.of(twoItems),
            HomeSnapshotFingerprint.of(twoItems.copy(sections = twoItems.sections.map { s ->
                s.copy(items = s.items.reversed())
            })),
        )
    }

    @Test
    fun `metadata-only item changes do NOT flip the fingerprint - documented caveat`() {
        val metadataOnly = listOf(
            "overview" to { i: MediaItem -> i.copy(overview = "A brand new synopsis") },
            "communityRating" to { i: MediaItem -> i.copy(communityRating = 8.5f) },
            "name" to { i: MediaItem -> i.copy(name = "Renamed") },
        )
        for ((field, mutate) in metadataOnly) {
            val mutated = result(
                sections = listOf(section(items = listOf(mutate(item())))),
            )
            assertEquals(
                HomeSnapshotFingerprint.of(result()),
                HomeSnapshotFingerprint.of(mutated),
                field,
            )
        }
    }
}
