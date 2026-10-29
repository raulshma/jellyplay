package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.network.api.HomeLayoutServerState
import com.raulshma.jellyplay.core.network.api.HomeLayoutApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the home-layout ↔ server DisplayPreferences bridge:
 *
 *  1. the codec — [serializeLayout]/[decodeServerLayout] round-trip (the
 *     presets machinery's exact pair), the LENIENT decode (unknown section /
 *     pinned-type names dropped — the version-union contract) and the clean
 *     abort on a corrupt payload (null, never a partial apply);
 *  2. the manual actions — upload serializes the CURRENT layout with the
 *     payload schema version, fetch resolves to Empty / Undecodable / Offer,
 *     both fail fast signed out;
 *  3. the autopush — trailing debounce (a burst = one push), no push while
 *     the pref is off, no replay of the dirty revision on arming, and a
 *     failed push re-armed by the next layout change (silent, no timers).
 *
 * The store is real over a temp-file DataStore (the homelayout adapter-test
 * idiom); the network client is a mockk. The debounce runs on runTest's
 * virtual clock ([advanceTimeBy]); pushes are observed through a recorded
 * StateFlow awaited while the test coroutine is suspended (the scheduler
 * pumps the background task chain — the upload's identity read hops the
 * DataStore's own dispatcher back onto the test scheduler), bounded by the
 * virtual-clock timeout in [awaitPushes].
 */
class HomeLayoutSyncControllerTest {

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var identityStore: ServerIdentityStore
    private lateinit var store: HomeDiscoveryStore
    private lateinit var apiClient: HomeLayoutApiClient
    private lateinit var controller: HomeLayoutSyncController

    /** Every write-payload the fake client accepted, in order. */
    private val pushedPayloads = MutableStateFlow<List<String>>(emptyList())

    @BeforeTest
    fun setup() {
        kotlinx.coroutines.runBlocking {
            dataStore = PreferenceDataStoreFactory.createWithPath(scope = storeScope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-layout-sync-test").apply { mkdirs() }
                File(dir, "home-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            dataStore.edit { it.clear() }
            identityStore = ServerIdentityStore(dataStore, storeScope)
            store = HomeDiscoveryStore(dataStore, storeScope, identityStore)
            apiClient = mockk()
            controller = HomeLayoutSyncController(
                homeDiscoveryStore = store,
                identityStore = identityStore,
                apiClient = apiClient,
                externalScope = storeScope,
                startAutopushOnConstruction = false,
            )
            // Drain the Eagerly-cached slice so the cleared, signed-out state
            // is observed before each test writes + reads.
            store.homeDiscovery.first()
        }
    }

    @AfterTest
    fun teardown() {
        storeScope.cancel()
    }

    /** Bounded REAL-time wait until the fake client has recorded [count] pushes. */
    private suspend fun awaitPushes(count: Int): List<String> =
        withTimeoutOrNull(5_000) {
            pushedPayloads.first { it.size >= count }
        } ?: error("expected $count pushes, saw ${pushedPayloads.value.size}")

    /** Default-answers the write seam, recording every payload. */
    private fun stubWrites(respond: (String) -> Result<Unit> = { Result.success(Unit) }) {
        coEvery { apiClient.writeHomeLayoutDisplayPreferences(any(), any(), any()) } coAnswers {
            pushedPayloads.value += secondArg<String>()
            respond(secondArg<String>())
        }
    }

    // ------------------------------------------------------------------
    // 1. The codec
    // ------------------------------------------------------------------

    @Test
    fun `serialize and decode round-trip the layout config`() {
        val config = layoutFixture()
        val decoded = decodeServerLayout(serializeLayout(config))
        assertEquals(config, decoded)
    }

    @Test
    fun `decode drops unknown section names, overrides and pinned types`() {
        val raw = """
            {
              "enabledHomeSectionTypes": ["NEXT_UP", "BRAND_NEW_SECTION"],
              "homeSectionOrder": ["RECENTLY_ADDED", "GHOST_SECTION"],
              "libraryHomeSectionOverrides": {"lib1": ["LATEST_MEDIA", "FUTURE_SECTION"]},
              "pinnedHomeSections": [
                {"type": "COLLECTION", "sourceId": "col1", "title": "Movies"},
                {"type": "NEW_KIND", "sourceId": "x", "title": "?"}
              ],
              "continueWatchingClickBehavior": "PLAY"
            }
        """.trimIndent()

        val decoded = assertNotNull(decodeServerLayout(raw))
        // Unknown names dropped — the version-union decode's contract.
        assertEquals(setOf(HomeSectionType.NEXT_UP), decoded.enabledHomeSectionTypes)
        assertEquals(listOf(HomeSectionType.RECENTLY_ADDED), decoded.homeSectionOrder)
        assertEquals(mapOf("lib1" to setOf(HomeSectionType.LATEST_MEDIA)), decoded.libraryHomeSectionOverrides)
        assertEquals(listOf(PinnedHomeSection(PinnedSectionType.COLLECTION, "col1", "Movies")), decoded.pinnedHomeSections)
    }

    @Test
    fun `decode aborts cleanly on corrupt or alien payloads`() {
        // Corrupt JSON, a non-object root, and a wrong-typed section set all
        // decode to null — the caller aborts the restore, the local layout is
        // never partially touched.
        assertNull(decodeServerLayout("not json at all {"))
        assertNull(decodeServerLayout("[]"))
        assertNull(decodeServerLayout("{\"enabledHomeSectionTypes\": 5}"))
        // An unknown enum OUTSIDE the sanitized section positions stays
        // strict (documented limitation) — the whole payload aborts.
        assertNull(
            decodeServerLayout(
                """{"discoverRows": [{"id": "dr1", "title": "Space", "source": "FUTURE_SOURCE"}]}""",
            ),
        )
    }

    // ------------------------------------------------------------------
    // 2. The manual actions
    // ------------------------------------------------------------------

    @Test
    fun `uploadNow serializes the current layout with the payload schema version`() = runTest {
        identityStore.setActiveUser(USER)
        stubWrites()
        store.applyLayout(layoutFixture())

        val result = controller.uploadNow()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            apiClient.writeHomeLayoutDisplayPreferences(USER, any(), HomeLayoutSyncController.LAYOUT_PAYLOAD_SCHEMA_VERSION)
        }
        val pushed = awaitPushes(1).single()
        // The pushed payload IS the store's current slice through the preset
        // codec (compared against the projection — the order setter
        // normalizes, so the payload is the normalized view, not the input).
        assertEquals(store.homeDiscovery.value.toHomeLayoutConfig(), assertNotNull(decodeServerLayout(pushed)))
    }

    @Test
    fun `manual actions fail fast when signed out`() = runTest {
        stubWrites()

        val upload = controller.uploadNow()
        assertTrue(upload.isFailure)
        val fetch = controller.fetchServerLayout()
        assertTrue(fetch.isFailure)
        coVerify(exactly = 0) { apiClient.writeHomeLayoutDisplayPreferences(any(), any(), any()) }
        coVerify(exactly = 0) { apiClient.readHomeLayoutDisplayPreferences(any()) }
    }

    @Test
    fun `fetchServerLayout resolves Empty, Undecodable and Offer`() = runTest {
        identityStore.setActiveUser(USER)

        // Nothing ever uploaded.
        coEvery { apiClient.readHomeLayoutDisplayPreferences(USER) } returns
            Result.success(HomeLayoutServerState(json = null, schemaVersion = null, savedAtEpochMillis = null))
        assertIs<HomeLayoutRestoreFetch.Empty>(controller.fetchServerLayout().getOrThrow())

        // A stored blob that does not decode: aborted cleanly, never applied.
        coEvery { apiClient.readHomeLayoutDisplayPreferences(USER) } returns
            Result.success(HomeLayoutServerState(json = "{corrupt", schemaVersion = 1, savedAtEpochMillis = 100L))
        assertIs<HomeLayoutRestoreFetch.Undecodable>(controller.fetchServerLayout().getOrThrow())

        // A decodable copy: the Offer carries config + the dialog metadata.
        coEvery { apiClient.readHomeLayoutDisplayPreferences(USER) } returns
            Result.success(HomeLayoutServerState(json = serializeLayout(layoutFixture()), schemaVersion = 1, savedAtEpochMillis = 1234L))
        val offer = assertIs<HomeLayoutRestoreFetch.Offer>(controller.fetchServerLayout().getOrThrow())
        assertEquals(layoutFixture(), offer.config)
        assertEquals(1234L, offer.savedAtEpochMillis)
        assertEquals(1, offer.schemaVersion)

        // Transport failure stays a failure.
        coEvery { apiClient.readHomeLayoutDisplayPreferences(USER) } returns
            Result.failure(IllegalStateException("offline"))
        assertTrue(controller.fetchServerLayout().isFailure)
    }

    // ------------------------------------------------------------------
    // 3. The autopush
    // ------------------------------------------------------------------

    @Test
    fun `autopush debounces a burst into one push of the current layout`() = runTest {
        identityStore.setActiveUser(USER)
        store.setLayoutAutopush(true)
        stubWrites()
        backgroundScope.launch { controller.runAutopush() }
        runCurrent()

        // A rapid burst of layout writes — a preset apply fans out like this.
        store.addPinnedHomeSection(PinnedHomeSection(PinnedSectionType.COLLECTION, "col1", "Movies"))
        store.setNextUpMaxDays(30)
        store.addPinnedHomeSection(PinnedHomeSection(PinnedSectionType.PLAYLIST, "pl1", "Chill"))
        runCurrent()

        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        awaitPushes(1)
        advanceTimeBy(DEBOUNCE_MS * 2)
        runCurrent()
        assertEquals(1, pushedPayloads.value.size, "a quiet window must collapse to ONE push")

        // The single push carried the layout as of push time (the last write).
        val config = assertNotNull(decodeServerLayout(pushedPayloads.value.single()))
        assertEquals(30, config.nextUpMaxDays)
        assertEquals(2, config.pinnedHomeSections.size)
    }

    @Test
    fun `autopush never pushes while the pref is off`() = runTest {
        identityStore.setActiveUser(USER)
        // pref OFF (the default) — layout writes happen regardless.
        stubWrites()
        backgroundScope.launch { controller.runAutopush() }
        runCurrent()

        store.addPinnedHomeSection(PinnedHomeSection(PinnedSectionType.COLLECTION, "col1", "Movies"))
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS * 3)
        runCurrent()

        assertEquals(0, pushedPayloads.value.size, "MANUAL policy: no push while off")
        coVerify(exactly = 0) { apiClient.writeHomeLayoutDisplayPreferences(any(), any(), any()) }
    }

    @Test
    fun `autopush does not replay a dirty revision on arming`() = runTest {
        identityStore.setActiveUser(USER)
        stubWrites()
        backgroundScope.launch { controller.runAutopush() }
        runCurrent()

        // The layout changed while the pref was off.
        store.addPinnedHomeSection(PinnedHomeSection(PinnedSectionType.COLLECTION, "col1", "Movies"))
        runCurrent()
        // NOW the user arms the autopush.
        store.setLayoutAutopush(true)
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS * 2)
        runCurrent()
        assertEquals(0, pushedPayloads.value.size, "arming must not push the stale state")

        // The NEXT layout change is what pushes.
        store.setNextUpMaxDays(7)
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(1, awaitPushes(1).size)
    }

    @Test
    fun `autopush failure is silent and the next change re-arms the push`() = runTest {
        identityStore.setActiveUser(USER)
        store.setLayoutAutopush(true)
        // First push fails, later ones succeed.
        var calls = 0
        stubWrites { calls++; if (calls == 1) Result.failure(IllegalStateException("5xx")) else Result.success(Unit) }
        backgroundScope.launch { controller.runAutopush() }
        runCurrent()

        store.setNextUpMaxDays(30)
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        awaitPushes(1) // the failed attempt

        // No retry timer — the retry happens on the NEXT layout change only.
        advanceTimeBy(DEBOUNCE_MS * 3)
        runCurrent()
        assertEquals(1, pushedPayloads.value.size, "a failed push must not retry on its own")

        store.setNextUpRewatching(true)
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        awaitPushes(2) // re-armed by the change; carries the current layout
        val second = assertNotNull(decodeServerLayout(pushedPayloads.value[1]))
        assertEquals(true, second.nextUpRewatching)
    }

    @Test
    fun `a user switch while both users are armed cancels the outgoing push and re-arms`() = runTest {
        stubWrites()
        backgroundScope.launch { controller.runAutopush() }
        runCurrent()

        // Both users armed (the autopush pref is per-user namespaced): arm B,
        // return to A and arm A too.
        identityStore.setActiveUser(USER_B)
        store.setLayoutAutopush(true)
        identityStore.setActiveUser(USER)
        store.setLayoutAutopush(true)
        runCurrent()

        store.setNextUpMaxDays(30)
        runCurrent()
        // Half-way through the debounce window the active user switches back
        // to B — who is armed just the same.
        advanceTimeBy(DEBOUNCE_MS / 2)
        identityStore.setActiveUser(USER_B)
        // The identity write rides DataStore IO — wait for the switch to
        // PROPAGATE (real time) before the virtual-time choreography, or the
        // assertions below race the still-live outgoing flow.
        withTimeoutOrNull(5_000) { identityStore.activeUserId.first { it == USER_B } }
        runCurrent()
        val leaked = withTimeoutOrNull(1_000) { pushedPayloads.first { it.isNotEmpty() } }
        assertNull(leaked, "the outgoing user's pending push must not fire under the new identity")

        // The restart re-applied drop(1): the revision that was CURRENT at the
        // switch (A's pending "30") is the swallowed baseline; B's own first
        // layout change pushes — under the NEW identity.
        store.setNextUpMaxDays(7)
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        awaitPushes(1)
        val pushed = assertNotNull(decodeServerLayout(pushedPayloads.value.single()))
        assertEquals(7, pushed.nextUpMaxDays)
        coVerify(atLeast = 1) { apiClient.writeHomeLayoutDisplayPreferences(USER_B, any(), any()) }
    }

    private companion object {
        const val USER = "sync-user"
        const val USER_B = "sync-user-b"
        const val DEBOUNCE_MS = HomeLayoutSyncController.AUTO_PUSH_DEBOUNCE_MILLIS
    }
}

/** A distinctive layout payload fixture — every [HomeLayoutConfig] domain set. */
private fun layoutFixture(): HomeLayoutConfig = HomeLayoutConfig(
    enabledHomeSectionTypes = setOf(HomeSectionType.NEXT_UP, HomeSectionType.RECENTLY_ADDED),
    homeSectionOrder = listOf(HomeSectionType.RECENTLY_ADDED, HomeSectionType.NEXT_UP),
    libraryHomeSectionOverrides = mapOf("lib1" to setOf(HomeSectionType.LATEST_MEDIA)),
    mergeContinueWatchingAndNextUp = true,
    nextUpMaxDays = 14,
    nextUpRewatching = true,
    pinnedHomeSections = listOf(PinnedHomeSection(PinnedSectionType.COLLECTION, "col1", "Movies")),
    discoverRows = listOf(DiscoverRowConfig(id = "dr1", title = "Space")),
    homeHeroEnabled = false,
    continueWatchingClickBehavior = ContinueWatchingClickBehavior.PLAY,
)
