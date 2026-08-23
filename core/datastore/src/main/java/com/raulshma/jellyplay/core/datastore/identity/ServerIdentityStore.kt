package com.raulshma.jellyplay.core.datastore.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

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
 */
@Singleton
class ServerIdentityStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope
    private val sharedPrefs: Flow<Preferences> = dataStore.data

    val activeServerId: Flow<String?> =
        sharedPrefs.map { it[Keys.ACTIVE_SERVER_ID] }.distinctUntilChanged()

    val activeUserId: Flow<String?> =
        sharedPrefs.map { it[Keys.ACTIVE_USER_ID] }.distinctUntilChanged()

    val deviceId: Flow<String?> =
        sharedPrefs.map { it[Keys.DEVICE_ID] }.distinctUntilChanged()

    /**
     * Combined snapshot of all three identity fields. Consumers that need to
     * react to *any* session change (e.g. to re-fetch user-scoped data) collect
     * this instead of three separate flows.
     */
    val identity: kotlinx.coroutines.flow.StateFlow<ServerIdentity> =
        combine(activeServerId, activeUserId, deviceId) { serverId, userId, devId ->
            ServerIdentity(serverId, userId, devId)
        }.stateIn(scope, SharingStarted.Eagerly, ServerIdentity())

    /** Lazily persists a stable device id on first access; idempotent thereafter. */
    suspend fun ensureDeviceId(): String {
        var id: String? = null
        dataStore.edit { prefs ->
            id = prefs[Keys.DEVICE_ID]
                ?: java.util.UUID.randomUUID().toString().also { prefs[Keys.DEVICE_ID] = it }
        }
        return id ?: error("deviceId could not be resolved")
    }

    suspend fun setActiveServer(serverId: String) {
        dataStore.edit { it[Keys.ACTIVE_SERVER_ID] = serverId }
    }

    suspend fun setActiveUser(userId: String) {
        dataStore.edit { it[Keys.ACTIVE_USER_ID] = userId }
    }

    /**
     * Sets the active server and user in a single DataStore edit. Two back-to-
     * back `setActiveServer` + `setActiveUser` calls each opened their own
     * `edit {}` → 2 disk reads + 2 atomic writes + 2 downstream re-emissions.
     * Batching them halves the I/O and the re-derivation cascade.
     */
    suspend fun setActiveSession(serverId: String, userId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SERVER_ID] = serverId
            prefs[Keys.ACTIVE_USER_ID] = userId
        }
    }

    /**
     * Active user id read straight from a [Preferences] snapshot — for callers
     * already holding one, e.g. inside a `DataStore.edit` transform where
     * collecting [activeUserId] would re-enter the DataStore. A blank id (or
     * absence, pre-login) reads as null. In-module keying helper for per-user
     * stores (e.g. `HomeDiscoveryStore`'s `u_<userId>::` namespace).
     */
    fun activeUserIdIn(prefs: Preferences): String? =
        prefs[Keys.ACTIVE_USER_ID]?.takeIf { it.isNotBlank() }

    /** Drops the active server + user but keeps the device id and all prefs. */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_SERVER_ID)
            prefs.remove(Keys.ACTIVE_USER_ID)
        }
    }

    private object Keys {
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}

/**
 * Snapshot of the active session identity. Plain data class so the datastore
 * module stays Compose-free; consumers in feature modules can wrap it in their
 * own `@Immutable` UI-state types if needed.
 */
data class ServerIdentity(
    val activeServerId: String? = null,
    val activeUserId: String? = null,
    val deviceId: String? = null,
)
