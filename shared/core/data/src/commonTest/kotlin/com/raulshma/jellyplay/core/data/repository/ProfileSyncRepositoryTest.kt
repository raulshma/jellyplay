package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayAppliedSetting
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayRejectedSetting
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingDelete
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Engine tests for the profile-sync cycle: opt-in gating, adopt-clean/push-
 * dirty split, LWW-reject retry, convergence — against fakes, no I/O. The
 * tombstone half (Phase 1's `deleted[]`/`nextCursor` wave): outbound delete
 * adoption and anti-resurrection, the paged delta sweep, the persisted
 * cursor, and the non-retryable reject surfacing.
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

    /**
     * Mirror-tracking adapter mirroring the real adapter's dirty semantics, in
     * memory — plus the tombstone SPI: [deleteLocally] parks a pending
     * outbound tombstone (mirror keeps the key, local loses it) that
     * [deletedKeys] reports until an applied push (or [deleteRemote]) clears
     * it.
     */
    private class FakeAdapter(
        override val namespace: String,
        initial: Map<String, JsonElement>,
    ) : ProfileSyncAdapter {
        val local = initial.toMutableMap()
        private val mirror = mutableMapOf<String, JsonElement>()
        val appliedRemote = mutableListOf<Map<String, JsonElement>>()
        val synced = mutableListOf<Map<String, JsonElement>>()
        val deletedRemote = mutableListOf<Set<String>>()
        private val pendingDeletes = mutableSetOf<String>()

        /** Simulates a local user delete: the value goes, the mirror stays. */
        fun deleteLocally(vararg keys: String) {
            keys.forEach { local.remove(it); pendingDeletes += it }
        }

        override suspend fun snapshot(): Map<String, JsonElement> = local.toMap()

        override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
            current.filter { (key, value) -> mirror[key] != value }

        override suspend fun deletedKeys(): Set<String> = pendingDeletes.toSet()

        override suspend fun applyRemote(entries: Map<String, JsonElement>) {
            appliedRemote += entries
            local.putAll(entries)
            // A restored value un-deletes the key (the real adapters derive
            // their outbound tombstones from mirror-vs-snapshot, so a value
            // back in the snapshot stops reading as a pending delete).
            pendingDeletes.removeAll(entries.keys)
        }

        override suspend fun deleteRemote(keys: Set<String>) {
            deletedRemote += keys
            keys.forEach { key ->
                local.remove(key)
                mirror.remove(key)
                pendingDeletes.remove(key)
            }
        }

        override suspend fun markSynced(values: Map<String, JsonElement>) {
            synced += values
            mirror.putAll(values)
        }
    }

    /**
     * Api fake: capabilities AVAILABLE (unless [capabilitiesFail]); server
     * state in [remote]. Tombstone writes (`JsonNull` values) REMOVE the row
     * — the wire's delete op. Delta reads serve [deltaPages] in order (a
     * page's `nextCursor` drives the engine's loop); once exhausted, one
     * empty page headed by the current seq (the legacy unpaged shape).
     */
    private class FakePluginApi(
        val remote: MutableMap<String, JsonElement> = mutableMapOf(),
        var rejectKeys: Set<String> = emptySet(),
        /** Per-key reject reasons; anything absent defaults to `stale-write`. */
        var rejectReasons: Map<String, String> = emptyMap(),
        var capabilitiesFail: Boolean = false,
        /** The resolved-profile payload's admin-defaults modes ("ns/key" → mode). */
        var modes: Map<String, String>? = null,
        /** The delta pages served in request order. */
        val deltaPages: ArrayDeque<JellyPlaySettingsSnapshot> = ArrayDeque(),
    ) : JellyPlayCapabilitiesRoutes, JellyPlaySettingsSyncRoutes {
        val pushedWrites = mutableListOf<JellyPlaySettingWrite>()
        /** Every delta read: (since, limit, cursor) triple, in request order. */
        val deltaRequests = mutableListOf<Triple<Long, Int?, Long?>>()
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

        override suspend fun getChangedSettings(
            since: Long,
            profile: String?,
            limit: Int?,
            cursor: Long?,
        ): Result<JellyPlaySettingsSnapshot> {
            deltaRequests += Triple(since, limit, cursor)
            val page = deltaPages.removeFirstOrNull()
                ?: JellyPlaySettingsSnapshot(head = seq, profile = "", settings = emptyList())
            return Result.success(page)
        }

        override suspend fun applySettings(
            profile: String?,
            deviceId: String?,
            writes: List<JellyPlaySettingWrite>,
        ): Result<JellyPlaySettingsBatchResult> {
            pushedWrites += writes
            seq += 1
            val applied = writes.filter { "${it.ns}/${it.key}" !in rejectKeys }
            applied.forEach { write ->
                // A JsonNull write is the delete op: the row goes.
                if (write.value is JsonNull) remote.remove("${write.ns}/${write.key}")
                else remote["${write.ns}/${write.key}"] = write.value
            }
            return Result.success(
                JellyPlaySettingsBatchResult(
                    head = seq,
                    applied = applied.map { JellyPlayAppliedSetting(it.ns, it.key, it.updatedAt, seq) },
                    rejected = writes.filter { it !in applied }.map {
                        JellyPlayRejectedSetting(it.ns, it.key, rejectReasons["${it.ns}/${it.key}"] ?: "stale-write")
                    },
                ),
            )
        }

        override suspend fun resetNamespace(ns: String, profile: String?): Result<Unit> = Result.success(Unit)
        override suspend fun resolveProfile(profile: String?): Result<JellyPlaySettingsSnapshot> = Result.success(snapshotOf())
        override fun settingsStream(resumeFromEventId: Long): Flow<com.raulshma.jellyplay.core.network.api.JellyPlaySseEvent> = emptyFlow()

        // ── sync-ledger / restore-point / export-import routes (settings-sync
        // role members this suite never exercises) ──
        override suspend fun getSyncStatus(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncHistory(since: Long?, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistory?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncHistoryKeys(seq: Long, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKeys?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun adminSyncOverview(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncAdminOverview?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSnapshots(): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot>?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun createSnapshot(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotCreated?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun restoreSnapshot(id: String): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun getSnapshotContent(id: String): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotContent?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun exportSettings(): Result<String?> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun importSettings(bundleJson: String, deviceId: String?): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult?> =
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
        val savedCursors: MutableList<Long>,
        val savedToggles: MutableList<Pair<String, Boolean>>,
    )

    private fun harness(
        local: Map<String, JsonElement>,
        remote: Map<String, JsonElement> = emptyMap(),
        rejectKeys: Set<String> = emptySet(),
        rejectReasons: Map<String, String> = emptyMap(),
        capabilitiesFail: Boolean = false,
        modes: Map<String, String>? = null,
        deltaPages: ArrayDeque<JellyPlaySettingsSnapshot> = ArrayDeque(),
        namespaceEnabled: MutableMap<String, Boolean>? = null,
    ): Harness {
        val api = FakePluginApi(remote.toMutableMap(), rejectKeys, rejectReasons, capabilitiesFail, modes, deltaPages)
        val adapter = FakeAdapter("prefs", local)
        val registry = SessionCacheRegistry(FakeSessionIdentity(), CoroutineScope(Dispatchers.Default))
        val statusStore = JellyPlayPluginStatusStore(api, registry)
        val clock = kotlinx.coroutines.flow.MutableStateFlow(1_000L)
        val savedCursors = mutableListOf<Long>()
        val savedToggles = mutableListOf<Pair<String, Boolean>>()
        val repo = ProfileSyncRepository(
            apiClient = api,
            statusStore = statusStore,
            sessionCacheRegistry = registry,
            adapters = listOf(adapter),
            deviceProfile = "desktop",
            deviceIdProvider = { "test-device" },
            nowMillis = { ++clock.value },
            loadDeltaCursor = { null },
            saveDeltaCursor = { value -> savedCursors += value },
            // The selective-sync seams: wired only when the caller hands a
            // toggle map (null on both = the every-namespace-enabled default).
            loadNamespaceEnabled = if (namespaceEnabled != null) (
                { ns -> namespaceEnabled[ns] ?: true }
                ) else null,
            saveNamespaceEnabled = if (namespaceEnabled != null) (
                { ns, value ->
                    savedToggles += ns to value
                    namespaceEnabled[ns] = value
                }
                ) else null,
        )
        return Harness(api, adapter, repo, statusStore, savedCursors, savedToggles)
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
        // The LWW retry loop's normal traffic never reads as a failure state.
        assertTrue(h.repo.state.value.rejectedKeys.isEmpty())

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

    // ── tombstones: outbound deletes, anti-resurrection, inbound `deleted[]` ──

    @Test
    fun localDelete_pushedAsTombstone_andDoesNotResurrect() = runTest {
        // A synced key the user deleted locally must NOT be adopted back over
        // (the pre-push ADOPT would resurrect it), and must leave as a
        // null-value write the server applies as a tombstone.
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")), remote = mapOf("prefs/theme" to JsonPrimitive("light")))
        // Establish the synced baseline (the push lands, the mirror holds "theme").
        h.repo.setEnabled(true)
        h.repo.requestSync()
        assertEquals(1, h.api.pushedWrites.size)

        h.adapter.deleteLocally("theme")
        h.repo.requestSync()

        // No resurrection: the differing remote row was never applied.
        assertFalse(h.adapter.appliedRemote.any { it.containsKey("theme") })
        assertNull(h.adapter.local["theme"])
        // The delete left as the wire's delete op.
        val delete = h.api.pushedWrites.last()
        assertEquals("theme", delete.key)
        assertEquals(JsonNull, delete.value)
        // Applied → the engine confirms through deleteRemote (mirror entry gone).
        assertTrue(h.adapter.deletedRemote.last().contains("theme"))

        // Next cycle: nothing pending, nothing re-pushed — the delete stuck.
        val writesBefore = h.api.pushedWrites.size
        h.repo.requestSync()
        assertEquals(writesBefore, h.api.pushedWrites.size)
        assertNull(h.adapter.local["theme"])
    }

    @Test
    fun rejectedDelete_keepsPending_andRetriesNextCycle() = runTest {
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))
        h.repo.setEnabled(true)
        h.repo.requestSync() // baseline push lands, mirror holds "theme"

        h.adapter.deleteLocally("theme")
        h.api.rejectKeys = setOf("prefs/theme")
        h.repo.requestSync()

        // Rejected tombstone: mirror entry stays, the key stays pending, and
        // a `stale-write` reject never reads as a failure state.
        assertTrue(h.adapter.deletedRemote.isEmpty())
        h.api.rejectKeys = emptySet()
        h.repo.requestSync()
        assertTrue(h.adapter.deletedRemote.last().contains("theme"))
        assertTrue(h.repo.state.value.rejectedKeys.isEmpty())
    }

    @Test
    fun deltaDeleted_appliedLocally_throughDeleteRemote() = runTest {
        // Another device (or a namespace reset) tombstoned the key: the delta
        // sweep's `deleted[]` removes the local value AND its mirror entry.
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))
        h.repo.setEnabled(true)
        h.repo.requestSync() // baseline push lands; the server row exists, the mirror holds it

        // The server-side delete: the row leaves the store (a real resolved
        // view no longer lists it) and the change-log row shows up in the
        // sweep's `deleted[]`.
        h.api.remote.remove("prefs/theme")
        h.api.deltaPages += JellyPlaySettingsSnapshot(head = 10, deleted = listOf(JellyPlaySettingDelete("prefs", "theme")))
        h.repo.requestSync()

        assertTrue(h.adapter.deletedRemote.last().contains("theme"))
        assertNull(h.adapter.local["theme"])

        // And it stays gone: nothing pending, nothing to adopt, nothing pushed.
        val writesBefore = h.api.pushedWrites.size
        h.repo.requestSync()
        assertEquals(writesBefore, h.api.pushedWrites.size)
        assertNull(h.adapter.local["theme"])
    }

    // ── pagination: the paged delta sweep + the persisted resume cursor ──

    @Test
    fun deltaSweep_followsNextCursor_untilNull_andAppliesEveryPage() = runTest {
        val h = harness(
            local = mapOf("stale" to JsonPrimitive("old"), "gone" to JsonPrimitive("here")),
            deltaPages = ArrayDeque(
                listOf(
                    JellyPlaySettingsSnapshot(
                        head = 50,
                        settings = listOf(
                            entryOf("prefs/fresh", JsonPrimitive("value")),
                        ),
                        nextCursor = 55,
                    ),
                    JellyPlaySettingsSnapshot(
                        head = 77,
                        deleted = listOf(JellyPlaySettingDelete("prefs", "gone")),
                        nextCursor = null,
                    ),
                ),
            ),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()

        // The loop followed the cursor chain: page 1 unpaged-cursor, page 2
        // resumed at 55, both with the sweep's page limit.
        assertEquals(2, h.api.deltaRequests.size)
        assertEquals(Triple(0L, 200, null), h.api.deltaRequests[0])
        assertEquals(Triple(0L, 200, 55L), h.api.deltaRequests[1])
        // Page 1's value landed, page 2's tombstone removed the row.
        assertEquals("value", (h.adapter.local["fresh"] as JsonPrimitive).content)
        assertNull(h.adapter.local["gone"])
        // The fully drained sweep persisted the LAST page's head.
        assertEquals(listOf(77L), h.savedCursors)
    }

    @Test
    fun deltaCursor_resumesFromPersistedValue() = runTest {
        val h = harness(
            local = emptyMap(),
            deltaPages = ArrayDeque(listOf(JellyPlaySettingsSnapshot(head = 42))),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()
        assertEquals(listOf(42L), h.savedCursors)

        // The next sweep starts at the persisted cursor, not from zero.
        h.repo.requestSync()
        assertEquals(42L, h.api.deltaRequests.last().first)
    }

    @Test
    fun deltaSweep_oldPluginPayload_behavesLikeToday() = runTest {
        // A page without `deleted`/`nextCursor` (an older plugin) decodes to
        // the defaults: single page, values only, no crash.
        val h = harness(
            local = mapOf("theme" to JsonPrimitive("dark")),
            deltaPages = ArrayDeque(
                listOf(
                    JellyPlaySettingsSnapshot(
                        head = 5,
                        settings = listOf(entryOf("prefs/other", JsonPrimitive("x"))),
                    ),
                ),
            ),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()

        assertEquals(1, h.api.deltaRequests.size)
        assertEquals("x", (h.adapter.local["other"] as JsonPrimitive).content)
        assertTrue(h.adapter.deletedRemote.isEmpty())
        // head 0-clamped old payloads never persist a bogus cursor.
        assertEquals(listOf(5L), h.savedCursors)
    }

    @Test
    fun deltaSweep_adoptsOnlyKeysTheResolvedPullDidNotArbitrate() = runTest {
        // Mixed delta payload: `theme` rides the resolved view this cycle
        // already arbitrated — re-adopting the sweep's differing value would
        // fight the profile merge, so the resolved row stands and the sweep's
        // row is skipped; `lang` is the freshness tail (absent from the
        // resolved pull) and adopts.
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))
        h.repo.setEnabled(true)
        h.repo.requestSync() // baseline push lands; the mirror holds "dark"

        // Another device's write: the next resolved pull arbitrates
        // prefs/theme (reader/theme rides the same payload — another
        // namespace, same key, must not shadow prefs' arbitration). The
        // sweep carries a differing row for the arbitrated key plus the
        // fresh tail.
        h.api.remote["prefs/theme"] = JsonPrimitive("light")
        h.api.remote["reader/theme"] = JsonPrimitive("x")
        h.api.deltaPages += JellyPlaySettingsSnapshot(
            head = 9,
            settings = listOf(
                entryOf("prefs/theme", JsonPrimitive("blue")),
                entryOf("prefs/lang", JsonPrimitive("de")),
            ),
        )
        h.repo.requestSync()

        // The arbitrated key keeps its resolved-pull value; the fresh tail lands.
        assertEquals("light", (h.adapter.local["theme"] as JsonPrimitive).content)
        assertEquals("de", (h.adapter.local["lang"] as JsonPrimitive).content)
    }

    // ── non-retryable rejects: clock-skew / device-revoked surface, not retry ──

    @Test
    fun clockSkewReject_surfacesInSyncState_notSilentRetry() = runTest {
        val h = harness(
            local = mapOf("theme" to JsonPrimitive("dark")),
            remote = mapOf("prefs/theme" to JsonPrimitive("light")),
            rejectKeys = setOf("prefs/theme"),
            rejectReasons = mapOf("prefs/theme" to "clock-skew"),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()

        val state = h.repo.state.value
        assertEquals(mapOf("prefs/theme" to "clock-skew"), state.rejectedKeys)
        assertTrue(state.lastError!!.contains("clock-skew"))
        // The key stays dirty (a clock fix + retry remains possible), but the
        // failure is VISIBLE — not the quiet stale-write loop.
        assertEquals("dark", (h.adapter.local["theme"] as JsonPrimitive).content)
    }

    @Test
    fun deviceRevokedReject_surfacesInSyncState_andClearsOnHealthyCycle() = runTest {
        val h = harness(
            local = mapOf("theme" to JsonPrimitive("dark")),
            remote = mapOf("prefs/theme" to JsonPrimitive("light")),
            rejectKeys = setOf("prefs/theme"),
            rejectReasons = mapOf("prefs/theme" to "device-revoked"),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()
        assertEquals(mapOf("prefs/theme" to "device-revoked"), h.repo.state.value.rejectedKeys)

        // The device gets re-registered (revocation lifted): the next healthy
        // cycle clears the failure state instead of carrying it forever.
        h.api.rejectKeys = emptySet()
        h.repo.requestSync()
        assertTrue(h.repo.state.value.rejectedKeys.isEmpty())
        assertNull(h.repo.state.value.lastError)
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

    // ── selective sync: the per-namespace device-local toggles ──

    @Test
    fun selectiveSync_disabledNamespace_skipsPushAdoptAndDelta_untilReEnabled() = runTest {
        val toggles = mutableMapOf("prefs" to false)
        val h = harness(
            local = mapOf("theme" to JsonPrimitive("dark")),
            remote = mapOf("prefs/theme" to JsonPrimitive("light")),
            deltaPages = ArrayDeque(
                listOf(JellyPlaySettingsSnapshot(head = 9, settings = listOf(entryOf("prefs/other", JsonPrimitive("x"))))),
            ),
            namespaceEnabled = toggles,
        )
        h.repo.setEnabled(true)

        h.repo.requestSync()

        // The disabled namespace skipped the whole cycle: nothing pushed,
        // nothing adopted, the local pending edit parked untouched.
        assertTrue(h.api.pushedWrites.isEmpty())
        assertEquals("dark", (h.adapter.local["theme"] as JsonPrimitive).content)
        assertTrue(h.adapter.appliedRemote.isEmpty())
        assertTrue(h.adapter.deletedRemote.isEmpty())
        // Its pending keys never read as pending — opted out, not stuck.
        assertTrue(h.repo.state.value.pendingByNamespace.isEmpty())

        // The toggle's flip persists through the seam and re-syncs at once:
        // the newly-enabled namespace's first cycle pushes the parked edit.
        h.repo.setNamespaceEnabled("prefs", true)
        assertEquals(listOf("prefs" to true), h.savedToggles)
        assertTrue(h.api.pushedWrites.isNotEmpty(), "re-enabling must run a cycle that flushes the parked edit")
        assertEquals(JsonPrimitive("dark"), h.api.remote["prefs/theme"])

        // And a subsequent local change rides the normal dirty path.
        h.adapter.local["theme"] = JsonPrimitive("mine")
        h.repo.requestSync()
        assertEquals(JsonPrimitive("mine"), h.api.remote["prefs/theme"])
    }

    @Test
    fun selectiveSync_failedToggleRead_defaultsToEnabled() = runTest {
        // A throwing seam read must never silently unsync a namespace.
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))
        val registry = SessionCacheRegistry(FakeSessionIdentity(), CoroutineScope(Dispatchers.Default))
        val failing = ProfileSyncRepository(
            apiClient = h.api,
            statusStore = JellyPlayPluginStatusStore(h.api, registry),
            sessionCacheRegistry = registry,
            adapters = listOf(h.adapter),
            deviceProfile = "desktop",
            deviceIdProvider = { "test-device" },
            nowMillis = { 1L },
            loadNamespaceEnabled = { throw IllegalStateException("io") },
            saveNamespaceEnabled = { _, _ -> throw IllegalStateException("io") },
        )
        failing.setEnabled(true)
        failing.requestSync()
        assertEquals(JsonPrimitive("dark"), h.api.remote["prefs/theme"], "a failed toggle read must not block the push")
    }

    @Test
    fun selectiveSync_deltaSkipsDisabledNamespace_butCursorStillAdvances() = runTest {
        val toggles = mutableMapOf("prefs" to false)
        val h = harness(
            local = emptyMap(),
            deltaPages = ArrayDeque(
                listOf(JellyPlaySettingsSnapshot(head = 30, settings = listOf(entryOf("prefs/skipped", JsonPrimitive("v"))))),
            ),
            namespaceEnabled = toggles,
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()

        assertNull(h.adapter.local["skipped"])
        // The drained sweep still advances the cursor: skipped rows are
        // skipped, not replayed forever.
        assertEquals(listOf(30L), h.savedCursors)
    }

    // ── conflicts: stale-write rejects with ours/theirs + the resolutions ──

    @Test
    fun staleWrite_conflictCapturesOursAndTheirs_atRejectTime() = runTest {
        val h = harness(
            local = mapOf("volume" to JsonPrimitive(80)),
            remote = mapOf("prefs/volume" to JsonPrimitive(30)),
            rejectKeys = setOf("prefs/volume"),
        )
        // Theirs carries a stamp (the resolved payload's row).
        h.api.remote["prefs/volume"] = JsonPrimitive(30)

        h.repo.setEnabled(true)
        h.repo.requestSync()

        val conflicts = h.repo.state.value.conflicts
        assertEquals(1, conflicts.size)
        val conflict = conflicts.single()
        assertEquals("prefs", conflict.ns)
        assertEquals("volume", conflict.key)
        assertEquals("stale-write", conflict.reason)
        assertEquals(JsonPrimitive(80), conflict.ours)
        assertEquals(JsonPrimitive(30), conflict.theirs)
        // `stale-write` stays OFF the fatal-reject face.
        assertTrue(h.repo.state.value.rejectedKeys.isEmpty())
        // The key keeps its normal retry semantics: local value parked, pending.
        assertEquals(80, (h.adapter.local["volume"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun healthyCycle_clearsConflicts_andPendingFace() = runTest {
        val h = harness(
            local = mapOf("volume" to JsonPrimitive(80)),
            remote = mapOf("prefs/volume" to JsonPrimitive(30)),
            rejectKeys = setOf("prefs/volume"),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()
        assertTrue(h.repo.state.value.conflicts.isNotEmpty())
        assertEquals(1, h.repo.state.value.pendingByNamespace["prefs"])

        h.api.rejectKeys = emptySet()
        h.repo.requestSync()

        assertTrue(h.repo.state.value.conflicts.isEmpty())
        assertTrue(h.repo.state.value.pendingByNamespace.isEmpty())
    }

    @Test
    fun resolveConflictKeepMine_repushesWithFreshStamp_andClears() = runTest {
        val h = harness(
            local = mapOf("volume" to JsonPrimitive(80)),
            remote = mapOf("prefs/volume" to JsonPrimitive(30)),
            rejectKeys = setOf("prefs/volume"),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync() // rejected once
        assertEquals(1, h.api.pushedWrites.size)

        // The server's stale row stops blocking: the fresh-stamp re-push wins.
        h.api.rejectKeys = emptySet()
        h.repo.resolveConflictKeepMine("prefs", "volume")

        // The re-push applied: the server holds OURS, the mirror caught up,
        // the conflict left the state.
        assertEquals(2, h.api.pushedWrites.size)
        assertEquals(JsonPrimitive(80), h.api.remote["prefs/volume"])
        assertTrue(h.repo.state.value.conflicts.isEmpty())
        h.repo.requestSync()
        assertEquals(2, h.api.pushedWrites.size, "a resolved key must stop reading dirty")
    }

    @Test
    fun resolveConflictTakeTheirs_adoptsRemoteRow_andClears() = runTest {
        val h = harness(
            local = mapOf("volume" to JsonPrimitive(80)),
            remote = mapOf("prefs/volume" to JsonPrimitive(30)),
            rejectKeys = setOf("prefs/volume"),
        )
        h.repo.setEnabled(true)
        h.repo.requestSync()

        h.repo.resolveConflictTakeTheirs("prefs", "volume")

        assertEquals(30, (h.adapter.local["volume"] as JsonPrimitive).content.toInt())
        assertTrue(h.repo.state.value.conflicts.isEmpty())
        // Synced: the adopted row reads clean on the next cycle.
        h.repo.requestSync()
        assertEquals(1, h.api.pushedWrites.size, "take-theirs must not push anything new")
    }

    @Test
    fun resolveConflict_onPendingDelete_keepMineRepushesTombstone_takeTheirsRestores() = runTest {
        // A delete that lost LWW: ours is null (a pending tombstone).
        val h = harness(local = mapOf("theme" to JsonPrimitive("dark")))
        h.repo.setEnabled(true)
        h.repo.requestSync() // baseline push lands

        h.adapter.deleteLocally("theme")
        h.api.remote["prefs/theme"] = JsonPrimitive("newer")
        h.api.rejectKeys = setOf("prefs/theme")
        h.repo.requestSync()
        val conflict = h.repo.state.value.conflicts.single()
        assertEquals("theme", conflict.key)
        assertNull(conflict.ours, "a pending delete's ours-side is null")
        assertEquals(JsonPrimitive("newer"), conflict.theirs)

        // keep mine: the tombstone re-pushes as the delete op and lands.
        h.api.rejectKeys = emptySet()
        h.repo.resolveConflictKeepMine("prefs", "theme")
        assertNull(h.api.remote["prefs/theme"])
        assertTrue(h.repo.state.value.conflicts.isEmpty())
        assertTrue(h.adapter.deletedRemote.last().contains("theme"))

        // take theirs on a fresh conflict restores the value (deleteRemote's
        // mirror + the applied row re-align).
        h.adapter.local["theme"] = JsonPrimitive("again")
        h.adapter.markSynced(mapOf("theme" to JsonPrimitive("again")))
        h.adapter.deleteLocally("theme")
        h.api.remote["prefs/theme"] = JsonPrimitive("server-wins")
        h.api.rejectKeys = setOf("prefs/theme")
        h.repo.requestSync()
        h.api.rejectKeys = emptySet()
        h.repo.resolveConflictTakeTheirs("prefs", "theme")
        assertEquals(JsonPrimitive("server-wins"), h.adapter.local["theme"])
        assertTrue(h.repo.state.value.conflicts.isEmpty())
    }

    private fun entryOf(compositeKey: String, value: JsonElement): com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry {
        val (ns, key) = compositeKey.split('/').let { it[0] to it.getOrElse(1) { "" } }
        return com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry(ns, key, 1, 0, "srv", "", value)
    }
}
