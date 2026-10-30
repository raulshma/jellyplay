package com.raulshma.jellyplay.core.datastore.network

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **network / offline-mode** preference domain: manual +
 * auto offline mode, the manual bandwidth cap, the metered-network behaviour,
 * adaptive-bitrate, data-saver, verbose-logging, the network timeout preset,
 * and the image-cache size + auto-delete toggles.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters, its read projection, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Note:** `incognito_mode_enabled` is intentionally NOT owned here — it
 * belongs to the playback/player domain (`VideoPlayerStore`).
 *
 * **Spec derivation:** every spec-declarable key this store owns is declared
 * exactly once as a row in [NetworkOfflinePreferenceSpecs] (wire name,
 * default, reset category, read/write encoding) and the machinery below is
 * derived from those rows — each [Keys] member rebuilds its row's typed key
 * from the row's wire name, each [read] projection row delegates to its row
 * encoding, the single-key setters and the derived rows of [restore] delegate
 * to the rows' derived writes, and [resetKeysFor] filters the rows by reset
 * category. Every row this domain declares resets under the single legacy
 * `DOWNLOADS_NETWORK` category.
 *
 * **Hand-written residuals (the documented opt-outs — see the spec KDoc):**
 *  - the [HOME_STREAMING_QUALITY] row is the declaration-only
 *    [com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec.string] (the
 *    hand read was the plain `prefs[key] ?: default`): its setter/restore
 *    lines stay hand-written (a declaration-only row has no derived write).
 *  - the self-signed trust-host grant set ([Keys.SELF_SIGNED_TRUST_HOSTS])
 *    has no spec row — a DataStore StringSet slot, and the spec tier's
 *    [com.raulshma.jellyplay.core.datastore.spec.PreferenceStorage] declares
 *    single-value storages only. The key, its read, its read-modify-write
 *    grant/revoke verbs and its `restore` line stay hand-written, and
 *    [resetKeysFor] appends its reset participation beside the rows.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class NetworkOfflineStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [NetworkOfflinePreferenceSpecs] row — the member rebuilds the row's
     * typed key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key
     * of the same name). Kept as a plain object rather than folded into the
     * rows because it is the reflection anchor for the JVM reset-coverage
     * guard — and because it still carries the one hand-residual key the
     * spec tier cannot declare (the trust-host StringSet slot).
     */
    internal object Keys {
        val MANUAL_OFFLINE_ENABLED = booleanPreferencesKey(NetworkOfflinePreferenceSpecs.MANUAL_OFFLINE_ENABLED.keyName)
        val AUTO_OFFLINE_ENABLED = booleanPreferencesKey(NetworkOfflinePreferenceSpecs.AUTO_OFFLINE_ENABLED.keyName)
        val MANUAL_BANDWIDTH_CAP = longPreferencesKey(NetworkOfflinePreferenceSpecs.MANUAL_BANDWIDTH_CAP.keyName)
        val METERED_NETWORK_BEHAVIOR =
            stringPreferencesKey(NetworkOfflinePreferenceSpecs.METERED_NETWORK_BEHAVIOR.keyName)
        val ADAPTIVE_BITRATE_ENABLED =
            booleanPreferencesKey(NetworkOfflinePreferenceSpecs.ADAPTIVE_BITRATE_ENABLED.keyName)
        val DATA_SAVER_ENABLED = booleanPreferencesKey(NetworkOfflinePreferenceSpecs.DATA_SAVER_ENABLED.keyName)
        val VERBOSE_NETWORK_LOGGING =
            booleanPreferencesKey(NetworkOfflinePreferenceSpecs.VERBOSE_NETWORK_LOGGING.keyName)
        val NETWORK_TIMEOUT_PRESET = stringPreferencesKey(NetworkOfflinePreferenceSpecs.NETWORK_TIMEOUT_PRESET.keyName)
        val MAX_CACHE_SIZE_MB = intPreferencesKey(NetworkOfflinePreferenceSpecs.MAX_CACHE_SIZE_MB.keyName)
        val AUTO_DELETE_CACHE = booleanPreferencesKey(NetworkOfflinePreferenceSpecs.AUTO_DELETE_CACHE.keyName)
        /**
         * The one hand-residual key: a DataStore StringSet slot the spec tier
         * cannot declare (no STRING_SET storage — see the class KDoc's
         * opt-outs).
         */
        val SELF_SIGNED_TRUST_HOSTS = stringSetPreferencesKey("self_signed_trust_hosts")
        /**
         * The home-streaming quality stored as the enum NAME ("AUTO" default) — see [NetworkOfflineSlice.homeStreamingQuality].
         * The declaration-only [NetworkOfflinePreferenceSpecs.HOME_STREAMING_QUALITY] row's slot.
         */
        val HOME_NETWORK_STREAMING_QUALITY =
            stringPreferencesKey(NetworkOfflinePreferenceSpecs.HOME_STREAMING_QUALITY.keyName)
    }

    val networkOffline: StateFlow<NetworkOfflineSlice> =
        dataStore.sliceStateFlow(scope, seed = NetworkOfflineSlice(), read = ::read)

    /**
     * Pure read of the network / offline-mode fields from a raw [Preferences]
     * snapshot, each field delegated to its [NetworkOfflinePreferenceSpecs]
     * row encoding (the declaration-only home-quality row via its plain
     * [com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec.readStored] —
     * the exact hand read). Exposed so the facade can fold these into the
     * whole-`UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): NetworkOfflineSlice = NetworkOfflineSlice(
        manualOfflineEnabled = NetworkOfflinePreferenceSpecs.MANUAL_OFFLINE_ENABLED.readFrom(prefs),
        autoOfflineEnabled = NetworkOfflinePreferenceSpecs.AUTO_OFFLINE_ENABLED.readFrom(prefs),
        manualBandwidthCap = NetworkOfflinePreferenceSpecs.MANUAL_BANDWIDTH_CAP.readFrom(prefs),
        homeStreamingQuality = NetworkOfflinePreferenceSpecs.HOME_STREAMING_QUALITY.readStored(prefs),
        meteredNetworkBehavior = NetworkOfflinePreferenceSpecs.METERED_NETWORK_BEHAVIOR.readFrom(prefs),
        adaptiveBitrateEnabled = NetworkOfflinePreferenceSpecs.ADAPTIVE_BITRATE_ENABLED.readFrom(prefs),
        dataSaverEnabled = NetworkOfflinePreferenceSpecs.DATA_SAVER_ENABLED.readFrom(prefs),
        verboseNetworkLogging = NetworkOfflinePreferenceSpecs.VERBOSE_NETWORK_LOGGING.readFrom(prefs),
        networkTimeoutPreset = NetworkOfflinePreferenceSpecs.NETWORK_TIMEOUT_PRESET.readFrom(prefs),
        maxCacheSizeMb = NetworkOfflinePreferenceSpecs.MAX_CACHE_SIZE_MB.readFrom(prefs),
        autoDeleteCache = NetworkOfflinePreferenceSpecs.AUTO_DELETE_CACHE.readFrom(prefs),
        selfSignedTrustHosts = prefs[Keys.SELF_SIGNED_TRUST_HOSTS] ?: emptySet(),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, plain slot — is the row's, not
    // re-declared here); the declaration-only home-quality row keeps its
    // hand-written line, as do the trust-host grant/revoke verbs below.
    // ------------------------------------------------------------------

    suspend fun setManualOffline(enabled: Boolean) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.MANUAL_OFFLINE_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setAutoOfflineEnabled(enabled: Boolean) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.AUTO_OFFLINE_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setManualBandwidthCap(cap: Long) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.MANUAL_BANDWIDTH_CAP.writeTo(it, cap) }
    }

    /**
     * The home-streaming quality as the persisted enum NAME ("AUTO" default —
     * the [com.raulshma.jellyplay.core.model.StreamingQuality] vocabulary).
     * Carried as a raw String so an unknown name from a newer binary degrades
     * to the caller's handling instead of a hard parse failure here. The
     * declaration-only row keeps this hand-written plain slot write (see the
     * class KDoc's opt-outs).
     */
    suspend fun setHomeStreamingQuality(value: String) {
        dataStore.edit { it[Keys.HOME_NETWORK_STREAMING_QUALITY] = value }
    }

    suspend fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.METERED_NETWORK_BEHAVIOR.writeTo(it, behavior) }
    }

    suspend fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.ADAPTIVE_BITRATE_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.DATA_SAVER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setVerboseNetworkLogging(enabled: Boolean) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.VERBOSE_NETWORK_LOGGING.writeTo(it, enabled) }
    }

    suspend fun setNetworkTimeoutPreset(preset: NetworkTimeoutPreset) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.NETWORK_TIMEOUT_PRESET.writeTo(it, preset) }
    }

    suspend fun setMaxCacheSize(sizeMb: Int) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.MAX_CACHE_SIZE_MB.writeTo(it, sizeMb) }
    }

    suspend fun setAutoDeleteCache(enabled: Boolean) {
        dataStore.edit { NetworkOfflinePreferenceSpecs.AUTO_DELETE_CACHE.writeTo(it, enabled) }
    }

    // ------------------------------------------------------------------
    // Self-signed certificate trust (opt-in, per server address) — the
    // hand-residual StringSet slot the spec tier cannot declare (see the
    // class KDoc's opt-outs); the read-modify-write verbs stay hand-written.
    // ------------------------------------------------------------------

    /**
     * Grants TLS trust for one server address. Entries are stored in the
     * canonical `scheme://host[:port]` form (no trailing slash) — the same
     * shape [com.raulshma.jellyplay.core.network.failover.ServerAddressRouter]
     * uses for endpoint addresses. Idempotent; trimming keeps a grant written
     * from user-typed input aligned with the matcher's parsing.
     */
    suspend fun addSelfSignedTrustHost(entry: String) {
        val normalized = entry.trim().trimEnd('/')
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[Keys.SELF_SIGNED_TRUST_HOSTS] =
                (prefs[Keys.SELF_SIGNED_TRUST_HOSTS] ?: emptySet()) + normalized
        }
    }

    /** Revokes TLS trust for one server address. Idempotent. */
    suspend fun removeSelfSignedTrustHost(entry: String) {
        val normalized = entry.trim().trimEnd('/')
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.SELF_SIGNED_TRUST_HOSTS] ?: return@edit
            prefs[Keys.SELF_SIGNED_TRUST_HOSTS] = current - normalized
        }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. This is the network/offline subset of the
     * legacy `DOWNLOADS_NETWORK` reset category — the download keys now belong
     * to [com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore].
     * [Keys.SELF_SIGNED_TRUST_HOSTS] is included: trust grants are
     * security-relevant, so every reset lane that touches the network domain
     * clears them (fail closed — a wiped grant simply re-prompts on the next
     * TLS failure).
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [NetworkOfflinePreferenceSpecs] rows whose declared
     * reset category matches, mapped to their derived keys, plus the
     * hand-residual trust-host set key for its `DOWNLOADS_NETWORK` lane (see
     * the class KDoc's opt-outs). The network/offline keys (plus the
     * image-cache size + auto delete toggles) all sit in the legacy
     * `DOWNLOADS_NETWORK` reset category.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        NetworkOfflinePreferenceSpecs.resetKeysFor(category) +
            if (category == PreferenceResetCategory.DOWNLOADS_NETWORK) {
                listOf(Keys.SELF_SIGNED_TRUST_HOSTS)
            } else {
                emptyList()
            }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore — the derived rows via their row's derived write (the same
     * encoding the row reads with), the declaration-only home-quality row and
     * the hand-residual trust-host set via their hand-written lines.
     */
    suspend fun restore(slice: NetworkOfflineSlice) {
        dataStore.edit { prefs ->
            NetworkOfflinePreferenceSpecs.MANUAL_OFFLINE_ENABLED.writeTo(prefs, slice.manualOfflineEnabled)
            NetworkOfflinePreferenceSpecs.AUTO_OFFLINE_ENABLED.writeTo(prefs, slice.autoOfflineEnabled)
            NetworkOfflinePreferenceSpecs.MANUAL_BANDWIDTH_CAP.writeTo(prefs, slice.manualBandwidthCap)
            prefs[Keys.HOME_NETWORK_STREAMING_QUALITY] = slice.homeStreamingQuality
            NetworkOfflinePreferenceSpecs.METERED_NETWORK_BEHAVIOR.writeTo(prefs, slice.meteredNetworkBehavior)
            NetworkOfflinePreferenceSpecs.ADAPTIVE_BITRATE_ENABLED.writeTo(prefs, slice.adaptiveBitrateEnabled)
            NetworkOfflinePreferenceSpecs.DATA_SAVER_ENABLED.writeTo(prefs, slice.dataSaverEnabled)
            NetworkOfflinePreferenceSpecs.VERBOSE_NETWORK_LOGGING.writeTo(prefs, slice.verboseNetworkLogging)
            NetworkOfflinePreferenceSpecs.NETWORK_TIMEOUT_PRESET.writeTo(prefs, slice.networkTimeoutPreset)
            NetworkOfflinePreferenceSpecs.MAX_CACHE_SIZE_MB.writeTo(prefs, slice.maxCacheSizeMb)
            NetworkOfflinePreferenceSpecs.AUTO_DELETE_CACHE.writeTo(prefs, slice.autoDeleteCache)
            prefs[Keys.SELF_SIGNED_TRUST_HOSTS] = slice.selfSignedTrustHosts
        }
    }
}

/**
 * The network / offline-mode preference slice. Plain data class.
 * Every default IS its [NetworkOfflinePreferenceSpecs] row's declared
 * default — read off the row via a plain accessor, so a preference's default
 * is stated exactly once, in its spec, and cannot drift between the spec
 * (what an absent key reads) and the slice (what a fresh in-memory instance
 * serves). The spec-default equality is pinned field-for-field by
 * `NetworkOfflineSpecDerivationTest`.
 */
@Immutable
@Serializable
data class NetworkOfflineSlice(
    val manualOfflineEnabled: Boolean = NetworkOfflinePreferenceSpecs.MANUAL_OFFLINE_ENABLED.default,
    val autoOfflineEnabled: Boolean = NetworkOfflinePreferenceSpecs.AUTO_OFFLINE_ENABLED.default,
    val manualBandwidthCap: Long = NetworkOfflinePreferenceSpecs.MANUAL_BANDWIDTH_CAP.default,
    /**
     * The home-streaming quality as the persisted enum NAME
     * ([com.raulshma.jellyplay.core.model.StreamingQuality] vocabulary).
     * Default "AUTO".
     */
    val homeStreamingQuality: String = NetworkOfflinePreferenceSpecs.HOME_STREAMING_QUALITY.default,
    val meteredNetworkBehavior: MeteredNetworkBehavior =
        NetworkOfflinePreferenceSpecs.METERED_NETWORK_BEHAVIOR.default,
    val adaptiveBitrateEnabled: Boolean = NetworkOfflinePreferenceSpecs.ADAPTIVE_BITRATE_ENABLED.default,
    val dataSaverEnabled: Boolean = NetworkOfflinePreferenceSpecs.DATA_SAVER_ENABLED.default,
    val verboseNetworkLogging: Boolean = NetworkOfflinePreferenceSpecs.VERBOSE_NETWORK_LOGGING.default,
    val networkTimeoutPreset: NetworkTimeoutPreset = NetworkOfflinePreferenceSpecs.NETWORK_TIMEOUT_PRESET.default,
    val maxCacheSizeMb: Int = NetworkOfflinePreferenceSpecs.MAX_CACHE_SIZE_MB.default,
    val autoDeleteCache: Boolean = NetworkOfflinePreferenceSpecs.AUTO_DELETE_CACHE.default,
    /**
     * Server addresses granted self-signed TLS trust, canonical
     * `scheme://host[:port]` form. Empty by default (opt-in only). The one
     * hand-residual slot — no spec row (see the store KDoc's opt-outs).
     */
    val selfSignedTrustHosts: Set<String> = emptySet(),
)
