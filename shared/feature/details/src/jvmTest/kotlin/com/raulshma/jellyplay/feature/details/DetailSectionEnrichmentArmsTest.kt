package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.SearchResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The wave-2 section modules' enrichment arms + admissions, in the registry
 * fake-host style ([DetailSectionRegistryTest] — the [DetailSectionEnrichmentHost]
 * interface is the test surface): the cases moved out of
 * DetailViewModelEnrichmentsTest (franchise, upcoming, collectionItems) and
 * DetailSectionAdmissionTest (the four kinds' fold-row pins) when their
 * sections joined the registry. The VM-level fan-out matrix keeps its own
 * pins (the arms fire through the registry splice there).
 */
class DetailSectionEnrichmentArmsTest {

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun item(id: String, type: MediaType, seriesId: String? = null) =
        MediaItem(id = id, name = "Item $id", mediaType = type, seriesId = seriesId)

    private fun detailOf(item: MediaItem) = MediaDetail(item = item)

    /** A REMOTE detail snapshot whose discovery is (or isn't) allowed — the arms' gate. */
    private fun inputs(item: MediaItem, remoteDiscoveryAllowed: Boolean = true) =
        DetailEnrichmentInputs(
            itemId = item.id,
            snapshot = MediaDetailSnapshot(
                detail = detailOf(item),
                context = DetailContext(
                    origin = DetailOrigin.REMOTE,
                    connectivity = RemoteConnectivity.AVAILABLE,
                    download = null,
                    syncState = null,
                    seriesAggregate = null,
                ),
                capabilities = DetailCapabilities(
                    remoteDiscovery = remoteDiscoveryAllowed,
                    remoteStreamSelection = remoteDiscoveryAllowed,
                    localSubtitleSelection = false,
                    localStreamInfo = false,
                    personNavigation = remoteDiscoveryAllowed,
                    studioNavigation = remoteDiscoveryAllowed,
                    smartPlay = remoteDiscoveryAllowed,
                    remoteWorkAllowed = remoteDiscoveryAllowed,
                    localDownloadManagement = false,
                    tagNavigation = remoteDiscoveryAllowed,
                    chapters = false,
                ),
                assets = DetailAssets(),
            ),
        )

    /**
     * The full [DetailSectionEnrichmentHost] fake: canned reads (keyed like
     * the former MockK stubs), the recorded calls the "never started"
     * assertions read, and the state writes captured from [updateState].
     */
    private class FakeHost(
        private val preferences: DetailPreferences = DetailPreferences(),
        private val specialFeaturesResult: Result<List<MediaItem>> = Result.success(emptyList()),
        private val boxSetResults: Map<String, Result<List<CollectionSummary>>> = emptyMap(),
        private val collectionResults: Map<String, Result<SearchResult>> = emptyMap(),
        private val upcomingResult: Result<List<MediaItem>> = Result.success(emptyList()),
        private val stale: Boolean = false,
    ) : DetailSectionEnrichmentHost {
        var written: DetailUiState? = null
        val boxSetCalls = mutableListOf<String>()
        val collectionCalls = mutableListOf<String>()
        val upcomingCalls = mutableListOf<String>()

        override fun isCurrent(itemId: String): Boolean = !stale

        override fun updateState(reduce: (DetailUiState) -> DetailUiState) {
            written = reduce(DetailUiState())
        }

        override suspend fun pluginFeature(feature: String, itemId: String): Boolean = false

        override suspend fun mdbListRatings(imdbId: String): Result<List<JellyPlayPluginRating>?>? = null

        override suspend fun specialFeatures(itemId: String): Result<List<MediaItem>> =
            specialFeaturesResult

        override suspend fun collectionItems(
            collectionId: String,
            startIndex: Int,
            limit: Int,
        ): Result<SearchResult> {
            collectionCalls += collectionId
            return collectionResults[collectionId] ?: Result.success(
                SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
            )
        }

        override suspend fun boxSetsContaining(itemId: String): Result<List<CollectionSummary>> {
            boxSetCalls += itemId
            return boxSetResults[itemId] ?: Result.success(emptyList())
        }

        override suspend fun upcomingEpisodes(parentId: String, limit: Int): Result<List<MediaItem>> {
            upcomingCalls += parentId
            return upcomingResult
        }

        override fun detailPreferences(): DetailPreferences = preferences

        override suspend fun <T, R> mapConcurrent(items: List<T>, transform: suspend (T) -> R): List<R> =
            items.mapNotNull { item ->
                runCatching { transform(item) }.getOrNull()
            }
    }

    private fun boxSet(id: String, name: String) = CollectionSummary(id = id, name = name, itemCount = 2)

    private fun members(vararg items: MediaItem) = SearchResult(
        items = items.toList(),
        totalRecordCount = items.size,
        startIndex = 0,
    )

    // ── the franchise arm (moved from DetailViewModelEnrichmentsTest) ─────

    @Test
    fun franchise_arm_mapsBoxSetAncestorsToRows_selfFiltered() = runTest {
        val host = FakeHost(
            boxSetResults = mapOf(
                "m1" to Result.success(listOf(boxSet("box1", "Fast Saga"), boxSet("box2", "Spy Universe"))),
            ),
            collectionResults = mapOf(
                "box1" to Result.success(
                    members(
                        item("m1", MediaType.MOVIE),
                        item("f2", MediaType.MOVIE),
                    ),
                ),
                "box2" to Result.success(members(item("s2", MediaType.MOVIE))),
            ),
        )
        val enrichment = MoreInCollectionSectionModule.enrichments(host).single()
        assertEquals("franchise", enrichment.name)
        val armInputs = inputs(item("m1", MediaType.MOVIE))
        assertTrue(enrichment.gate(armInputs), "remote movie → the arm may start")

        enrichment.run(armInputs)

        val rows = host.written?.franchiseCollections
        assertEquals(listOf("box1", "box2"), rows?.map { it.id })
        // Members keep the server-default order and never contain the loaded
        // item itself.
        assertEquals(listOf("f2"), rows?.first()?.members?.map { it.id })
        assertEquals(listOf("s2"), rows?.last()?.members?.map { it.id })
    }

    @Test
    fun franchise_arm_capsAtTwoBoxSets_andDropsEmptyRows() = runTest {
        val host = FakeHost(
            boxSetResults = mapOf(
                "m2" to Result.success(listOf(boxSet("box1", "One"), boxSet("box2", "Two"), boxSet("box3", "Three"))),
            ),
            collectionResults = mapOf(
                "box1" to Result.success(members()),
                "box2" to Result.success(members(item("s1", MediaType.MOVIE))),
            ),
        )

        MoreInCollectionSectionModule.enrichments(host).single().run(inputs(item("m2", MediaType.MOVIE)))

        // Two ancestors taken (the cap), the empty first row dropped.
        assertEquals(listOf("box2"), host.written?.franchiseCollections?.map { it.id })
    }

    @Test
    fun franchise_arm_ancestorsFetchFailure_leavesRowsEmpty() = runTest {
        val host = FakeHost(
            boxSetResults = mapOf("m3" to Result.failure(RuntimeException("down"))),
        )

        MoreInCollectionSectionModule.enrichments(host).single().run(inputs(item("m3", MediaType.MOVIE)))

        // Silent absence: the failure never surfaces and the members read
        // never starts.
        assertTrue(host.written?.franchiseCollections?.isEmpty() ?: true)
        assertTrue(host.collectionCalls.isEmpty())
    }

    @Test
    fun franchise_arm_preferenceOff_startsNoFetch() = runTest {
        val host = FakeHost(preferences = DetailPreferences(showMoreInCollection = false))

        val armInputs = inputs(item("m4", MediaType.MOVIE))
        assertTrue(MoreInCollectionSectionModule.enrichments(host).single().gate(armInputs))
        MoreInCollectionSectionModule.enrichments(host).single().run(armInputs)

        assertTrue(host.boxSetCalls.isEmpty())
        assertTrue(host.written?.franchiseCollections?.isEmpty() ?: true)
    }

    @Test
    fun franchise_arm_gate_excludesEpisodeDetail() = runTest {
        // The D6 gate is MOVIE || SERIES — an EPISODE detail never starts the
        // ancestor read (its BoxSet ancestry would key on the episode id).
        val host = FakeHost()
        val armInputs = inputs(item("e9", MediaType.EPISODE, seriesId = "s9"))
        assertFalse(MoreInCollectionSectionModule.enrichments(host).single().gate(armInputs))
        assertTrue(host.boxSetCalls.isEmpty())
    }

    // ── the upcoming arm (moved from DetailViewModelEnrichmentsTest) ──────

    @Test
    fun upcoming_arm_fetchesKeyedOnTheSeriesId() = runTest {
        val host = FakeHost(
            upcomingResult = Result.success(
                listOf(
                    MediaItem(
                        id = "up1",
                        name = "Next week",
                        mediaType = MediaType.EPISODE,
                        seriesId = "s1",
                        premiereDate = "2026-10-20T00:00:00.000Z",
                    ),
                ),
            ),
        )
        val enrichment = UpcomingEpisodesSectionModule.enrichments(host).single()
        assertEquals("upcoming", enrichment.name)
        val armInputs = inputs(item("s1", MediaType.SERIES))
        assertTrue(enrichment.gate(armInputs), "remote SERIES → the arm may start")

        enrichment.run(armInputs)

        // The strip keyed on the SERIES id (parentId = s1, the loaded item).
        assertEquals(listOf("s1"), host.upcomingCalls)
        assertEquals(listOf("up1"), host.written?.upcomingEpisodes?.map { it.id })
    }

    @Test
    fun upcoming_arm_gate_excludesEpisodeDetail() = runTest {
        // The keying rule: an EPISODE detail's series id is NOT its item id,
        // and the strip must not fire keyed on the wrong id — it must not
        // fire at all (the SERIES-only gate excludes re-keying entirely).
        val host = FakeHost()
        val armInputs = inputs(item("e1", MediaType.EPISODE, seriesId = "s1"))
        assertFalse(UpcomingEpisodesSectionModule.enrichments(host).single().gate(armInputs))
        assertTrue(host.upcomingCalls.isEmpty())
    }

    @Test
    fun upcoming_arm_preferenceOffOrFetchFailure_leavesTheStripAbsent() = runTest {
        // Preference off: the body-pref half stops the fetch before it starts.
        val gated = FakeHost(preferences = DetailPreferences(showUpcomingStrip = false))
        val seriesInputs = inputs(item("s2", MediaType.SERIES))
        assertTrue(UpcomingEpisodesSectionModule.enrichments(gated).single().gate(seriesInputs))
        UpcomingEpisodesSectionModule.enrichments(gated).single().run(seriesInputs)
        assertTrue(gated.upcomingCalls.isEmpty())

        // Fetch failure — silent absence, never an error surface.
        val failing = FakeHost(upcomingResult = Result.failure(RuntimeException("server down")))
        UpcomingEpisodesSectionModule.enrichments(failing).single().run(inputs(item("s3", MediaType.SERIES)))
        assertEquals(listOf("s3"), failing.upcomingCalls)
        assertTrue(failing.written?.upcomingEpisodes?.isEmpty() ?: true)
    }

    // ── the collectionItems arm (moved from DetailViewModelEnrichmentsTest) ─

    @Test
    fun collectionItems_arm_writesTheMembersPage() = runTest {
        val host = FakeHost(
            collectionResults = mapOf(
                "c1" to Result.success(members(item("ci1", MediaType.MOVIE))),
            ),
        )
        val enrichment = CollectionItemsSectionModule.enrichments(host).single()
        assertEquals("collectionItems", enrichment.name)
        val armInputs = inputs(item("c1", MediaType.COLLECTION))
        assertTrue(enrichment.gate(armInputs), "remote COLLECTION → the arm may start")

        enrichment.run(armInputs)

        assertEquals(listOf("c1"), host.collectionCalls)
        assertEquals(listOf("ci1"), host.written?.collectionItems?.map { it.id })
    }

    @Test
    fun collectionItems_arm_gate_requiresTheCollectionFamilyAndRemoteDiscovery() = runTest {
        val host = FakeHost()
        assertFalse(CollectionItemsSectionModule.enrichments(host).single().gate(inputs(item("m1", MediaType.MOVIE))))
        // A local collection never starts it (the remote-only companion rule).
        val local = DetailEnrichmentInputs(
            itemId = "c2",
            snapshot = MediaDetailSnapshot(
                detail = detailOf(item("c2", MediaType.COLLECTION)),
                context = DetailContext(
                    origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                    connectivity = RemoteConnectivity.BLOCKED,
                    download = null,
                    syncState = null,
                    seriesAggregate = null,
                ),
                capabilities = DetailCapabilities(
                    remoteDiscovery = false,
                    remoteStreamSelection = false,
                    localSubtitleSelection = true,
                    localStreamInfo = true,
                    personNavigation = false,
                    studioNavigation = false,
                    smartPlay = false,
                    remoteWorkAllowed = false,
                    localDownloadManagement = true,
                    tagNavigation = false,
                    chapters = false,
                ),
                assets = DetailAssets(),
            ),
        )
        assertFalse(CollectionItemsSectionModule.enrichments(host).single().gate(local))
        assertTrue(host.collectionCalls.isEmpty())
    }

    // ── the specialFeatures arm (the moved fold-gate pin's fetch half) ────

    @Test
    fun specialFeatures_arm_writesTheExtras_andGateRequiresRemoteDiscovery() = runTest {
        val extra = item("extra1", MediaType.MOVIE)
        val host = FakeHost(specialFeaturesResult = Result.success(listOf(extra)))
        val enrichment = SpecialFeaturesSectionModule.enrichments(host).single()
        assertEquals("specialFeatures", enrichment.name)
        val armInputs = inputs(item("m1", MediaType.MOVIE))
        assertTrue(enrichment.gate(armInputs))

        enrichment.run(armInputs)

        assertEquals(listOf(extra), host.written?.specialFeatures)

        // A discovery-capped snapshot never starts the fetch.
        val cold = FakeHost()
        assertFalse(SpecialFeaturesSectionModule.enrichments(cold).single().gate(inputs(item("m2", MediaType.MOVIE), remoteDiscoveryAllowed = false)))
        assertTrue(cold.written == null)
    }

    // ── module admissions (moved from DetailSectionAdmissionTest) ─────────

    private fun admissionState(
        item: MediaItem,
        preferences: DetailPreferences = DetailPreferences(),
        build: DetailUiState.() -> DetailUiState = { this },
    ): DetailContentState = DetailContentState(
        core = DetailUiState(
            detail = detailOf(item),
            origin = DetailOrigin.REMOTE,
            capabilities = DetailCapabilities(
                remoteDiscovery = true,
                remoteStreamSelection = true,
                localSubtitleSelection = true,
                localStreamInfo = true,
                personNavigation = true,
                studioNavigation = true,
                smartPlay = true,
                remoteWorkAllowed = true,
                localDownloadManagement = true,
                tagNavigation = true,
                chapters = true,
            ),
        ).build(),
        itemId = item.id,
        preferences = preferences,
    )

    @Test
    fun collectionItems_admitsOnlyForCollections_atSlot7() {
        val detail = detailOf(item("c1", MediaType.COLLECTION))
        assertTrue(CollectionItemsSectionModule.isAdmitted(admissionState(item("c1", MediaType.COLLECTION)), detail))
        assertEquals(7, DetailSectionKind.COLLECTION_ITEMS.delayIndex)
        // Any other family: no slot at all — the empty-members gate stays
        // render-side, admission checks only the media type.
        assertFalse(
            CollectionItemsSectionModule.isAdmitted(
                admissionState(item("m1", MediaType.MOVIE)),
                detailOf(item("m1", MediaType.MOVIE)),
            ),
        )
    }

    @Test
    fun specialFeatures_admitsOnRemoteDiscoveryAndContent() {
        val movie = item("m1", MediaType.MOVIE)
        val remote = admissionState(movie)
        // No extras fetched → silent absence (the silent-absence contract).
        assertFalse(SpecialFeaturesSectionModule.isAdmitted(remote, detailOf(movie)))
        // Extras fetched → admitted at its slot.
        val hydrated = admissionState(movie) { copy(specialFeatures = listOf(item("sf1", MediaType.MOVIE))) }
        assertTrue(SpecialFeaturesSectionModule.isAdmitted(hydrated, detailOf(movie)))
        assertEquals(13, DetailSectionKind.SPECIAL_FEATURES.delayIndex)
        // A discovery-capped snapshot never admits, content or not.
        val capped = DetailContentState(
            core = hydrated.core.copy(capabilities = hydrated.core.capabilities.copy(remoteDiscovery = false)),
            itemId = movie.id,
        )
        assertFalse(SpecialFeaturesSectionModule.isAdmitted(capped, detailOf(movie)))
    }

    @Test
    fun upcomingStrip_admitsOnContentAndPreference_andTheSeriesTrioKeepsItsOrder() {
        val series = item("s1", MediaType.SERIES)
        val strip = listOf(item("up1", MediaType.EPISODE, seriesId = "s1"))
        // No strip content (the enrichment's silent absence) → no slot.
        assertFalse(UpcomingEpisodesSectionModule.isAdmitted(admissionState(series), detailOf(series)))
        // Content + pref on → admitted at the shared slot 6.
        val withStrip = admissionState(series) { copy(upcomingEpisodes = strip) }
        assertTrue(UpcomingEpisodesSectionModule.isAdmitted(withStrip, detailOf(series)))
        assertEquals(6, DetailSectionKind.UPCOMING_EPISODES.delayIndex)
        // Preference off → no slot even with content.
        assertFalse(
            UpcomingEpisodesSectionModule.isAdmitted(
                admissionState(series, preferences = DetailPreferences(showUpcomingStrip = false)) { copy(upcomingEpisodes = strip) },
                detailOf(series),
            ),
        )
        // Admission is content-driven, NOT media-type-driven: a movie with
        // (impossible-by-enrichment) content would admit — the SERIES-only
        // rule lives on the enrichment's gate.
        val movie = item("m1", MediaType.MOVIE)
        assertTrue(
            UpcomingEpisodesSectionModule.isAdmitted(
                admissionState(movie) { copy(upcomingEpisodes = strip) },
                detailOf(movie),
            ),
        )

        // The series trio's emission order (the former fold pin): the
        // merged emission — fold + registry, re-sorted by the enum — puts
        // the smart-play card before the unaired strip before the season
        // tree, all in the shared stagger band 6.
        val loadedSeries = admissionState(series) {
            copy(
                upcomingEpisodes = strip,
                smartPlayTarget = DetailUiState.SmartPlayTarget(
                    episode = item("next1", MediaType.EPISODE, seriesId = "s1"),
                    label = "Next up",
                    startPositionTicks = 0L,
                ),
            )
        }
        val merged = DetailSectionRegistry.admittedSections(
            handFold = DetailSectionAdmission.from(loadedSeries, detailOf(series)).admit(),
            state = loadedSeries,
            detail = detailOf(series),
        )
        val upNext = merged.indexOf(DetailSectionKind.UP_NEXT)
        val upcoming = merged.indexOf(DetailSectionKind.UPCOMING_EPISODES)
        val seasons = merged.indexOf(DetailSectionKind.SEASONS)
        assertTrue(upNext in 0 until upcoming, "UP_NEXT must be emitted before UPCOMING_EPISODES")
        assertTrue(upcoming in 0 until seasons, "UPCOMING_EPISODES must be emitted before SEASONS")
        assertTrue(merged[upNext].delayIndex == 6 && merged[upcoming].delayIndex == 6 && merged[seasons].delayIndex == 6)
    }

    @Test
    fun franchiseRow_admitsOnVisibleRowsAndPreference() {
        val movie = item("m1", MediaType.MOVIE)
        val rows = listOf(
            DetailUiState.FranchiseCollection(id = "box1", name = "Franchise", members = listOf(movie)),
            DetailUiState.FranchiseCollection(id = "box2", name = "Shared universe", members = listOf(movie)),
        )
        // No rows → no slot (the silent-absence contract).
        assertFalse(MoreInCollectionSectionModule.isAdmitted(admissionState(movie), detailOf(movie)))
        // Rows + pref on → admitted at the shared slot 10.
        val withRows = admissionState(movie) { copy(franchiseCollections = rows) }
        assertTrue(MoreInCollectionSectionModule.isAdmitted(withRows, detailOf(movie)))
        assertEquals(10, DetailSectionKind.MORE_IN_COLLECTION.delayIndex)
        // Preference off → no slot even with rows.
        assertFalse(
            MoreInCollectionSectionModule.isAdmitted(
                admissionState(movie, preferences = DetailPreferences(showMoreInCollection = false)) { copy(franchiseCollections = rows) },
                detailOf(movie),
            ),
        )
    }

    @Test
    fun franchiseRow_hiddenRows_excludeFromAdmission() {
        val item = item("i3", MediaType.MOVIE)
        val detail = detailOf(item)
        fun stateWith(hidden: Map<String, Set<String>>) = DetailContentState(
            core = DetailUiState(
                detail = detail,
                origin = DetailOrigin.REMOTE,
                franchiseCollections = listOf(
                    DetailUiState.FranchiseCollection(id = "box1", name = "Franchise", members = listOf(item)),
                    DetailUiState.FranchiseCollection(id = "box2", name = "Shared universe", members = listOf(item)),
                ),
                hiddenFranchiseCollections = hidden,
            ),
            itemId = "i3",
        )

        // No hides → admitted.
        assertTrue(MoreInCollectionSectionModule.isAdmitted(stateWith(emptyMap()), detail))
        // One row hidden → the other keeps the slot admitted.
        assertTrue(MoreInCollectionSectionModule.isAdmitted(stateWith(mapOf("i3" to setOf("box1"))), detail))
        // Every row hidden → the slot drops (no empty gap in the body).
        assertFalse(MoreInCollectionSectionModule.isAdmitted(stateWith(mapOf("i3" to setOf("box1", "box2"))), detail))
        // Hides are keyed per item: another item's hides don't leak in.
        assertTrue(MoreInCollectionSectionModule.isAdmitted(stateWith(mapOf("other" to setOf("box1", "box2"))), detail))
    }
}
