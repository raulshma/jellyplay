package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayAppliedSetting
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayDevice
import com.raulshma.jellyplay.core.network.api.JellyPlayMessage
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayRejectedSetting
import com.raulshma.jellyplay.core.network.api.JellyPlaySeerrStatus
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Engine tests for the profile-sync cycle: opt-in gating, adopt-clean/push-
 * dirty split, LWW-reject retry, convergence — against fakes, no I/O.
 */
class ProfileSyncRepositoryTest {

    // ------------------------------------------------------------------
    // fakes
    // ------------------------------------------------------------------

    private class FakeSessionIdentity : com.raulshma.jellyplay.core.data.session.SessionIdentityProvider {
        // Replay-0: no spurious SignedOut replay racing the test body's setup.
        override val transitions: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.session.HomeSessionTransition> =
            MutableSharedFlow()
        override suspend fun currentIdentity(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override fun currentIdentitySnapshot(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override suspend fun cacheIdentity(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
        override fun cacheIdentitySnapshot(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
    }

    /** Mirror-tracking adapter mirroring the real adapter's dirty semantics, in memory. */
    private class FakeAdapter(
        override val namespace: String,
        initial: Map<String, JsonElement>,
    ) : ProfileSyncAdapter {
        val local = initial.toMutableMap()
        private val mirror = mutableMapOf<String, JsonElement>()
        val appliedRemote = mutableListOf<Map<String, JsonElement>>()
        val synced = mutableListOf<Map<String, JsonElement>>()

        override suspend fun snapshot(): Map<String, JsonElement> = local.toMap()

        override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
            current.filter { (key, value) -> mirror[key] != value }

        override suspend fun applyRemote(entries: Map<String, JsonElement>) {
            appliedRemote += entries
            local.putAll(entries)
        }

        override suspend fun markSynced(values: Map<String, JsonElement>) {
            synced += values
            mirror.putAll(values)
        }
    }

    /** Api fake: capabilities AVAILABLE (unless [capabilitiesFail]); server state in [remote]. */
    private class FakePluginApi(
        val remote: MutableMap<String, JsonElement> = mutableMapOf(),
        var rejectKeys: Set<String> = emptySet(),
        var capabilitiesFail: Boolean = false,
        /** The resolved-profile payload's admin-defaults modes ("ns/key" → mode). */
        var modes: Map<String, String>? = null,
    ) : JellyPlayPluginApiClient {
        val pushedWrites = mutableListOf<JellyPlaySettingWrite>()
        private var seq = 0L

        override suspend fun getCapabilities(): Result<JellyPlayCapabilities> =
            if (capabilitiesFail) {
                Result.failure(IllegalStateException("404"))
            } else {
                Result.success(JellyPlayCapabilities(1, "1.0.0", listOf("settings-sync"), 0, listOf("")))
            }

        private fun entryOf(compositeKey: String, value: JsonElement): com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry {
            val (ns, key) = compositeKey.split('/').let { it[0] to it.getOrElse(1) { "" } }
            return com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry(ns, key, 1, 0, "srv", "", value)
        }

        private fun snapshotOf(): JellyPlaySettingsSnapshot =
            JellyPlaySettingsSnapshot(
                head = seq,
                profile = "",
                settings = remote.map { (key, value) -> entryOf(key, value) },
                modes = modes,
            )

        override suspend fun getSettings(profile: String?): Result<JellyPlaySettingsSnapshot> = Result.success(snapshotOf())

        override suspend fun getChangedSettings(since: Long, profile: String?): Result<JellyPlaySettingsSnapshot> =
            Result.success(JellyPlaySettingsSnapshot(head = seq, profile = "", settings = emptyList()))

        override suspend fun applySettings(
            profile: String?,
            deviceId: String?,
            writes: List<JellyPlaySettingWrite>,
        ): Result<JellyPlaySettingsBatchResult> {
            pushedWrites += writes
            seq += 1
            val applied = writes.filter { "${it.ns}/${it.key}" !in rejectKeys }
            applied.forEach { write -> remote["${write.ns}/${write.key}"] = write.value }
            return Result.success(
                JellyPlaySettingsBatchResult(
                    head = seq,
                    applied = applied.map { JellyPlayAppliedSetting(it.ns, it.key, it.updatedAt, seq) },
                    rejected = writes.filter { it !in applied }.map { JellyPlayRejectedSetting(it.ns, it.key, "stale-write") },
                ),
            )
        }

        override suspend fun resetNamespace(ns: String, profile: String?): Result<Unit> = Result.success(Unit)
        override suspend fun resolveProfile(profile: String?): Result<JellyPlaySettingsSnapshot> = Result.success(snapshotOf())
        override fun settingsStream(): Flow<com.raulshma.jellyplay.core.network.api.JellyPlaySseEvent> = emptyFlow()
        override suspend fun registerDevice(
            deviceId: String,
            name: String,
            platform: String,
            appVersion: String,
            push: com.raulshma.jellyplay.core.network.api.JellyPlayDevicePush,
        ): Result<Unit> = Result.success(Unit)
        override suspend fun unregisterDevice(deviceId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getDevices(): Result<List<JellyPlayDevice>> = Result.success(emptyList())
        override fun eventsStream(): Flow<com.raulshma.jellyplay.core.network.api.JellyPlaySseEvent> = emptyFlow()
        override suspend fun broadcast(title: String, body: String, url: String?): Result<Unit> = Result.success(Unit)
        override suspend fun getMessages(): Result<List<JellyPlayMessage>> = Result.success(emptyList())
        override suspend fun markMessageRead(messageId: String): Result<Unit> = Result.success(Unit)
        override suspend fun seerrStatus(): Result<JellyPlaySeerrStatus> = Result.failure(IllegalStateException("unused"))
        override suspend fun seerrLogin(authType: String, username: String?, password: String?, quickConnectSecret: String?): Result<Unit> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun seerrLogout(): Result<Unit> = Result.failure(IllegalStateException("unused"))

        // ── per-feature endpoints added after this fake was written; unused by the sync-engine tests ──
        override suspend fun getSyncStatus(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncHistory(since: Long?, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistory?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncHistoryKeys(seq: Long, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKeys?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun adminSyncOverview(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncAdminOverview?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getMdbListRatings(imdbId: String): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRatingsResult?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getTmdbSeasonRatings(tmdbId: String, seasonNumber: Int): Result<Map<Int, com.raulshma.jellyplay.core.network.api.JellyPlayEpisodeRatings>?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getJellyPlaySimilarItems(itemId: String, limit: Int): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlayScoredItem>> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getAnimeMarkers(seriesId: String, providerSeriesId: String): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySeriesMarkers?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getCustomRow(title: String): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowResult?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getCustomRowCatalog(): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSeasonalRow(keyword: String?): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowResult?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getBookmarks(itemId: String): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlayBookmark>> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun upsertBookmark(itemId: String, request: com.raulshma.jellyplay.core.network.api.JellyPlayBookmarkRequest): Result<com.raulshma.jellyplay.core.network.api.JellyPlayBookmark> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun deleteBookmark(itemId: String, bookmarkId: String): Result<Unit> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getUserRatings(filter: String?): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlayUserRating>> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getActiveTranscodes(): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode>> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getMyTranscodes(): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode>> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun cancelTranscode(sessionId: String): Result<Unit> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getAnalyticsOverview(days: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsOverview?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getAnalyticsSessions(userId: String?, since: Long?, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsSessions?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getMyAnalytics(days: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlayMyAnalytics?> =
            Result.failure(IllegalStateException("unused"))
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private class Harness(
        val api: FakePluginApi,
        val adapter: FakeAdapter,
        val repo: ProfileSyncRepository,
        val statusStore: JellyPlayPluginStatusStore,
    )

    private fun harness(
        local: Map<String, JsonElement>,
        remote: Map<String, JsonElement> = emptyMap(),
        rejectKeys: Set<String> = emptySet(),
        capabilitiesFail: Boolean = false,
        modes: Map<String, String>? = null,
    ): Harness {
        val api = FakePluginApi(remote.toMutableMap(), rejectKeys, capabilitiesFail, modes)
        val adapter = FakeAdapter("prefs", local)
        val registry = SessionCacheRegistry(FakeSessionIdentity(), CoroutineScope(Dispatchers.Default))
        val statusStore = JellyPlayPluginStatusStore(api, registry)
        val clock = kotlinx.coroutines.flow.MutableStateFlow(1_000L)
        val repo = ProfileSyncRepository(
            apiClient = api,
            statusStore = statusStore,
            sessionCacheRegistry = registry,
            adapters = listOf(adapter),
            deviceProfile = "desktop",
            deviceIdProvider = { "test-device" },
            nowMillis = { ++clock.value },
        )
        return Harness(api, adapter, repo, statusStore)
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    fun disabledByDefault_requestSyncIsNoOp() = runTest {
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))

        h.repo.requestSync()

        assertTrue(h.api.pushedWrites.isEmpty())
        assertFalse(h.repo.state.value.enabled)
    }

    @Test
    fun localChange_pushedAndServerAdopts() = runTest {
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")), remote = mapOf("prefs/theme" to JsonPrimitive("light")))

        h.repo.setEnabled(true)
        h.repo.requestSync()

        assertEquals(1, h.api.pushedWrites.size)
        assertEquals(JsonPrimitive("dark"), h.api.pushedWrites[0].value)
        assertEquals("dark", (h.api.remote["prefs/theme"] as JsonPrimitive).content)
        // Applied → marked synced → second cycle is a no-op push-wise.
        h.repo.requestSync()
        assertEquals(1, h.api.pushedWrites.size)
    }

    @Test
    fun cleanLocal_adoptsRemoteWinner() = runTest {
        val h = harness(local = mapOf("layout" to JsonPrimitive("list")), remote = mapOf("prefs/layout" to JsonPrimitive("grid")))

        h.repo.setEnabled(true)
        // Cycle 1: local "list" is dirty (never synced) → pushed, wins.
        h.repo.requestSync()

        // Another device won: server now holds "compact"; local stays clean (equal to mirror "list"? No —
        // mirror holds the pushed "list", local was set to grid manually = dirty).
        // Simulate the REAL clean-adopt path: mark local equal to its mirror, then the server value changes.
        h.adapter.local["layout"] = JsonPrimitive("list")
        h.adapter.markSynced(mapOf("layout" to JsonPrimitive("list")))
        h.api.remote["prefs/layout"] = JsonPrimitive("compact")

        h.repo.requestSync()

        assertEquals("compact", (h.adapter.local["layout"] as JsonPrimitive).content)
        assertTrue(h.adapter.appliedRemote.any { map -> map["layout"]?.toString() == "\"compact\"" })
    }

    @Test
    fun dirtyKey_neverOverwrittenByRemoteBeforePush() = runTest {
        // Local changed since last sync AND remote changed — local must push, not be adopted over.
        val h = harness(
            local = mapOf("layout" to JsonPrimitive("mine")),
            remote = mapOf("prefs/layout" to JsonPrimitive("theirs")),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync() // pushes "mine", server adopts (updatedAt newer)

        // Round 2: both sides change. Local (dirty vs mirror "mine") + server changed by other device.
        h.adapter.local["layout"] = JsonPrimitive("mine2")
        h.api.remote["prefs/layout"] = JsonPrimitive("theirs2")

        h.repo.requestSync()

        // Local wins the race: "mine2" pushed, never clobbered by "theirs2" pre-push.
        assertEquals("mine2", (h.api.remote["prefs/layout"] as JsonPrimitive).content)
        assertEquals("mine2", (h.adapter.local["layout"] as JsonPrimitive).content)
    }

    @Test
    fun rejectedWrite_staysDirty_andRetriesNextCycle() = runTest {
        // Server holds a NEWER write (simulated by rejecting our push as stale).
        val h = harness(
            local = mapOf("volume" to JsonPrimitive(80)),
            remote = mapOf("prefs/volume" to JsonPrimitive(30)),
            rejectKeys = setOf("prefs/volume"),
        )
        h.repo.setEnabled(true)

        h.repo.requestSync() // push rejected → "volume" stays dirty, local untouched
        assertTrue(h.api.pushedWrites.isNotEmpty())
        assertEquals(80, (h.adapter.local["volume"] as JsonPrimitive).content.toInt())

        // Server value wins on retry path: stop rejecting → push applies this time.
        h.api.rejectKeys = emptySet()
        h.repo.requestSync()
        assertEquals(80, (h.api.remote["prefs/volume"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun unavailablePlugin_endsQuietly_noPush_noErrorSurface() = runTest {
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")), capabilitiesFail = true)

        h.repo.setEnabled(true)
        h.repo.requestSync()

        assertTrue(h.api.pushedWrites.isEmpty())
        // ADR 0010 §1: probe failure = UNAVAILABLE, never an error surface —
        // the cycle ends quietly instead of writing lastError.
        assertNull(h.repo.state.value.lastError)
        assertNull(h.repo.state.value.lastSyncAt)
        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, h.statusStore.status.value)
    }

    @Test
    fun syncState_reportsOutcome() = runTest {
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))

        h.repo.setEnabled(true)
        h.repo.requestSync()

        val state = h.repo.state.value
        assertTrue(state.enabled)
        assertTrue(state.lastSyncAt != null)
        assertNull(state.lastError)
        assertTrue(state.syncedKeys >= 1)
        assertFalse(state.inFlight)
    }

    // ── forced keys (the admin-defaults `modes` map, the resolved payload's additive face) ──

    @Test
    fun forcedModes_exposedOnForcedKeys_afterCycle() = runTest {
        val h = harness(
            local = mapOf("theme" to JsonPrimitive("dark")),
            modes = mapOf(
                "prefs/pluginFeature.events.enabled" to "forced",
                "prefs/theme" to "suggested",
                "other/row" to "unset",
            ),
        )

        h.repo.setEnabled(true)
        h.repo.requestSync()

        // ONLY the `forced` mode locks — suggested/unset stay writable.
        assertEquals(setOf("prefs/pluginFeature.events.enabled"), h.repo.forcedKeys.value)
    }

    @Test
    fun absentModes_olderPlugin_forcedKeysStayEmpty() = runTest {
        // A payload without the additive `modes` field (an older plugin) must
        // read as "no mode in force anywhere", never as a stale set.
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")), modes = null)
        h.repo.setEnabled(true)
        h.repo.requestSync()

        assertTrue(h.repo.forcedKeys.value.isEmpty())

        // A later payload that drops the modes again clears the set.
        h.api.modes = mapOf("prefs/theme" to "forced")
        h.repo.requestSync()
        assertEquals(setOf("prefs/theme"), h.repo.forcedKeys.value)
        h.api.modes = null
        h.repo.requestSync()
        assertTrue(h.repo.forcedKeys.value.isEmpty())
    }
}
