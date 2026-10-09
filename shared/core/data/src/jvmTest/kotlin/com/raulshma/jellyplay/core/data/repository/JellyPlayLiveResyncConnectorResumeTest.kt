package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The settings stream's resume cursor (the Phase 1 `Last-Event-ID` wave),
 * driven through [JellyPlayLiveResyncConnector] on the test scheduler's
 * virtual time: the first connect resumes from the persisted per-user id, the
 * id seen on the wire is persisted and becomes the reconnect's `Last-Event-ID`
 * — so the server replays exactly the gap the drop swallowed. Rides the REAL
 * [ProfileSyncRepository] (enabled; its follow-up cycles fold against the api
 * fake's empty settings) and REAL [JellyPlayPluginStatusStore], the
 * [ProfileSyncRepositoryTest] harness shape.
 */
class JellyPlayLiveResyncConnectorResumeTest {

    // ------------------------------------------------------------------
    // fakes (the ProfileSyncRepositoryTest harness shape, trimmed)
    // ------------------------------------------------------------------

    private class FakeSessionIdentity : com.raulshma.jellyplay.core.data.session.SessionIdentityProvider {
        override val transitions: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.session.HomeSessionTransition> =
            MutableSharedFlow()
        override suspend fun currentIdentity(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override fun currentIdentitySnapshot(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override suspend fun cacheIdentity(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
        override fun cacheIdentitySnapshot(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
    }

    /** Api fake: the probe says AVAILABLE + `settings-sync`; streams are scripted. */
    private class FakePluginApi : JellyPlayCapabilitiesRoutes, JellyPlaySettingsSyncRoutes {
        /** Every settings-stream connect: the resume id it asked to resume from. */
        val connectResumeIds = mutableListOf<Long>()

        /** Flows served per [settingsStream] call; the last one repeats forever. */
        val streams = ArrayDeque<Flow<JellyPlaySseEvent>>()

        override suspend fun getCapabilities(): Result<com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities> =
            Result.success(
                com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities(
                    1, "1.0.0", listOf("settings-sync"), 0, listOf(""),
                ),
            )

        override suspend fun getSettings(profile: String?): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot> =
            Result.success(emptySnapshot())

        override suspend fun getChangedSettings(
            since: Long,
            profile: String?,
            limit: Int?,
            cursor: Long?,
        ): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot> = Result.success(emptySnapshot())

        override suspend fun applySettings(
            profile: String?,
            deviceId: String?,
            writes: List<com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite>,
        ): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult> =
            Result.success(com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult())

        override suspend fun resetNamespace(ns: String, profile: String?): Result<Unit> = Result.success(Unit)

        override suspend fun resolveProfile(profile: String?): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot> =
            Result.success(emptySnapshot())

        override fun settingsStream(resumeFromEventId: Long): Flow<JellyPlaySseEvent> {
            connectResumeIds += resumeFromEventId
            return streams.removeFirstOrNull() ?: flow { awaitCancellation() }
        }

        private fun emptySnapshot() = com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot()

        // ── sync-ledger / restore-point / export-import routes (settings-sync
        // role members this suite never exercises) ──
        override suspend fun getSyncStatus() = error("unused")
        override suspend fun getSyncHistory(since: Long?, limit: Int) = error("unused")
        override suspend fun getSyncHistoryKeys(seq: Long, limit: Int) = error("unused")
        override suspend fun adminSyncOverview() = error("unused")
        override suspend fun getSnapshots() = error("unused")
        override suspend fun createSnapshot() = error("unused")
        override suspend fun restoreSnapshot(id: String) = error("unused")
        override suspend fun exportSettings() = error("unused")
        override suspend fun importSettings(bundleJson: String, deviceId: String?) = error("unused")

    }

    /** Auth fake: only the authenticated edge the connector arms on. */
    private class FakeAuthRepository : com.raulshma.jellyplay.core.data.repository.AuthRepository {
        override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(true)
        override val servers = emptyFlow<List<com.raulshma.jellyplay.core.model.ServerInfo>>()
        override val currentServer = emptyFlow<com.raulshma.jellyplay.core.model.ServerInfo?>()
        override val currentUser = emptyFlow<com.raulshma.jellyplay.core.model.UserInfo?>()
        override val currentServerUsers = kotlinx.coroutines.flow.MutableStateFlow(
            emptyList<com.raulshma.jellyplay.core.model.UserInfo>(),
        )
        override suspend fun addServer(address: String) = error("unused")
        override suspend fun probeServer(address: String) = error("unused")
        override suspend fun removeServer(serverId: String) = Unit
        override suspend fun switchServer(serverId: String) = error("unused")
        override suspend fun addServerAddress(serverId: String, address: String) = error("unused")
        override suspend fun removeServerAddress(serverId: String, address: String) = error("unused")
        override suspend fun switchServerAddress(serverId: String, address: String) = error("unused")
        override suspend fun login(serverAddress: String, username: String, password: String) = error("unused")
        override suspend fun isQuickConnectEnabled() = error("unused")
        override suspend fun initiateQuickConnect() = error("unused")
        override suspend fun pollQuickConnect(secret: String) = error("unused")
        override suspend fun loginWithQuickConnect(serverAddress: String, secret: String) = error("unused")
        override suspend fun authorizeQuickConnect(code: String) = error("unused")
        override suspend fun restoreSession() = error("unused")
        override suspend fun refreshCurrentUser() = error("unused")
        override suspend fun logout() = Unit
        override suspend fun revokeServerSession() = Unit
        override suspend fun switchUser(userId: String) = error("unused")
        override suspend fun removeUser(userId: String) = Unit
        override suspend fun getUsersForServer(serverId: String): List<com.raulshma.jellyplay.core.model.UserInfo> = emptyList()
        override suspend fun postCapabilities() = error("unused")
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private lateinit var api: FakePluginApi

    /**
     * Builds the harness and starts the connector. The scripted streams MUST
     * be queued into [api] BEFORE this runs — the first connect consumes the
     * queue head (the empty queue's fallback hang flow would park the loop).
     */
    private fun TestScope.buildConnector(
        api: FakePluginApi,
        loadLastEventId: suspend () -> Long?,
        saveLastEventId: suspend (Long) -> Unit,
    ): ProfileSyncRepository {
        this@JellyPlayLiveResyncConnectorResumeTest.api = api
        val registry = SessionCacheRegistry(FakeSessionIdentity(), CoroutineScope(Dispatchers.Default))
        val statusStore = JellyPlayPluginStatusStore(api, registry)
        val repo = ProfileSyncRepository(
            apiClient = api,
            statusStore = statusStore,
            sessionCacheRegistry = registry,
            adapters = emptyList(),
            deviceProfile = "desktop",
            deviceIdProvider = { "test-device" },
            nowMillis = { 1_000L },
        )
        repo.setEnabled(true)
        JellyPlayLiveResyncConnector(
            apiClient = api,
            syncRepository = repo,
            statusStore = statusStore,
            authRepository = FakeAuthRepository(),
            // Virtual time: the edge collector, the debounce and the
            // reconnect backoff all ride the test scheduler.
            scope = backgroundScope,
            loadLastEventId = loadLastEventId,
            saveLastEventId = saveLastEventId,
        ).start()
        // One scheduler turn up front: the edge collector's launch must have
        // run (and served its scripted stream) before the virtual-time
        // advance below.
        testScheduler.runCurrent()
        return repo
    }

    @Test
    fun reconnect_resumesFromLastSeenEventId_persistingItAlongTheWay() = runTest {
        val persistedIds = mutableListOf<Long>()
        // Stream 1: one frame (id 9) on top of the persisted cursor 7, held
        // past the burst-collapse debounce, then a drop. Stream 2 (the
        // reconnect): hangs — the loop parks there.
        val api = FakePluginApi().apply {
            streams += flow {
                emit(JellyPlaySseEvent(9, "settings.changed", "{}"))
                delay(2_000)
                throw RuntimeException("stream dropped")
            }
        }
        buildConnector(
            api = api,
            loadLastEventId = { 7L },
            saveLastEventId = { value -> persistedIds += value },
        )
        // Scheduler discipline (measured, not assumed): runCurrent turns the
        // initial dispatches; advanceTimeBy drives the debounce window, the
        // scripted stream's delay, the drop and the reconnect backoff.
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()

        // First connect resumed from the PERSISTED id; the reconnect from the
        // id seen on the wire — the gap the drop swallowed gets replayed.
        assertEquals(listOf(7L, 9L), api.connectResumeIds)
        // The seen id was persisted along the way.
        assertEquals(listOf(9L), persistedIds)
    }

    @Test
    fun firstConnect_withoutPersistedId_noResumeHeader() = runTest {
        val api = FakePluginApi().apply {
            streams += flow { awaitCancellation() }
        }
        buildConnector(
            api = api,
            loadLastEventId = { null },
            saveLastEventId = { },
        )
        testScheduler.runCurrent()

        // Nothing persisted → the legacy connect (0 = no Last-Event-ID).
        assertEquals(listOf(0L), api.connectResumeIds)
    }
}
