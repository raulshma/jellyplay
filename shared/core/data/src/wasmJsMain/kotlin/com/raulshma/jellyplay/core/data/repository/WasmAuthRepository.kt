package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.normalizeServerAddress
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * wasmJs [AuthRepository]: a faithful port of the jvmShared
 * [AuthRepositoryImpl] establishment choreography (connect / authenticate /
 * quick-connect / restore / logout / revoke / user-switch paths) onto the
 * wasm seams — the web shell's landing flow stops hand-mirroring those call
 * orders and drives this repository instead (see WebConnectController).
 *
 * Same backing stores as android/desktop, resolved from the web Koin graph:
 * the OPFS Room database (`webDatabaseModule` + `databaseDaosModule`), the
 * shared [ServerIdentityStore] over the localStorage-backed "user_prefs"
 * DataStore (`datastoreCommonModule` + `webDatastoreModule`), the Ktor wasm
 * API clients (`networkWasmModule`), and the shared application scope.
 *
 * DECLARED DIVERGENCES vs [AuthRepositoryImpl] (every one is a platform
 * fact, not a semantic choice — call orders, failure classification and
 * state transitions are ported verbatim):
 *
 *  - **[AuthRepository] only, NOT [RealtimeConnection]** — the web has no
 *    realtime transport today: `JellyfinWebSocketClient` is jvmShared
 *    (OkHttp websocket) and no wasm counterpart exists, so the socket seam
 *    stays unimplemented and unbound on this platform. The JVM impl backs
 *    both interfaces as one singleton; this class deliberately does not.
 *  - **[userApiClient] is a separate constructor dependency.** The JVM impl
 *    takes one `JellyfinApiClient` that subsumes [UserApiClient]
 *    (`getCurrentUser` drives `storedTokenRejected`/`refreshCurrentUser`);
 *    the wasm graph binds `AuthApiClient` and `UserApiClient` as two Ktor
 *    singles over the SAME shared `AtomicSessionState` (`networkWasmModule`),
 *    so the session they read/write is one despite the two references.
 *  - **[timeSource] is [EpochMillisSource]**, the commonMain clock slice —
 *    the jvmShared `TimeSource` adds a `java.time` surface that cannot cross
 *    into wasmJsMain. The persisted `lastConnected` stamps read the same
 *    epoch-millis clock (`wallNowMillis` on JVM, the identical read the
 *    SystemTimeSource binding performs).
 *  - **The folder-id memoization is a tiny bounded map**, not
 *    `androidx.collection.LruCache` (a JVM-only artifact on the jvmShared
 *    classpath) — same 16-entry bound, same purpose (decode-once for
 *    `enabledFolderIds` JSON), coarse clear-on-overflow instead of LRU
 *    eviction (Kotlin/wasm is single-threaded in this shell and the map only
 *    ever holds per-server folder lists, so the difference is unobservable
 *    memory behavior).
 *  - **Inherited client-side divergences** (declared at the Ktor client,
 *    restated here because they surface through repository calls):
 *    `selectReachableAddress` probes addresses strictly sequentially (the
 *    JVM router's concurrent alternate fan-out is OkHttp-bound), and
 *    `postCapabilities` sends no DeviceProfile.
 *  - **TokenCipher is the web pass-through** (`WebTokenCipher`, bound in
 *    `webDatabaseModule`): tokens persist as plaintext in origin-scoped OPFS
 *    — the browser sandbox is the trust boundary there (see that object's
 *    KDoc; android/desktop keep their keystore/key-file ciphers).
 *
 * The wasmJsTest lane never executes for :shared:core:data (compile-only
 * target), so this port is pinned by source-scan mirror tests in jvmTest —
 * `WasmAuthRepositoryMirrorContractTest` asserts the member surface matches
 * [AuthRepository] exactly (and that no RealtimeConnection member sneaks in).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WasmAuthRepository constructor(
    private val apiClient: AuthApiClient,
    private val userApiClient: UserApiClient,
    private val database: JellyPlayDatabase,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
    private val serverIdentityStore: ServerIdentityStore,
    private val tokenCipher: TokenCipher,
    private val json: Json,
    /**
     * Shared application scope for the hot `stateIn` flows below and the
     * deferred restore-validation pass (the `DatastoreQualifiers.application
     * Scope` binding — same lifetime discipline as the JVM impl; never
     * cancelled for this singleton).
     */
    private val externalScope: CoroutineScope,
    /** Clock seam for the persisted `lastConnected` stamps (see class KDoc). */
    private val timeSource: EpochMillisSource,
) : AuthRepository {

    private companion object {
        /** Bound of the folder-id decode memoization (see class KDoc). */
        private const val FOLDER_IDS_CACHE_CAPACITY = 16

        /**
         * Verbatim from the JVM impl: the network half of session restore
         * must not gate the first real frame on a live round trip (address
         * selection + the token check each wait at most this long inside
         * [restoreSession]; past it the gate releases with the DB-restored
         * session kept and the stage re-runs on the application scope).
         */
        private const val RESTORE_NETWORK_STAGE_TIMEOUT_MS = 2_000L
    }

    /**
     * The folder-id decode memoization — the declared-divergence stand-in for
     * the JVM `LruCache` (see class KDoc). Single-threaded on this shell, so
     * a plain mutable map needs no synchronization.
     */
    private val folderIdsCache = HashMap<String, List<String>>()

    override val servers: Flow<List<ServerInfo>> = serverDao.getAllServers().map { entities ->
        entities.map { it.toServerInfo() }
    }.stateIn(externalScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override val currentServer: Flow<ServerInfo?> = apiClient.currentServer

    override val currentUser: Flow<UserInfo?> = apiClient.currentUser

    /**
     * Derived from the client's ATOMIC session flow — same invariant as the
     * JVM impl: `session` is non-null exactly when a fully established
     * `(server, user)` pair exists, so this never observes the synthetic
     * `(newServer, oldUser)` intermediate a `combine(currentServer,
     * currentUser)` would produce. Never combine the two separate flows back.
     */
    override val isAuthenticated: StateFlow<Boolean> = apiClient.session
        .map { it != null }
        .stateIn(externalScope, SharingStarted.WhileSubscribed(5_000), false)

    override val currentServerUsers: StateFlow<List<UserInfo>> =
        apiClient.currentServer.flatMapLatest { server ->
            server?.id?.let { sid ->
                userDao.getUsersForServer(sid).map { list ->
                    list.map { it.toUserInfo(server.address) }
                }
            } ?: flowOf(emptyList())
        }.stateIn(externalScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun addServer(address: String): Result<ServerInfo> {
        return apiClient.connectToServer(address).onSuccess { serverInfo ->
            serverDao.insertServer(
                ServerEntity(
                    id = serverInfo.id,
                    name = serverInfo.name,
                    address = serverInfo.address,
                )
            )
        }
    }

    override suspend fun probeServer(address: String): Result<ServerInfo> =
        apiClient.getServerInfo(address)

    override suspend fun removeServer(serverId: String) {
        database.withTransaction {
            userDao.deleteUsersForServer(serverId)
            serverDao.deleteServerById(serverId)
        }
    }

    override suspend fun switchServer(serverId: String): Result<Unit> = runCatchingRethrowingCancellation {
        val serverEntity = serverDao.getServerById(serverId) ?: return Result.success(Unit)
        // The remembered user: the most recently connected one, else the
        // server row's quick-sign-in user. Resolution stays here — it is
        // switchServer's one genuine divergence from switchUser.
        val userEntity = userDao.getMostRecentUserForServer(serverId)
            ?: serverEntity.userId?.let { userDao.getUserById(it) }
        // `return` (non-local through the inline wrapper) is load-bearing:
        // as a trailing expression the helper's Result would be Unit-coerced
        // by the wrapper's T = Unit inference and a rejected token would
        // surface as success.
        return adoptPersistedSession(serverEntity, userEntity, caller = "switchServer") { remembered ->
            serverDao.updateServer(serverEntity.copy(lastConnected = timeSource.nowEpochMillis()))
            userDao.updateUser(remembered.copy(lastConnected = timeSource.nowEpochMillis()))
        }
    }

    override suspend fun addServerAddress(serverId: String, address: String): Result<Unit> = runCatchingRethrowingCancellation {
        val normalizedAddress = normalizeServerAddress(address)
        val serverEntity = serverDao.getServerById(serverId)
            ?: return Result.failure(Exception("Server not found"))
        if (serverEntity.address == normalizedAddress) {
            return Result.failure(Exception("Address is already the primary address"))
        }
        val currentAlternates = serverEntity.decodedAlternateAddresses()
        if (normalizedAddress in currentAlternates) {
            return Result.failure(Exception("Address is already an alternate"))
        }
        val info = apiClient.getServerInfo(normalizedAddress).getOrElse {
            return Result.failure(Exception("Could not connect to server at $normalizedAddress"))
        }
        if (info.id != serverId) {
            return Result.failure(Exception("This address points to a different server"))
        }
        val updated = currentAlternates + normalizedAddress
        serverDao.updateServer(
            serverEntity.copy(alternateAddresses = json.encodeToString(updated))
        )
    }

    override suspend fun removeServerAddress(serverId: String, address: String): Result<Unit> = runCatchingRethrowingCancellation {
        val serverEntity = serverDao.getServerById(serverId)
            ?: return Result.failure(Exception("Server not found"))
        val currentAlternates = serverEntity.decodedAlternateAddresses()
        val updated = currentAlternates - address
        serverDao.updateServer(
            serverEntity.copy(
                alternateAddresses = if (updated.isEmpty()) null else json.encodeToString(updated)
            )
        )
    }

    override suspend fun switchServerAddress(serverId: String, address: String): Result<Unit> = runCatchingRethrowingCancellation {
        val serverEntity = serverDao.getServerById(serverId)
            ?: return Result.failure(Exception("Server not found"))
        // No scheme defaulting here, unlike [normalizeServerAddress]: the
        // address must match a stored (already-schemed) alternate as written.
        val normalizedAddress = address.trim().trimEnd('/')
        val currentAlternates = serverEntity.decodedAlternateAddresses()
        if (normalizedAddress !in currentAlternates) {
            return Result.failure(Exception("Address not found in alternate addresses"))
        }
        val oldPrimary = serverEntity.address
        val newAlternates = (currentAlternates - normalizedAddress) + oldPrimary
        serverDao.updateServer(
            serverEntity.copy(
                address = normalizedAddress,
                alternateAddresses = json.encodeToString(newAlternates),
            )
        )
        val updatedServer = serverEntity.copy(
            address = normalizedAddress,
            alternateAddresses = json.encodeToString(newAlternates),
        ).toServerInfo()
        apiClient.setServer(updatedServer)
        val currentUser = apiClient.currentUser.first()
        if (currentUser != null) {
            apiClient.setUser(currentUser.copy(serverAddress = normalizedAddress))
        }
    }

    override suspend fun login(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> {
        val normalizedAddress = normalizeServerAddress(serverAddress)
        val serverFromDb = serverDao.getServerByAddress(normalizedAddress)

        val existingServerInfo = serverFromDb?.toServerInfo()

        return if (existingServerInfo != null) {
            apiClient.authenticateUser(existingServerInfo, username, password)
        } else {
            // The wasm address overload falls back to connectToServer when no
            // server is configured — the same engine-side connect+authenticate
            // the JVM path performs for an unknown address (one extra probe
            // round trip on a first sign-in; the landing pane's probe is pure
            // by contract and must stay that way).
            apiClient.authenticateUser(serverAddress, username, password)
        }.onSuccess { user ->
            val server = apiClient.currentServer.first()
            if (server != null) {
                persistSession(server, user, username)
            }
        }
    }

    override suspend fun isQuickConnectEnabled(): Result<Boolean> {
        return apiClient.isQuickConnectEnabled()
    }

    override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> {
        return apiClient.initiateQuickConnect()
    }

    override suspend fun pollQuickConnect(secret: String): Result<QuickConnectState> {
        return apiClient.getQuickConnectState(secret)
    }

    override suspend fun loginWithQuickConnect(
        serverAddress: String,
        secret: String,
    ): Result<UserInfo> {
        val normalizedAddress = normalizeServerAddress(serverAddress)
        val serverFromDb = serverDao.getServerByAddress(normalizedAddress)

        val existingServerInfo = serverFromDb?.toServerInfo()

        val serverInfo = existingServerInfo
            ?: apiClient.currentServer.first()
            ?: return Result.failure(Exception("Not connected to server"))

        return apiClient.authenticateWithQuickConnect(serverInfo, secret).onSuccess { user ->
            val server = apiClient.currentServer.first()
            if (server != null) {
                persistSession(server, user)
            }
        }
    }

    override suspend fun authorizeQuickConnect(code: String): Result<Boolean> =
        apiClient.authorizeQuickConnect(code)

    override suspend fun restoreSession(): Result<Unit> = runCatchingRethrowingCancellation {
        val serverId: String? = serverIdentityStore.activeServerId.first()
        val userId: String? = serverIdentityStore.activeUserId.first()
        if (serverId != null && userId != null) {
            val serverEntity = serverDao.getServerById(serverId)
            if (serverEntity == null) {
                // Don't log the raw server/user GUIDs — they survive into
                // release builds and can be used for cross-session correlation.
                Log.w("WasmAuthRepository", "restoreSession: server not found in DB")
                serverIdentityStore.setActiveSession("", "")
                return@runCatchingRethrowingCancellation
            }
            val server = serverEntity.toServerInfo()
            apiClient.setServer(server)
            // Address failover rationale lives on
            // [selectReachableAddressDefensively] (sequential probes on wasm —
            // the declared client divergence). Bounded wait exactly like the
            // JVM pass; on timeout the gate below releases on the primary
            // address and selection re-runs off the gate in the deferred pass.
            val addressSelected = tryRestoreNetworkStage {
                selectReachableAddressDefensively()
            }

            val userEntity = userDao.getUserById(userId)
            if (userEntity == null) {
                Log.w("WasmAuthRepository", "restoreSession: user not found in DB")
                serverIdentityStore.setActiveUser("")
                return@runCatchingRethrowingCancellation
            }
            val token = tokenCipher.decrypt(userEntity.accessToken)
            if (token != null) {
                apiClient.setUser(
                    UserInfo(
                        id = userId,
                        name = userEntity.name,
                        serverAddress = server.address,
                        accessToken = token,
                        serverId = serverId,
                        isAdmin = userEntity.isAdmin,
                        canDeleteContent = userEntity.canDeleteContent,
                        maxParentalAgeRating = userEntity.maxParentalAgeRating,
                        primaryImageTag = userEntity.primaryImageTag,
                        enabledFolderIds = userEntity.enabledFolderIds?.let {
                            try {
                                json.decodeFromString<List<String>>(it)
                            } catch (_: Exception) { emptyList() }
                        } ?: emptyList(),
                    )
                )
                // The token check is a live authenticated GET — bound it
                // the same way (the wasm getCurrentUser rides the same
                // shared session). On timeout the restored session stands
                // exactly as a successful (or non-401-failed) validation
                // leaves it, so the first-frame gate releases with the
                // session kept.
                val validationCompleted = tryRestoreNetworkStage {
                    validateRestoredSession()
                }
                if (!addressSelected || !validationCompleted) {
                    launchDeferredRestoreValidation(addressSelected, validationCompleted)
                }
            } else {
                Log.w("WasmAuthRepository", "restoreSession: no access token for active user")
            }
        }
    }.onFailure { e ->
        Log.e("WasmAuthRepository", "restoreSession failed", e)
    }

    /**
     * Runs one restore-time network stage bounded by
     * [RESTORE_NETWORK_STAGE_TIMEOUT_MS]. Returns whether the stage completed
     * within the bound — false on timeout (the stage is abandoned mid-flight),
     * leaving the caller to re-run it in the deferred pass.
     */
    private suspend fun tryRestoreNetworkStage(stage: suspend () -> Unit): Boolean =
        withTimeoutOrNull(RESTORE_NETWORK_STAGE_TIMEOUT_MS) {
            stage()
            true
        } ?: false

    /**
     * Deferred validation pass on the application scope — never cancelled for
     * this singleton (verbatim rationale from the JVM impl: a definitive 401
     * here funnels through validateRestoredSession's disconnect +
     * clearSession, the same teardown the shell observes through
     * isAuthenticated flipping false).
     */
    private fun launchDeferredRestoreValidation(addressSelected: Boolean, validationCompleted: Boolean) {
        externalScope.launch {
            if (!addressSelected) {
                withTimeoutOrNull(RESTORE_NETWORK_STAGE_TIMEOUT_MS) {
                    selectReachableAddressDefensively()
                }
            }
            if (!validationCompleted) {
                runCatchingRethrowingCancellation { validateRestoredSession() }
                    .onFailure { e ->
                        Log.w("WasmAuthRepository", "Deferred session validation failed", e)
                    }
            }
        }
    }

    /**
     * Restore-time guard on top of [storedTokenRejected]: a token the server
     * definitively rejects (401) tears the freshly restored session down, so
     * the shell lands on the login screen instead of a cached ghost home.
     */
    private suspend fun validateRestoredSession() {
        if (storedTokenRejected()) {
            Log.w("WasmAuthRepository", "restoreSession: server rejected the stored token (401) — clearing session")
            apiClient.disconnect()
            serverIdentityStore.clearSession()
        }
    }

    /** The failure surfaced when a stored token is rejected (HTTP 401). */
    private fun sessionExpired(): ApiException = ApiException(
        isRetryable = false,
        httpCode = 401,
        isAccessDenied = true,
        message = "Authentication required. Please sign in again.",
    )

    /**
     * One cheap round-trip after adopting a persisted token (verbatim
     * rationale from the JVM impl: only HTTP 401 counts as rejection — 403
     * means authenticated-but-forbidden, network/5xx failures keep the
     * session so offline use is unchanged). Sourced from [userApiClient] —
     * the declared two-reference divergence over the one shared
     * `AtomicSessionState`.
     */
    private suspend fun storedTokenRejected(): Boolean {
        val rejected = runCatchingRethrowingCancellation { userApiClient.getCurrentUser().exceptionOrNull() }
            .getOrNull() as? ApiException
            ?: return false
        return rejected.httpCode == 401
    }

    /**
     * Address failover shared by every session-adoption path (verbatim from
     * the JVM impl: prefer the primary when reachable, else fail over to an
     * alternate; a probe crash must not fail the adoption; a cancelled caller
     * must still cancel). Sequential probing is the wasm client's declared
     * divergence (class KDoc).
     */
    private suspend fun selectReachableAddressDefensively() {
        runCatchingRethrowingCancellation { apiClient.selectReachableAddress() }
    }

    /**
     * The shared session-establishment choreography behind [switchServer]
     * and [switchUser] — ported verbatim from the JVM impl (the steps both
     * callers used to hand-copy):
     *
     *  1. `apiClient.disconnect()` — drop any live session first (one atomic
     *     null publish, never a synthetic `(newServer, oldUser)`
     *     intermediate).
     *  2. `setServer` with the entity's [ServerInfo] projection.
     *  3. Best-effort address failover ([selectReachableAddressDefensively]).
     *  4. `setUser` with the entity's token [UserInfo] projection.
     *  5. The stored-token 401 guard: a token the server definitively
     *     rejects must not establish a session — see
     *     [storedTokenRejected]. A rejected token disconnects and fails the
     *     switch with [sessionExpired] so the login screen asks for
     *     credentials instead of bouncing through a cached ghost home.
     *     [caller] only names the site in the rejection log line.
     *  6. `serverIdentityStore.setActiveSession(server.id, user.userId)` +
     *     the `lastConnected` stamps in ONE `database.withTransaction` —
     *     the stamps are the callers' one genuine divergence, so
     *     [persistStamps] receives the resolved non-null user and runs
     *     inside the transaction (switchServer stamps both rows; switchUser
     *     additionally re-binds the server row's quick-sign-in
     *     `userId`/`accessToken`).
     *
     * A null [userEntity] (switchServer on a server with no remembered
     * user) still runs steps 1-3 — the client lands pointed at the new
     * server — and then stops: no user to adopt, nothing to stamp, success.
     *
     * [restoreSession] deliberately does NOT ride this spine (verbatim from
     * the JVM impl: its network stages are timeout-bounded BETWEEN the
     * steps, its 401 reaction is a teardown rather than a failed Result,
     * and it neither re-writes the identity store nor stamps
     * `lastConnected`); it shares only the failover body.
     */
    private suspend fun adoptPersistedSession(
        serverEntity: ServerEntity,
        userEntity: UserEntity?,
        caller: String,
        persistStamps: suspend (UserEntity) -> Unit,
    ): Result<Unit> {
        val server = serverEntity.toServerInfo()
        apiClient.disconnect()
        apiClient.setServer(server)
        selectReachableAddressDefensively()
        if (userEntity == null) return Result.success(Unit)
        apiClient.setUser(userEntity.toUserInfo(server.address))
        // Guard against re-arming a revoked token: without this, tapping a
        // saved server or user adopts the dead stored token, the app lands on
        // a cached ghost home whose live calls all 401, and the next cold
        // start clears the session again — a server-screen/home bounce loop.
        if (storedTokenRejected()) {
            Log.w("WasmAuthRepository", "$caller: stored token for ${userEntity.name} rejected (401)")
            apiClient.disconnect()
            return Result.failure(sessionExpired())
        }
        serverIdentityStore.setActiveSession(server.id, userEntity.userId)
        database.withTransaction { persistStamps(userEntity) }
        return Result.success(Unit)
    }

    override suspend fun refreshCurrentUser(): Result<UserInfo> {
        val cached = apiClient.currentUser.first()
            ?: return Result.failure(Exception("No active user to refresh"))

        val result = userApiClient.getCurrentUser()
        return result.fold(
            onSuccess = { managed ->
                val refreshed = cached.copy(
                    isAdmin = managed.policy.isAdministrator,
                    canDeleteContent = managed.policy.enableContentDeletion,
                )
                apiClient.setUser(refreshed)
                persistRefreshedFlags(refreshed)
                Result.success(refreshed)
            },
            onFailure = { e ->
                // 401/403 = the server has revoked/demoted this user. Clear
                // admin status so the admin area is blocked immediately; the
                // server remains the ultimate authority on the next call.
                if (e is ApiException && e.isAccessDenied) {
                    val demoted = cached.copy(isAdmin = false, canDeleteContent = false)
                    apiClient.setUser(demoted)
                    persistRefreshedFlags(demoted)
                    Result.success(demoted)
                } else {
                    // Transient/non-access failure — keep the cached value so a
                    // flaky network can't lock an admin out. The server still
                    // 403s on the real privileged call as a backstop.
                    Result.failure(e)
                }
            },
        )
    }

    /**
     * Persists just the refreshed permission flags ([UserEntity.isAdmin] and
     * [UserEntity.canDeleteContent]) for the active user without rewriting the
     * whole entity (avoids clobbering the token, image tag, etc.).
     */
    private suspend fun persistRefreshedFlags(user: UserInfo) {
        val existing = userDao.getUserById(user.id) ?: return
        if (existing.isAdmin == user.isAdmin && existing.canDeleteContent == user.canDeleteContent) return
        userDao.updateUser(
            existing.copy(
                isAdmin = user.isAdmin,
                canDeleteContent = user.canDeleteContent,
            )
        )
    }

    override suspend fun logout() {
        apiClient.disconnect()
        // Clear only the active session selection — preserve the stable
        // device id and all user preferences so re-login does not orphan
        // server-side sessions or reset settings.
        serverIdentityStore.clearSession()
    }

    override suspend fun revokeServerSession() {
        val currentUserId = apiClient.currentUser.first()?.id
        try {
            apiClient.revokeServerSession()
        } catch (_: Exception) {
            // Even if the server call fails, we should still clear local state
        }
        if (currentUserId != null) {
            removeUser(currentUserId)
        }
        apiClient.disconnect()
        serverIdentityStore.clearSession()
    }

    override suspend fun switchUser(userId: String): Result<Unit> = runCatchingRethrowingCancellation {
        val userEntity = userDao.getUserById(userId) ?: return Result.success(Unit)
        val server = serverDao.getServerById(userEntity.serverId) ?: return Result.success(Unit)
        // Same load-bearing `return` as switchServer — see its comment.
        return adoptPersistedSession(server, userEntity, caller = "switchUser") { remembered ->
            // Stamp divergence vs switchServer: switching user also re-binds
            // the server row's quick-sign-in pointer (userId + accessToken)
            // to the newly adopted user.
            userDao.updateUser(remembered.copy(lastConnected = timeSource.nowEpochMillis()))
            serverDao.updateServer(
                server.copy(
                    userId = remembered.userId,
                    accessToken = remembered.accessToken,
                    lastConnected = timeSource.nowEpochMillis(),
                )
            )
        }
    }

    override suspend fun removeUser(userId: String) {
        database.withTransaction {
            userDao.deleteUserById(userId)
            // Single bulk UPDATE — same effect as the JVM pass: every server
            // row bound to this user has its userId + accessToken cleared.
            serverDao.clearUserFromServers(userId)
        }
        val currentUserId = apiClient.currentUser.first()?.id
        if (currentUserId == userId) {
            apiClient.disconnect()
            serverIdentityStore.setActiveUser("")
        }
    }

    override suspend fun getUsersForServer(serverId: String): List<UserInfo> {
        // Use the one-shot DAO query instead of `.first()` on the Flow, which
        // would cancel a fresh Flow emission (full query cost) on every call.
        return userDao.getUsersForServerOnce(serverId).map { it.toUserInfo() }
    }

    override suspend fun postCapabilities(): Result<Unit> = apiClient.postCapabilities()

    private suspend fun persistSession(server: ServerInfo, user: UserInfo, fallbackUsername: String = "") {
        val existingServer = serverDao.getServerById(server.id)
        val preservedAlternateAddresses = existingServer?.alternateAddresses
        if (existingServer == null) {
            serverDao.insertServer(
                ServerEntity(
                    id = server.id,
                    name = server.name,
                    address = server.address,
                )
            )
        }
        val userEntity = UserEntity(
            userId = user.id,
            serverId = server.id,
            name = user.name.ifBlank { fallbackUsername },
            accessToken = tokenCipher.encrypt(user.accessToken) ?: "",
            primaryImageTag = user.primaryImageTag,
            maxParentalAgeRating = user.maxParentalAgeRating,
            isAdmin = user.isAdmin,
            canDeleteContent = user.canDeleteContent,
            enabledFolderIds = json.encodeToString(user.enabledFolderIds),
            lastConnected = timeSource.nowEpochMillis(),
        )
        database.withTransaction {
            userDao.insertUser(userEntity)
            serverDao.updateServer(
                ServerEntity(
                    id = server.id,
                    name = server.name,
                    address = server.address,
                    userId = user.id,
                    accessToken = tokenCipher.encrypt(user.accessToken),
                    lastConnected = timeSource.nowEpochMillis(),
                    alternateAddresses = preservedAlternateAddresses,
                )
            )
        }
        serverIdentityStore.setActiveSession(server.id, user.id)
    }

    private fun ServerEntity.toServerInfo() = ServerInfo(
        id = id,
        name = name,
        address = address,
        userId = userId,
        accessToken = tokenCipher.decrypt(accessToken),
        isConnected = accessToken != null,
        alternateAddresses = alternateAddresses?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
        } ?: emptyList(),
    )

    /**
     * The address-management reads' decode: stored alternates JSON → list,
     * undecodable slots read empty. [toServerInfo]'s mirror decode stays
     * hand-rolled — it deliberately uses plain `runCatching` (no
     * cancellation rethrow) as ported.
     */
    private suspend fun ServerEntity.decodedAlternateAddresses(): List<String> =
        alternateAddresses?.let {
            runCatchingRethrowingCancellation { json.decodeFromString<List<String>>(it) }
                .getOrDefault(emptyList())
        } ?: emptyList()

    private fun UserEntity.toUserInfo(serverAddress: String = "") = UserInfo(
        id = userId,
        name = name,
        serverAddress = serverAddress,
        accessToken = tokenCipher.decrypt(accessToken) ?: "",
        serverId = serverId,
        isAdmin = isAdmin,
        canDeleteContent = canDeleteContent,
        maxParentalAgeRating = maxParentalAgeRating,
        primaryImageTag = primaryImageTag,
        enabledFolderIds = enabledFolderIds?.let { raw ->
            folderIdsCache[raw] ?: try {
                json.decodeFromString<List<String>>(raw).also { decoded ->
                    if (folderIdsCache.size >= FOLDER_IDS_CACHE_CAPACITY) folderIdsCache.clear()
                    folderIdsCache[raw] = decoded
                }
            } catch (_: Exception) { emptyList() }
        } ?: emptyList(),
    )
}
