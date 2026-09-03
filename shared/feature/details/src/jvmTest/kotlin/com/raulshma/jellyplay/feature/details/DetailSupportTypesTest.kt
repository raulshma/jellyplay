package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.ResyncResult
import com.raulshma.jellyplay.core.model.ResyncStep
import com.raulshma.jellyplay.core.model.ResyncStepResult
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_edit
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Invariants pinned for the detail feature's small support types that no
 * other suite constructs directly:
 *  - [DetailBackdropTier] mirrors [AdaptiveBackdropHeight] one-for-one — the
 *    backdrop box and the scroll-math controller must agree on the parallax
 *    window (this equality IS the agreement).
 *  - [PersonInfo.toOfflinePersonInfo] maps identity fields verbatim (image
 *    tag → imageTag, blurHash → blurHash) and deliberately leaves
 *    `localImagePath` null — the call site resolves the local portrait.
 *  - [DetailMessage] variants are distinct, carry their payloads, and the
 *    SeriesDownload variant keeps the raw count + nullable error for the
 *    screen-side plural resolution.
 *  - [DetailSession] can be constructed as a bare itemId-only session (the
 *    `loadItemInternal` reset shape).
 *  - [DetailStores] / [RemoteDiscoveryClients] are pure DI aggregation: every
 *    property exposes exactly the constructor instance.
 *  - [DetailStrings]' SAM factory keeps the lambda shape call sites use and
 *    forwards the vararg args verbatim.
 */
class DetailSupportTypesTest {

    // ── DetailBackdropTier ↔ AdaptiveBackdropHeight ─────────────────────────

    @Test
    fun backdropTiers_mirrorAdaptiveHeights() {
        assertEquals(AdaptiveBackdropHeight.Tv, DetailBackdropTier.Tv.dp)
        assertEquals(AdaptiveBackdropHeight.LandscapeExpanded, DetailBackdropTier.LandscapeExpanded.dp)
        assertEquals(AdaptiveBackdropHeight.Expanded, DetailBackdropTier.Expanded.dp)
        assertEquals(AdaptiveBackdropHeight.Portrait, DetailBackdropTier.Portrait.dp)
        assertTrue(
            DetailBackdropTier.entries.size == 4,
            "a new tier must be mirrored in AdaptiveBackdropHeight too",
        )
    }

    // ── PersonInfo.toOfflinePersonInfo ──────────────────────────────────────

    @Test
    fun personMapper_carriesIdentityFields_andLeavesLocalImageNull() {
        val person = PersonInfo(
            id = "person-1",
            name = "Jane Actor",
            role = "The Lead",
            type = "Actor",
            primaryImageTag = "tag-abc",
            primaryBlurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
        )

        val offline = person.toOfflinePersonInfo()

        assertEquals("person-1", offline.id)
        assertEquals("Jane Actor", offline.name)
        assertEquals("The Lead", offline.role)
        assertEquals("Actor", offline.type)
        assertEquals("tag-abc", offline.imageTag, "primaryImageTag maps to imageTag")
        assertEquals("LEHV6nWB2yk8pyo0adR*.7kCMdnj", offline.blurHash)
        assertNull(
            offline.localImagePath,
            "the local portrait path is resolved at the call site, not at this seam",
        )
    }

    @Test
    fun personMapper_roleAndTagsAreNullable() {
        val person = PersonInfo(id = "person-2", name = "Crew", type = "Writer")

        val offline = person.toOfflinePersonInfo()

        assertNull(offline.role)
        assertNull(offline.imageTag)
        assertNull(offline.blurHash)
        assertEquals("Writer", offline.type)
    }

    // ── DetailMessage ───────────────────────────────────────────────────────

    @Test
    fun detailMessages_carryTheirPayloads_andStayDistinct() {
        val text = DetailMessage.Text("saved")
        val download = DetailMessage.SeriesDownload(queuedCount = 12, error = null)
        val downloadError = DetailMessage.SeriesDownload(queuedCount = 0, error = "disk full")
        val watchParty = DetailMessage.WatchPartyStarted("m1")

        assertEquals("saved", text.text)
        assertEquals(12, download.queuedCount)
        assertNull(download.error, "a successful series download carries no error")
        assertEquals("disk full", downloadError.error)
        assertEquals("m1", watchParty.itemId)

        assertNotEquals<DetailMessage.SeriesDownload>(download, downloadError)
        assertNotEquals<Any>(text, watchParty)
    }

    // ── DetailSession ───────────────────────────────────────────────────────

    @Test
    fun detailSession_bareItemIdOnlySession_hasEmptyContent() {
        val session = DetailSession(itemId = "m1")

        assertEquals("m1", session.itemId)
        assertNull(session.seriesId)
        assertNull(session.detail)
        assertTrue(session.seasons.isEmpty())
        assertTrue(session.episodes.isEmpty())
        assertTrue(session.sortedEpisodes.isEmpty())
    }

    // ── DetailStores / RemoteDiscoveryClients DI aggregation ────────────────

    @Test
    fun detailStores_exposeExactlyTheConstructorInstances() {
        val projections = mockk<PreferenceProjections>(relaxed = true)
        val libraryStore = mockk<LibraryStore>(relaxed = true)
        val homeDiscoveryStore = mockk<HomeDiscoveryStore>(relaxed = true)
        val experimentalStore = mockk<ExperimentalStore>(relaxed = true)
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)

        val stores = DetailStores(
            projections = projections,
            libraryStore = libraryStore,
            homeDiscoveryStore = homeDiscoveryStore,
            experimentalStore = experimentalStore,
            engineStore = engineStore,
        )

        assertSame(projections, stores.projections)
        assertSame(libraryStore, stores.libraryStore)
        assertSame(homeDiscoveryStore, stores.homeDiscoveryStore)
        assertSame(experimentalStore, stores.experimentalStore)
        assertSame(engineStore, stores.engineStore)
    }

    @Test
    fun remoteDiscoveryClients_exposeExactlyTheConstructorInstances() {
        val seerrRepository = mockk<SeerrRepository>(relaxed = true)
        val seerrRequestDelegate = mockk<SeerrRequestDelegate>(relaxed = true)
        val arrRepository = mockk<ArrRepository>(relaxed = true)
        val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)

        val clients = RemoteDiscoveryClients(
            seerrRepository = seerrRepository,
            seerrRequestDelegate = seerrRequestDelegate,
            arrRepository = arrRepository,
            offlineModeManager = offlineModeManager,
        )

        assertSame(seerrRepository, clients.seerrRepository)
        assertSame(seerrRequestDelegate, clients.seerrRequestDelegate)
        assertSame(arrRepository, clients.arrRepository)
        assertSame(offlineModeManager, clients.offlineModeManager)
    }

    // ── DetailStrings SAM factory ───────────────────────────────────────────

    @Test
    fun detailStringsSamFactory_forwardsResourceAndArgsVerbatim() = runTest {
        val strings = DetailStrings { res, args -> "res#${res.hashCode()}:${args.joinToString()}" }

        // jvmTest has no top-level resource accessor vals — reach the same
        // resource through the generated Res object.
        val resolved = strings.get(Res.string.detail_option_edit, "arg1", 2, null)

        assertTrue(
            resolved.startsWith("res#"),
            "the fake resolver receives the StringResource and the vararg array",
        )
        // joinToString renders the trailing null as "null" — arity AND order are
        // the contract being pinned.
        assertTrue(resolved.endsWith(":arg1, 2, null"), "args forwarded in order: $resolved")
    }

    @Test
    fun detailMessages_seriesDownloadEquality_keysOnCountAndError() {
        assertEquals(
            DetailMessage.SeriesDownload(3, null),
            DetailMessage.SeriesDownload(3, null),
        )
        assertEquals(
            DetailMessage.WatchPartyStarted("m2"),
            DetailMessage.WatchPartyStarted("m2"),
        )
    }

    // Done wraps the repository's aggregate result verbatim; the screen reads
    // it once and then clears the state back to Idle.
    @Test
    fun resyncUiState_doneCarriesTheRepositoryResult() {
        val result = ResyncResult(
            itemId = "m1",
            steps = listOf(ResyncStepResult(itemId = "m1", step = ResyncStep.FETCH_DETAIL, success = true)),
            mediaFileChanged = false,
        )
        val done = ResyncUiState.Done(result)

        assertSame(result, done.result)
        assertNotEquals<ResyncUiState>(ResyncUiState.Idle, done)
        assertNotEquals<ResyncUiState>(ResyncUiState.Working, done)
    }

    @Test
    fun resyncUiState_errorCarriesTheMessage() {
        val error = ResyncUiState.Error("resync failed")

        assertEquals("resync failed", error.message)
        assertNotEquals<ResyncUiState>(ResyncUiState.Idle, error)
    }
}
