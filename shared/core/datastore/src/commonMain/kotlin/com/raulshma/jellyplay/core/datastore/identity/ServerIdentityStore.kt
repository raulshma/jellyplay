package com.raulshma.jellyplay.core.datastore.identity

import com.raulshma.jellyplay.core.datastore.randomUuidString

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The persisted session-identity machine — the ONE read model over the two
 * DataStore session keys (`active_server_id` / `active_user_id`). Sealed phase
 * per the state-management convention (§3 infra machines): the former shape
 * observed the two raw keys independently, so the impossible combinations were
 * representable and observable downstream — most notably
 * `ServerIdentity(activeUserId = "")` (the raw `activeUserId` flow did NOT
 * apply the blank-filter its `activeUserIdIn` sibling had) and the three
 * on-disk encodings of "signed out" (absent key / `""` sentinel / key
 * removal) that only one read path resolved uniformly. This type folds the
 * former `ServerIdentity` data class in rather than duplicating it: the
 * session fields become the phase payloads, and the device id — which is
 * device state, not session state — moved to [ServerIdentityStore.deviceId]'s
 * own flow.
 *
 * Variants are exactly the representable session states:
 *
 *  - [NoSession] — no usable server selection (covers a legacy disk with a
 *    blank or absent server key, and the degenerate user-key-without-server
 *    garbage state, which is unrestorable and reads as signed out).
 *  - [ServerSelected] — a server is pointed at, no user signed in (the
 *    state a switch to a server with no remembered user persists, and the
 *    state `clearUser` lands in).
 *  - [ActiveSession] — server + user, i.e. what a cold start can fully
 *    restore.
 *
 * **Blank == absent, uniformly, for BOTH keys.** Legacy installs carry `""`
 * sentinel values written by the former `setActiveSession("", "")` /
 * `setActiveUser("")` calls; [resolve] reads them exactly as an absent key
 * (pinned in ServerIdentityStoreTest). Writes enforce the same rule from the
 * other side: the store's commands require non-blank ids and express
 * "absent" only through key removal, so no new blank sentinel can be minted.
 *
 * The on-disk key names and bytes are untouched by this migration — existing
 * installs restore identically.
 */
sealed interface PersistedIdentity {

    /** The server the persisted selection names — every phase except [NoSession] carries one. */
    val serverId: String? get() = null

    /** The signed-in user — exactly [ActiveSession]'s payload. */
    val userId: String? get() = null

    /** No usable session: nothing to adopt on cold start. */
    data object NoSession : PersistedIdentity

    /**
     * A server is selected, no user is signed in. Cold start re-points the
     * client at the server (the login flow opens on that server's user list);
     * no token is adoptable.
     */
    data class ServerSelected(
        override val serverId: String,
    ) : PersistedIdentity

    /** The fully restorable session: server + signed-in user. */
    data class ActiveSession(
        override val serverId: String,
        override val userId: String,
    ) : PersistedIdentity

    companion object {

        /**
         * The pure decision core of the machine (convention §3): resolves the
         * two RAW on-disk values into the phase, applying the blank==absent
         * rule uniformly — unit-testable without DataStore, and the ONE place
         * the legacy-sentinel semantics live.
         */
        fun resolve(rawServerId: String?, rawUserId: String?): PersistedIdentity = when {
            rawServerId.isNullOrBlank() -> NoSession
            rawUserId.isNullOrBlank() -> ServerSelected(rawServerId)
            else -> ActiveSession(rawServerId, rawUserId)
        }
    }
}

/**
 * Active server / user / device identity for the current session.
 *
 * This is session state, not a user preference — which server the app is
 * connected to and which user is signed in drives auth, networking, and
 * per-user data scoping. Extracted from `UserPreferencesStore` (where it
 * accreted) so consumers stop importing a 3000-line preference god object
 * just to read the active user id.
 *
 * **Storage**: reuses the same `"user_prefs"` DataStore file as
 * `UserPreferencesStore` (same key strings). The DataStore singleton is keyed
 * by `(applicationContext, name)`, so both classes reach the same instance —
 * no migration, no second file.
 *
 * **Read model**: [identity] is the store's ONE state flow — the sealed
 * [PersistedIdentity] phase derived from the two session keys with the
 * blank==absent rule applied uniformly (see the type's KDoc).
 * [activeServerId] / [activeUserId] are derived projections of that flow for
 * consumers that need a single field, NOT independent raw-key flows anymore —
 * a blank on disk never flows through them. [deviceId] is the device id's own
 * eagerly-shared flow (it was folded out of the former `ServerIdentity`
 * snapshot: device state, not session state; the DI-critical `.value` fast
 * path of `resolveDeviceId` reads it the same way).
 *
 * **Write model**: four phase-shaped commands, all single-edit:
 * [setActiveSession] (→ ActiveSession, non-blank args REQUIRED — the blank
 * sentinel is unrepresentable), [setSelectedServer] (→ ServerSelected, and it
 * removes any stale user key in the same edit), [clearUser] (→ ServerSelected
 * when a server key remains) and [clearSession] (→ NoSession). "Absent" is
 * expressed ONLY through key removal; the former ad-hoc blank-sentinel writes
 * (`setActiveSession("", "")` / `setActiveUser("")`) are gone.
 *
 * **Declared residue (out of scope here)**: `ServerInfo.isConnected` is
 * derived from the `ServerEntity.accessToken` shadow-token column
 * (`AuthRepositoryImpl.toServerInfo`'s `isConnected = accessToken != null`) —
 * a stale DB projection of "a stored token exists", a different notion from
 * the WebSocket `isConnected`; the ServerEntity shadow columns themselves
 * stay as-is. Both are recorded as known residue, not bugs this store fixes.
 */
class ServerIdentityStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope
    private val sharedPrefs: Flow<Preferences> = dataStore.data

    /** The raw key reads — private; only [identity]'s derivation may consume them. */
    private val rawServerId: Flow<String?> =
        sharedPrefs.map { it[Keys.ACTIVE_SERVER_ID] }.distinctUntilChanged()

    private val rawUserId: Flow<String?> =
        sharedPrefs.map { it[Keys.ACTIVE_USER_ID] }.distinctUntilChanged()

    /**
     * The ONE session-identity state flow: the [PersistedIdentity] phase
     * derived from the two keys via [PersistedIdentity.resolve]. Consumers
     * that need to react to *any* session change (e.g. to re-fetch user-scoped
     * data) collect this; single-field consumers read the projections below.
     */
    val identity: StateFlow<PersistedIdentity> =
        combine(rawServerId, rawUserId) { serverId, userId ->
            PersistedIdentity.resolve(serverId, userId)
        }.stateIn(scope, SharingStarted.Eagerly, PersistedIdentity.NoSession)

    /**
     * One-shot, DISK-BACKED identity read — the cold-start-safe sibling of
     * [identity]. Not a plain `identity.first()`: a stateIn(Eagerly) StateFlow
     * always has its initial value available, so a plain first() returns the
     * [PersistedIdentity.NoSession] seed instantly during the DataStore-read
     * window — with no happens-before edge between the eagerly-started
     * collector and the caller — and a cold-start reader (session restore,
     * fresh-process workers) silently maps the seed to "nothing to restore"
     * even when disk holds an ActiveSession. The same trap the deviceId read
     * dodges in AppStartupPrewarms' critical prewarm. This read collects the
     * RAW cold `dataStore.data` flows instead, suspending until the first
     * disk read completes, so it always answers disk truth.
     *
     * [identity] stays the observer surface; call this for one-shot reads that
     * must not race application startup.
     */
    suspend fun readIdentity(): PersistedIdentity =
        combine(rawServerId, rawUserId) { serverId, userId ->
            PersistedIdentity.resolve(serverId, userId)
        }.first()

    /**
     * The device id's own eagerly-shared flow. Eager so a `.value` read never
     * suspends — `resolveDeviceId`'s DI-critical fast path and both platform
     * prewarms (which await the first non-null publication) read it directly.
     */
    val deviceId: StateFlow<String?> =
        sharedPrefs.map { it[Keys.DEVICE_ID] }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Derived projection of [identity]: the selected server, null when no
     * usable server is selected. Blank-free by construction (the phase space
     * never carries a blank id) — legacy on-disk sentinels read as null.
     */
    val activeServerId: Flow<String?> =
        identity.map { it.serverId }.distinctUntilChanged()

    /**
     * Derived projection of [identity]: the signed-in user, null unless the
     * phase is [PersistedIdentity.ActiveSession]. Blank-free by construction —
     * the former raw flow leaked `ServerIdentity(activeUserId = "")`
     * downstream on legacy disks; this never does.
     */
    val activeUserId: Flow<String?> =
        identity.map { it.userId }.distinctUntilChanged()

    /** Lazily persists a stable device id on first access; idempotent thereafter. */
    suspend fun ensureDeviceId(): String {
        var id: String? = null
        dataStore.edit { prefs ->
            id = prefs[Keys.DEVICE_ID]
                ?: randomUuidString().also { prefs[Keys.DEVICE_ID] = it }
        }
        return id ?: error("deviceId could not be resolved")
    }

    /**
     * Persists the full [PersistedIdentity.ActiveSession] phase in one
     * DataStore edit. Two back-to-back single-key writes each opened their own
     * `edit {}` → 2 disk reads + 2 atomic writes + 2 downstream
     * re-derivations; batching halves the I/O and the re-derivation cascade.
     *
     * Non-blank args REQUIRED: the blank-sentinel encoding of "signed out" is
     * unrepresentable at the write side — clearing is [clearSession]'s /
     * [clearUser]'s key-removal job.
     */
    suspend fun setActiveSession(serverId: String, userId: String) {
        require(serverId.isNotBlank()) {
            "serverId must be non-blank — clear the session via clearSession(), not a blank sentinel"
        }
        require(userId.isNotBlank()) {
            "userId must be non-blank — clear the user via clearUser(), not a blank sentinel"
        }
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SERVER_ID] = serverId
            prefs[Keys.ACTIVE_USER_ID] = userId
        }
    }

    /**
     * Persists the [PersistedIdentity.ServerSelected] phase: points the
     * selection at [serverId] and removes any stale user key IN THE SAME edit.
     * The removal is load-bearing — a remembered user of the PREVIOUS server
     * must not ride the new selection (an ActiveSession(newServer, oldUser)
     * on-disk pair would restore a token minted for the other server).
     *
     * The write-behind-the-bug this closes: the former userless server-switch
     * path re-pointed the client in memory and wrote NOTHING, so the DataStore
     * kept naming the old server and the next cold start restored it.
     */
    suspend fun setSelectedServer(serverId: String) {
        require(serverId.isNotBlank()) {
            "serverId must be non-blank — clear the session via clearSession(), not a blank sentinel"
        }
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SERVER_ID] = serverId
            prefs.remove(Keys.ACTIVE_USER_ID)
        }
    }

    /**
     * Removes the user key only → [PersistedIdentity.ServerSelected] when a
     * server key remains, [PersistedIdentity.NoSession] otherwise. The ONE
     * user-clear idiom (it replaces the former `setActiveUser("")` sentinel
     * and keeps the server selection intact — e.g. the active user row
     * vanishing leaves the app pointed at its server).
     */
    suspend fun clearUser() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_USER_ID)
        }
    }

    /** Drops the active server + user (→ NoSession) but keeps the device id and all prefs. */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_SERVER_ID)
            prefs.remove(Keys.ACTIVE_USER_ID)
        }
    }

    /**
     * Active user id read straight from a [Preferences] snapshot — for callers
     * already holding one, e.g. inside a `DataStore.edit` transform where
     * collecting [activeUserId] would re-enter the DataStore. A blank id (or
     * absence, pre-login) reads as null — the same blank==absent rule the
     * typed flow applies; this helper predates it and never leaked a blank.
     * In-module keying helper for per-user stores (e.g. `HomeDiscoveryStore`'s
     * `u_<userId>::` namespace).
     */
    fun activeUserIdIn(prefs: Preferences): String? =
        prefs[Keys.ACTIVE_USER_ID]?.takeIf { it.isNotBlank() }

    private object Keys {
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    companion object {
        /**
         * Key names that must never leave the device through settings sync —
         * session/device identity is per-device by design (docs/
         * jellyplay-plugin.md's "never synced: … identity" list). Referenced
         * by the sync adapter's registration (core:data DI), never by sync
         * logic itself; mirrored in the plugin's settings-catalog generator
         * denylist.
         */
        val SyncExcludedKeys: Set<String> = setOf(
            Keys.ACTIVE_SERVER_ID,
            Keys.ACTIVE_USER_ID,
            Keys.DEVICE_ID,
        ).map { it.name }.toSet()
    }
}
