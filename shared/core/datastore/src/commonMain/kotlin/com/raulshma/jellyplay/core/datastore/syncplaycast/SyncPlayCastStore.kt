package com.raulshma.jellyplay.core.datastore.syncplaycast

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **SyncPlay + casting + DVR** preference domain: the
 * SyncPlay join behaviour + sync tolerance + auto-accept invites, the default
 * casting strategy + background-casting + preferred renderer, and the DVR
 * pre/post padding + recording quality.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters, its read projection, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [SyncPlayCastPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding (or
 * [PreferenceSpec.readStored] for the declaration-only string rows), the
 * encoded single-key setters and [restore] rows delegate to the rows' derived
 * writes, and [resetKeysFor] filters the rows by reset category.
 *
 * **Hand-written residuals (the documented opt-outs — the declaration-only
 * string rows have no derived write):** [setPreferredRenderer] writes the
 * renderer or REMOVES the key for null, [setDvrRecordingQuality] keeps its
 * hand write line, and [restore]'s `preferred_renderer` row keeps the same
 * nullable guard — a removal / a guard cannot be a row encoding.
 *
 * **Note:** `live_stream_option` is intentionally NOT owned here — it belongs to
 * [com.raulshma.jellyplay.core.datastore.playback.PlaybackStore].
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class SyncPlayCastStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [SyncPlayCastPreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key of
     * the same name). Kept as a plain object rather than folded into the rows
     * because it is the reflection anchor for the JVM reset-coverage guard.
     */
    internal object Keys {
        val SYNC_PLAY_JOIN_BEHAVIOR = stringPreferencesKey(SyncPlayCastPreferenceSpecs.SYNC_PLAY_JOIN_BEHAVIOR.keyName)
        val SYNC_PLAY_TOLERANCE_MS = longPreferencesKey(SyncPlayCastPreferenceSpecs.SYNC_PLAY_TOLERANCE_MS.keyName)
        val SYNC_PLAY_AUTO_ACCEPT_INVITES = booleanPreferencesKey(SyncPlayCastPreferenceSpecs.SYNC_PLAY_AUTO_ACCEPT_INVITES.keyName)
        val DEFAULT_CASTING_STRATEGY = stringPreferencesKey(SyncPlayCastPreferenceSpecs.DEFAULT_CASTING_STRATEGY.keyName)
        val BACKGROUND_CASTING_ENABLED = booleanPreferencesKey(SyncPlayCastPreferenceSpecs.BACKGROUND_CASTING_ENABLED.keyName)
        val PREFERRED_RENDERER = stringPreferencesKey(SyncPlayCastPreferenceSpecs.PREFERRED_RENDERER.keyName)
        val DVR_PRE_PADDING_MINUTES = intPreferencesKey(SyncPlayCastPreferenceSpecs.DVR_PRE_PADDING_MINUTES.keyName)
        val DVR_POST_PADDING_MINUTES = intPreferencesKey(SyncPlayCastPreferenceSpecs.DVR_POST_PADDING_MINUTES.keyName)
        val DVR_RECORDING_QUALITY = stringPreferencesKey(SyncPlayCastPreferenceSpecs.DVR_RECORDING_QUALITY.keyName)
    }

    val syncPlayCast: StateFlow<SyncPlayCastSlice> =
        dataStore.sliceStateFlow(scope, seed = SyncPlayCastSlice(), read = ::read)

    /**
     * Pure read of the SyncPlay + casting + DVR fields from a raw [Preferences]
     * snapshot, each field delegated to its [SyncPlayCastPreferenceSpecs] row
     * encoding. Exposed so the facade can fold these into the
     * whole-`UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): SyncPlayCastSlice = SyncPlayCastSlice(
        syncPlayJoinBehavior = SyncPlayCastPreferenceSpecs.SYNC_PLAY_JOIN_BEHAVIOR.readFrom(prefs),
        syncPlayToleranceMs = SyncPlayCastPreferenceSpecs.SYNC_PLAY_TOLERANCE_MS.readFrom(prefs),
        syncPlayAutoAcceptInvites = SyncPlayCastPreferenceSpecs.SYNC_PLAY_AUTO_ACCEPT_INVITES.readFrom(prefs),
        defaultCastingStrategy = SyncPlayCastPreferenceSpecs.DEFAULT_CASTING_STRATEGY.readFrom(prefs),
        backgroundCastingEnabled = SyncPlayCastPreferenceSpecs.BACKGROUND_CASTING_ENABLED.readFrom(prefs),
        preferredRenderer = SyncPlayCastPreferenceSpecs.PREFERRED_RENDERER.readStored(prefs),
        dvrPrePaddingMinutes = SyncPlayCastPreferenceSpecs.DVR_PRE_PADDING_MINUTES.readFrom(prefs),
        dvrPostPaddingMinutes = SyncPlayCastPreferenceSpecs.DVR_POST_PADDING_MINUTES.readFrom(prefs),
        dvrRecordingQuality = SyncPlayCastPreferenceSpecs.DVR_RECORDING_QUALITY.readStored(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, the typed slots — is the row's, not
    // re-declared here); the declaration-only string rows keep their
    // hand-written lines (the documented opt-outs).
    // ------------------------------------------------------------------

    suspend fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.SYNC_PLAY_JOIN_BEHAVIOR.writeTo(it, behavior) }
    }

    suspend fun setSyncPlayToleranceMs(ms: Long) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.SYNC_PLAY_TOLERANCE_MS.writeTo(it, ms) }
    }

    suspend fun setSyncPlayAutoAcceptInvites(enabled: Boolean) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.SYNC_PLAY_AUTO_ACCEPT_INVITES.writeTo(it, enabled) }
    }

    suspend fun setDefaultCastingStrategy(strategy: CastingStrategy) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.DEFAULT_CASTING_STRATEGY.writeTo(it, strategy) }
    }

    suspend fun setBackgroundCastingEnabled(enabled: Boolean) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.BACKGROUND_CASTING_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setPreferredRenderer(renderer: String?) {
        dataStore.edit {
            if (renderer != null) it[Keys.PREFERRED_RENDERER] = renderer else it.remove(Keys.PREFERRED_RENDERER)
        }
    }

    suspend fun setDvrPrePaddingMinutes(minutes: Int) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.DVR_PRE_PADDING_MINUTES.writeTo(it, minutes) }
    }

    suspend fun setDvrPostPaddingMinutes(minutes: Int) {
        dataStore.edit { SyncPlayCastPreferenceSpecs.DVR_POST_PADDING_MINUTES.writeTo(it, minutes) }
    }

    suspend fun setDvrRecordingQuality(quality: String) {
        dataStore.edit { it[Keys.DVR_RECORDING_QUALITY] = quality }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. This is the `SYNCPLAY_CASTING` reset category
     * minus `LIVE_STREAM_OPTION` (owned by `PlaybackStore`).
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [SyncPlayCastPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. Every key owned here
     * descends under `PreferenceResetCategory.SYNCPLAY_CASTING`.
     * `LIVE_STREAM_OPTION` is owned by `PlaybackStore`, and the runtime recall
     * slots (favorite channels, recent DLNA devices, live-TV last channel) are
     * excluded from category reset by the facade, so none appear in any
     * category list here.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        SyncPlayCastPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with) — except [Keys.PREFERRED_RENDERER], whose nullable guard stays the
     * hand-written line (an absent-key restore cannot be a row write).
     */
    suspend fun restore(slice: SyncPlayCastSlice) {
        dataStore.edit { prefs ->
            SyncPlayCastPreferenceSpecs.SYNC_PLAY_JOIN_BEHAVIOR.writeTo(prefs, slice.syncPlayJoinBehavior)
            SyncPlayCastPreferenceSpecs.SYNC_PLAY_TOLERANCE_MS.writeTo(prefs, slice.syncPlayToleranceMs)
            SyncPlayCastPreferenceSpecs.SYNC_PLAY_AUTO_ACCEPT_INVITES.writeTo(prefs, slice.syncPlayAutoAcceptInvites)
            SyncPlayCastPreferenceSpecs.DEFAULT_CASTING_STRATEGY.writeTo(prefs, slice.defaultCastingStrategy)
            SyncPlayCastPreferenceSpecs.BACKGROUND_CASTING_ENABLED.writeTo(prefs, slice.backgroundCastingEnabled)
            slice.preferredRenderer?.let { prefs[Keys.PREFERRED_RENDERER] = it }
            SyncPlayCastPreferenceSpecs.DVR_PRE_PADDING_MINUTES.writeTo(prefs, slice.dvrPrePaddingMinutes)
            SyncPlayCastPreferenceSpecs.DVR_POST_PADDING_MINUTES.writeTo(prefs, slice.dvrPostPaddingMinutes)
            prefs[Keys.DVR_RECORDING_QUALITY] = slice.dvrRecordingQuality
        }
    }
}

/**
 * The SyncPlay + casting + DVR preference slice. Plain data class.
 * Every default IS its [SyncPlayCastPreferenceSpecs] row's declared default —
 * read off the row via a plain accessor, so a preference's default is stated
 * exactly once, in its spec, and cannot drift between the spec (what an absent
 * key reads) and the slice (what a fresh in-memory instance serves). The
 * spec-default equality is pinned field-for-field by
 * `SyncPlayCastSpecDerivationTest`.
 */
@Immutable
@Serializable
data class SyncPlayCastSlice(
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayCastPreferenceSpecs.SYNC_PLAY_JOIN_BEHAVIOR.default,
    val syncPlayToleranceMs: Long = SyncPlayCastPreferenceSpecs.SYNC_PLAY_TOLERANCE_MS.default,
    val syncPlayAutoAcceptInvites: Boolean = SyncPlayCastPreferenceSpecs.SYNC_PLAY_AUTO_ACCEPT_INVITES.default,
    val defaultCastingStrategy: CastingStrategy = SyncPlayCastPreferenceSpecs.DEFAULT_CASTING_STRATEGY.default,
    val backgroundCastingEnabled: Boolean = SyncPlayCastPreferenceSpecs.BACKGROUND_CASTING_ENABLED.default,
    val preferredRenderer: String? = SyncPlayCastPreferenceSpecs.PREFERRED_RENDERER.default,
    val dvrPrePaddingMinutes: Int = SyncPlayCastPreferenceSpecs.DVR_PRE_PADDING_MINUTES.default,
    val dvrPostPaddingMinutes: Int = SyncPlayCastPreferenceSpecs.DVR_POST_PADDING_MINUTES.default,
    val dvrRecordingQuality: String = SyncPlayCastPreferenceSpecs.DVR_RECORDING_QUALITY.default,
)
