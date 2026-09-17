package com.raulshma.jellyplay.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSeasonDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceServer
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.feature.settings.ConnectionProbe
import com.raulshma.jellyplay.feature.settings.ConnectionProbe.FallbackText
import com.raulshma.jellyplay.feature.settings.ConnectionProbe.Failure
import com.raulshma.jellyplay.feature.settings.ConnectionProbe.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

/**
 * Browser-free unit cover for the web Seerr controller's DECISION logic now
 * that the hand-rolled probe state machine is gone: [WebSeerrController]'s
 * status core IS the shared `ConnectionProbe` board (single `Unit` key,
 * details = the server version), so what is pinned HERE is the web slice of
 * that contract —
 *
 *  - REFUSED PRE-FLIGHT: the blank-credential guards are the machine's
 *    `refused` lambda in the old guard order (URL before key), mapping to
 *    the localized ServerUrlRequired / ApiKeyRequired fallbacks — and a
 *    refusal writes NOTHING (no persist, no repository call).
 *  - PINNED CALL ORDER: persist-then-test, event by event — setServerUrl →
 *    setAuthMethod(API_KEY) → setEnabled(true) → setApiKey →
 *    testApiKeyConnection. The order lives only in the probe action; this
 *    test is the pin that keeps it byte-identical to the former hand-rolled
 *    persist (and the API-key slice of SeerrSettingsViewModel).
 *  - STATUS MAPPING: success → Connected(version); a server-reported failure
 *    message → Error(Reported) verbatim; a null message → the localized
 *    ConnectionFailed fallback; a thrown non-cancellation crash → the
 *    localized UnexpectedError fallback (the machine's declared policy —
 *    the alignment SeerrSettingsViewModel accepted in the same migration).
 *  - THE DECLARED SINGLE-FLIGHT ARM: the probe is constructed
 *    CALLER_GATED (the pane disables its controls while Testing — the
 *    caller owns concurrency), and the test pins the machine's safety net
 *    under that arm: a probe that lost the registration NEVER lands its
 *    outcome.
 *
 * The tests wrap their bodies in [runTest] (kotlin.test rejects `suspend`
 * test functions on wasmJs). The controller's probe board runs on its own
 * page-lifetime scope (WebSideEffectScope → Dispatchers.Default), so outcomes
 * are awaited through the real-time polling helper, never by blocking — the
 * WebConnectControllerTest idiom. No browser, no fetch: a scriptable
 * [SeerrRepository] fake plus recording fakes under the real
 * [SeerrPreferencesStore] / [SeerrSecureCredentialsStore] wrappers (the
 * controller consumes the concrete classes, so the write-order evidence is
 * observed one layer down: at the DataStore `updateData` key-diff and the
 * SecureKeyValueStorage `putString`).
 */
class WebSeerrControllerTest {

    // ── the shared event log + fakes ───────────────────────────────────────

    /**
     * One ordered log across all three fakes — the persist-then-test order
     * pin reads exactly this list.
     */
    private class EventLog {
        val events = mutableListOf<String>()
    }

    /**
     * The repository fake: only [testApiKeyConnection] is on the tested path;
     * everything else throws. [script] swaps the outcome/behavior per test
     * (success, reported failure, null-message failure, crash, gate-parked).
     */
    private class FakeSeerrRepository(private val log: EventLog) : SeerrRepository {

        var testCalls = 0
            private set

        var script: suspend () -> Result<SeerrStatusResponse> = {
            Result.success(SeerrStatusResponse(version = "1.2.3"))
        }

        override suspend fun testApiKeyConnection(): Result<SeerrStatusResponse> {
            testCalls += 1
            log.events += "testApiKeyConnection"
            return script()
        }

        private fun unused(): Nothing = throw UnsupportedOperationException("unused in WebSeerrControllerTest")

        override suspend fun testConnection(): Result<SeerrStatusResponse> = unused()
        override suspend fun loginJellyfin(username: String, password: String): Result<SeerrStatusResponse> = unused()
        override suspend fun loginLocal(email: String, password: String): Result<SeerrStatusResponse> = unused()
        override suspend fun search(query: String, page: Int): Result<SeerrSearchResponse> = unused()
        override suspend fun getMovieDetails(tmdbId: Int): Result<SeerrMovieDetails> = unused()
        override suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails> = unused()
        override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): Result<SeerrSeasonDetail> = unused()
        override suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> = unused()
        override suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> = unused()
        override suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> = unused()
        override suspend fun getTmdbVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>> = unused()
        override suspend fun getTmdbReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>> = unused()
        override suspend fun requestMedia(
            tmdbId: Int,
            mediaType: String,
            seasons: List<Int>?,
            serverId: Int?,
            profileId: Int?,
            rootFolder: String?,
            tags: List<Int>?,
        ): Result<SeerrMediaRequest> = unused()
        override suspend fun getRadarrSettings(): Result<List<SeerrRadarrSettings>> = unused()
        override suspend fun getSonarrSettings(): Result<List<SeerrSonarrSettings>> = unused()
        override suspend fun getRadarrServiceDetail(id: Int): Result<SeerrRadarrServiceDetail> = unused()
        override suspend fun getSonarrServiceDetail(id: Int): Result<SeerrSonarrServiceDetail> = unused()
        override suspend fun getServiceRadarrServers(): Result<List<SeerrServiceServer>> = unused()
        override suspend fun getServiceSonarrServers(): Result<List<SeerrServiceServer>> = unused()
        override suspend fun getServiceRadarrDetail(id: Int): Result<SeerrRadarrServiceDetail> = unused()
        override suspend fun getServiceSonarrDetail(id: Int): Result<SeerrSonarrServiceDetail> = unused()
        override fun isConnected(): Flow<Boolean> = MutableStateFlow(false)
        override fun isEnabled(): Flow<Boolean> = MutableStateFlow(false)
        override fun isSearchEnabled(): Flow<Boolean> = MutableStateFlow(false)
        override fun isRecommendationsEnabled(): Flow<Boolean> = MutableStateFlow(false)
        override fun isDiscoverEnabled(): Flow<Boolean> = MutableStateFlow(false)
        override fun getPreferences(): Flow<SeerrPreferences> = MutableStateFlow(SeerrPreferences())
        override suspend fun getTrending(page: Int): Result<SeerrSearchResponse> = unused()
        override suspend fun getDiscoverMovies(page: Int, primaryReleaseDateGte: String?): Result<SeerrSearchResponse> = unused()
        override suspend fun getDiscoverTv(page: Int, firstAirDateGte: String?): Result<SeerrSearchResponse> = unused()
        override suspend fun getRequests(
            take: Int,
            skip: Int,
            filter: String,
            sort: String,
            sortDirection: String,
            requestedBy: Int?,
            mediaType: String?,
            search: String?,
        ): Result<SeerrRequestListResponse> = unused()
        override suspend fun getRequest(id: Int): Result<SeerrRequestItem> = unused()
        override suspend fun approveRequest(id: Int): Result<SeerrRequestItem> = unused()
        override suspend fun declineRequest(id: Int): Result<SeerrRequestItem> = unused()
        override suspend fun retryRequest(id: Int): Result<SeerrRequestItem> = unused()
        override suspend fun deleteRequest(id: Int): Result<Unit> = unused()
        override suspend fun deleteMedia(mediaId: Int, is4k: Boolean): Result<Unit> = unused()
        override suspend fun editRequest(
            id: Int,
            mediaType: String,
            mediaId: Int,
            serverId: Int?,
            profileId: Int?,
            rootFolder: String?,
            tags: List<Int>?,
            seasons: List<Int>?,
        ): Result<SeerrRequestItem> = unused()
        override suspend fun getRequestCount(): Result<SeerrRequestCount> = unused()
        override suspend fun getCurrentUser(): Result<SeerrCurrentUser> = unused()
        override fun isAdmin(): Flow<Boolean> = MutableStateFlow(false)
        override val currentUser: StateFlow<SeerrCurrentUser?> = MutableStateFlow(null)
        override val pendingRequestCount: StateFlow<Int> = MutableStateFlow(0)
        override fun startPolling() = unused()
        override fun stopPolling() = unused()
    }

    /**
     * In-memory secure storage recording the API-key write — the store's
     * inner key is the literal "api_key" (the localStorage prefix is a
     * platform-layer concern, not present here).
     */
    private class RecordingSecureStorage(private val log: EventLog) : SecureKeyValueStorage {
        private val map = mutableMapOf<String, String>()

        override fun getString(key: String, defValue: String?): String? = map[key] ?: defValue

        override fun putString(key: String, value: String?) {
            if (value == null) {
                map.remove(key)
            } else {
                map[key] = value
            }
            if (key == "api_key") log.events += "setApiKey"
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    /**
     * In-memory prefs DataStore recording WHICH key a write touched, by
     * diffing before/after — the honest observation point for the persist
     * order, since [SeerrPreferencesStore] is a concrete class (each store
     * method performs exactly one single-key edit, so one event per call).
     * Keys mirror the store's private Keys object.
     */
    private class RecordingDataStore(private val log: EventLog) : DataStore<Preferences> {
        private val backing = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> get() = backing

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val previous = backing.value
            val next = transform(previous)
            if (next[SERVER_URL] != previous[SERVER_URL]) log.events += "setServerUrl"
            if (next[AUTH_METHOD] != previous[AUTH_METHOD]) log.events += "setAuthMethod"
            if (next[ENABLED] != previous[ENABLED]) log.events += "setEnabled"
            backing.value = next
            return next
        }

        private companion object {
            val SERVER_URL = stringPreferencesKey("seerr_server_url")
            val AUTH_METHOD = stringPreferencesKey("seerr_auth_method")
            val ENABLED = booleanPreferencesKey("seerr_enabled")
        }
    }

    // ── the environment ────────────────────────────────────────────────────

    private class Env(
        val controller: WebSeerrController,
        val repo: FakeSeerrRepository,
        val log: EventLog,
    )

    /**
     * Fresh controller per test over the real store wrappers; the side-effect
     * scope is instance-owned, so tests never share jobs — outcomes are
     * awaited through the polling helper.
     */
    private fun env(configure: FakeSeerrRepository.() -> Unit = {}): Env {
        val log = EventLog()
        val repo = FakeSeerrRepository(log)
        val storage = RecordingSecureStorage(log)
        val prefs = RecordingDataStore(log)
        val secure = SeerrSecureCredentialsStore(storage)
        val store = SeerrPreferencesStore(
            dataStore = prefs,
            secureCredentialsStore = secure,
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        repo.configure()
        return Env(WebSeerrController(seerrPreferencesStore = store, secureCredentialsStore = secure, seerrRepository = repo), repo, log)
    }

    /** Polls [condition] on the event loop until true or [timeoutMs] elapses. */
    private suspend fun awaitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (!condition()) {
            if (deadline.hasPassedNow()) throw AssertionError("condition not met within ${timeoutMs}ms")
            delay(25)
        }
    }

    /**
     * Soaks [timeoutMs] of REAL time (the runTest scheduler's delays are
     * virtual) while yielding, so page-lifetime Dispatchers.Default jobs make
     * progress — for asserting that something never lands.
     */
    private suspend fun soakRealTime(timeoutMs: Long = 400) {
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (!deadline.hasPassedNow()) {
            delay(25)
        }
    }

    private val url = "http://localhost:5055"
    private val key = "e2e-seerr-api-key"

    // ── refused pre-flight: blank credentials, nothing written ─────────────

    @Test
    fun `blank server URL refuses synchronously and writes nothing`() = runTest {
        val env = env()
        env.controller.testConnection("", key)
        awaitUntil {
            env.controller.connectionStatus.value ==
                Status.Error(Failure.Declared(FallbackText.ServerUrlRequired))
        }
        soakRealTime()
        assertEquals(0, env.repo.testCalls, "a refused probe must not reach the repository")
        assertTrue(env.log.events.isEmpty(), "a refused probe must not persist (events: ${env.log.events})")
    }

    @Test
    fun `blank API key refuses after the server URL guard and writes nothing`() = runTest {
        val env = env()
        env.controller.testConnection(url, "")
        awaitUntil {
            env.controller.connectionStatus.value ==
                Status.Error(Failure.Declared(FallbackText.ApiKeyRequired))
        }
        soakRealTime()
        assertEquals(0, env.repo.testCalls)
        assertTrue(env.log.events.isEmpty(), "a refused probe must not persist (events: ${env.log.events})")
    }

    // ── the pinned persist-then-test order ─────────────────────────────────

    @Test
    fun `persist-then-test order is pinned event by event and success lands Connected`() = runTest {
        val env = env()
        env.controller.testConnection(url, key)
        awaitUntil {
            env.controller.connectionStatus.value == Status.Connected(WebSeerrController.ConnectionDetails("1.2.3"))
        }
        assertEquals(
            listOf("setServerUrl", "setAuthMethod", "setEnabled", "setApiKey", "testApiKeyConnection"),
            env.log.events,
            "persist FIRST (byte-identical order), THEN the repository call — the contract",
        )
        assertEquals(1, env.repo.testCalls)
    }

    // ── the status mapping ─────────────────────────────────────────────────

    @Test
    fun `a server-reported failure message lands Reported verbatim`() = runTest {
        val env = env {
            script = { Result.failure(IllegalStateException("boom")) }
        }
        env.controller.testConnection(url, key)
        awaitUntil {
            env.controller.connectionStatus.value == Status.Error(Failure.Reported("boom"))
        }
        assertEquals(
            listOf("setServerUrl", "setAuthMethod", "setEnabled", "setApiKey", "testApiKeyConnection"),
            env.log.events,
            "a failing test still persisted first — the pinned order is unconditional",
        )
    }

    @Test
    fun `a null failure message degrades to the localized ConnectionFailed fallback`() = runTest {
        val env = env {
            script = { Result.failure(IllegalStateException()) } // null message
        }
        env.controller.testConnection(url, key)
        awaitUntil {
            env.controller.connectionStatus.value ==
                Status.Error(Failure.Declared(FallbackText.ConnectionFailed))
        }
    }

    @Test
    fun `an unexpected crash degrades to the localized UnexpectedError fallback`() = runTest {
        val env = env {
            script = { throw RuntimeException("bug") }
        }
        env.controller.testConnection(url, key)
        awaitUntil {
            env.controller.connectionStatus.value ==
                Status.Error(Failure.Declared(FallbackText.UnexpectedError))
        }
    }

    // ── the declared single-flight arm ─────────────────────────────────────

    @Test
    fun `under CALLER_GATED a probe that lost the registration never lands`() = runTest {
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        val env = env {
            var attempt = 0
            script = {
                val n = attempt++
                (if (n == 0) gate1 else gate2).await()
                Result.success(SeerrStatusResponse(version = if (n == 0) "first" else "second"))
            }
        }
        env.controller.testConnection(url, key)
        awaitUntil { env.controller.connectionStatus.value is Status.Testing }

        // The pane's controls are disabled while Testing, so a second probe
        // can only be a caller bug — under the declared CALLER_GATED arm the
        // machine neither cancels nor restarts, and the registration (and the
        // landing right) goes to the newer probe.
        env.controller.testConnection(url, key)
        gate2.complete(Unit)
        awaitUntil {
            env.controller.connectionStatus.value == Status.Connected(WebSeerrController.ConnectionDetails("second"))
        }

        // Real-time soak: the superseded probe's gate opens afterwards — its
        // stale "first" must never land over the newer verdict.
        gate1.complete(Unit)
        soakRealTime()
        assertEquals(
            Status.Connected(WebSeerrController.ConnectionDetails("second")),
            env.controller.connectionStatus.value,
            "the identity guard must hold under CALLER_GATED too",
        )
    }

    // ── the pane's line-clearing path ──────────────────────────────────────

    @Test
    fun `resetProbeStatus drops the standing verdict back to Idle`() = runTest {
        val env = env()
        env.controller.testConnection(url, key)
        awaitUntil {
            env.controller.connectionStatus.value is Status.Connected
        }
        env.controller.resetProbeStatus()
        awaitUntil { env.controller.connectionStatus.value == Status.Idle }
    }
}
