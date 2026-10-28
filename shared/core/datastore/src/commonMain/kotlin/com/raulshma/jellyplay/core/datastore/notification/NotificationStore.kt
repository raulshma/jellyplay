package com.raulshma.jellyplay.core.datastore.notification

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SeriesNotificationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Deep module owning the **notification + newsletter** preference domain.
 *
 * Two related sub-domains fold into one store because the legacy projection
 * already grouped them as user-facing "what gets surfaced to me, and how often"
 * settings:
 *
 *  - **Notifications** — the enabled toggle, background check frequency, quiet
 *    hours window + sound/vibrate/lights channels, per-check cap, and the
 *    per-library notification configs (JSON map). These are projected as the
 *    nested [NotificationPreferences] aggregate, mirroring the legacy
 *    `UserPreferencesStore.notificationPreferences` shape.
 *  - **Newsletter** — the weekly digest enabled toggle, day-of-week, the
 *    last-viewed timestamp (one-time state, *excluded* from reset), and the
 *    enabled-sections set + section ordering (both JSON-encoded).
 *
 * **Decision — slice shape:** the slice keeps the nested
 * [NotificationPreferences] object for the notification sub-domain (so callers
 * that already consume the aggregate are unchanged) but flattens the newsletter
 * fields onto the slice itself, since newsletter has no equivalent aggregate in
 * `core.model` and the legacy store exposed them as independent keys.
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [NotificationPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding — including the read-policy split the codec note below
 * records, now per row) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding, the newsletter
 * single-key setters and [restore]'s encoded rows delegate to the rows'
 * derived writes, and [resetKeysFor] filters the rows by reset category. The
 * JSON blob decodes live in the rows now (memoised beside them).
 *
 * **Cross-key writes owned here (hand-written by decision — a coalesced or
 * read-modify-write cannot be a row encoding):**
 *  - [updateNotificationPreferences] — reads the current aggregate inside the
 *    edit, applies [transform], and writes every field back in one
 *    transaction (mirrors the legacy
 *    `UserPreferencesStore.updateNotificationPreferences`).
 *  - [updateSeriesNotificationConfigs] — the per-series map's
 *    read-modify-write verb (the worker's per-series write-back and the
 *    opt-in/out commands funnel through it, so concurrent writers cannot drop
 *    each other's entries), plus its [setSeriesNotificationEnabled] /
 *    [clearSeriesNotificationConfigs] command wrappers.
 *  - the six plain boolean rows (declaration-only `PreferenceSpec.boolean`
 *    declarations with no derived write) are written only by the two
 *    hand-written sites above and [restore]'s hand rows.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Codec note:** the notification booleans (`notifications_enabled`,
 * `notifications_quiet_hours_enabled`, `notifications_sound_enabled`,
 * `notifications_vibrate_enabled`, `notifications_lights_enabled`,
 * `notifications_new_episodes_enabled`) and the
 * notification ints (`notifications_quiet_hours_start/end`,
 * `notifications_max_per_check`) are **not** in the legacy-string → typed-key
 * migration lists, so they are read with plain `prefs[key] ?: default` (the
 * declaration-only [NotificationPreferenceSpecs] rows; the ints ride the
 * behaviorally-identical `PreferenceSpec.plainInt`-encoded rows —
 * see that object's read-policy note). The newsletter keys
 * (`newsletter_enabled`, `newsletter_day_of_week`, `newsletter_last_viewed_ms`)
 * **are** migrated, so they go through the [NotificationPreferenceSpecs]
 * `plainBoolean`/`plainInt`/`plainLong` rows' shared fallback reads.
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [NotificationPreferenceSpecs] row + one [read] / [restore] row (and its
 * setter) + one write-through test line. See the spec KDoc.
 */
class NotificationStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    /**
     * The store's DataStore keys, each derived from its
     * [NotificationPreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key of
     * the same name). Kept as a plain object rather than folded into the rows
     * because it is the reflection anchor for the JVM reset-coverage guard and
     * the key-identity reference for the hand-written coalesced / read-modify-
     * write verbs below.
     */
    internal object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_ENABLED.keyName)
        val NOTIFICATIONS_CHECK_FREQUENCY = stringPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_CHECK_FREQUENCY.keyName)
        val NOTIFICATIONS_QUIET_HOURS_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_ENABLED.keyName)
        val NOTIFICATIONS_QUIET_HOURS_START = intPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_START.keyName)
        val NOTIFICATIONS_QUIET_HOURS_END = intPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_END.keyName)
        val NOTIFICATIONS_SOUND_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_SOUND_ENABLED.keyName)
        val NOTIFICATIONS_VIBRATE_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_VIBRATE_ENABLED.keyName)
        val NOTIFICATIONS_LIGHTS_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_LIGHTS_ENABLED.keyName)
        val NOTIFICATIONS_MAX_PER_CHECK = intPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_MAX_PER_CHECK.keyName)
        val NOTIFICATIONS_LIBRARY_CONFIGS = stringPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_LIBRARY_CONFIGS.keyName)
        val NOTIFICATIONS_NEW_EPISODES_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_NEW_EPISODES_ENABLED.keyName)
        val NOTIFICATIONS_SERIES_CONFIGS = stringPreferencesKey(NotificationPreferenceSpecs.NOTIFICATIONS_SERIES_CONFIGS.keyName)


        val NEWSLETTER_ENABLED = booleanPreferencesKey(NotificationPreferenceSpecs.NEWSLETTER_ENABLED.keyName)
        val NEWSLETTER_DAY_OF_WEEK = intPreferencesKey(NotificationPreferenceSpecs.NEWSLETTER_DAY_OF_WEEK.keyName)
        val NEWSLETTER_LAST_VIEWED_MS = longPreferencesKey(NotificationPreferenceSpecs.NEWSLETTER_LAST_VIEWED_MS.keyName)
        val ENABLED_NEWSLETTER_SECTIONS = stringPreferencesKey(NotificationPreferenceSpecs.ENABLED_NEWSLETTER_SECTIONS.keyName)
        val NEWSLETTER_SECTION_ORDER = stringPreferencesKey(NotificationPreferenceSpecs.NEWSLETTER_SECTION_ORDER.keyName)
    }

    val notification: StateFlow<NotificationSlice> =
        dataStore.sliceStateFlow(scope, seed = NotificationSlice(), read = ::read)

    /**
     * Pure read of the notification + newsletter fields from a raw
     * [Preferences] snapshot, each field delegated to its
     * [NotificationPreferenceSpecs] row encoding (the declaration-only boolean
     * rows via [PreferenceSpec.readStored] — the exact hand plain read).
     * Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): NotificationSlice = NotificationSlice(
        notificationPreferences = NotificationPreferences(
            enabled = NotificationPreferenceSpecs.NOTIFICATIONS_ENABLED.readStored(prefs),
            checkFrequency = NotificationPreferenceSpecs.NOTIFICATIONS_CHECK_FREQUENCY.readFrom(prefs),
            quietHoursEnabled = NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_ENABLED.readStored(prefs),
            quietHoursStart = NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_START.readFrom(prefs),
            quietHoursEnd = NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_END.readFrom(prefs),
            soundEnabled = NotificationPreferenceSpecs.NOTIFICATIONS_SOUND_ENABLED.readStored(prefs),
            vibrateEnabled = NotificationPreferenceSpecs.NOTIFICATIONS_VIBRATE_ENABLED.readStored(prefs),
            lightsEnabled = NotificationPreferenceSpecs.NOTIFICATIONS_LIGHTS_ENABLED.readStored(prefs),
            maxPerCheck = NotificationPreferenceSpecs.NOTIFICATIONS_MAX_PER_CHECK.readFrom(prefs),
            libraryConfigs = NotificationPreferenceSpecs.NOTIFICATIONS_LIBRARY_CONFIGS.readFrom(prefs),
            newEpisodesEnabled = NotificationPreferenceSpecs.NOTIFICATIONS_NEW_EPISODES_ENABLED.readStored(prefs),
            seriesConfigs = NotificationPreferenceSpecs.NOTIFICATIONS_SERIES_CONFIGS.readFrom(prefs),
        ),
        newsletterEnabled = NotificationPreferenceSpecs.NEWSLETTER_ENABLED.readFrom(prefs),
        newsletterDayOfWeek = NotificationPreferenceSpecs.NEWSLETTER_DAY_OF_WEEK.readFrom(prefs),
        newsletterLastViewedMs = NotificationPreferenceSpecs.NEWSLETTER_LAST_VIEWED_MS.readFrom(prefs),
        enabledNewsletterSections = NotificationPreferenceSpecs.ENABLED_NEWSLETTER_SECTIONS.readFrom(prefs),
        newsletterSectionOrder = NotificationPreferenceSpecs.NEWSLETTER_SECTION_ORDER.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — the newsletter single-key setters delegate to their rows'
    // derived writes; the notification aggregate has no single-key setters
    // (its writes are the coalesced verb below).
    // ------------------------------------------------------------------

    /**
     * Atomic update of the whole [NotificationPreferences] aggregate. Reads the
     * current values inside the edit, applies [transform], and writes every
     * field back in one transaction — mirrors the legacy
     * `UserPreferencesStore.updateNotificationPreferences`. Hand-written by
     * decision: a coalesced multi-key write cannot be a row encoding (see the
     * [NotificationPreferenceSpecs] KDoc's opt-outs).
     */
    suspend fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        dataStore.edit { prefs ->
            val current = NotificationPreferences(
                enabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
                checkFrequency = prefs[Keys.NOTIFICATIONS_CHECK_FREQUENCY].toEnumOrNull() ?: CheckFrequency.EVERY_6_HOURS,
                quietHoursEnabled = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] ?: false,
                quietHoursStart = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_START] ?: 1380,
                quietHoursEnd = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_END] ?: 420,
                soundEnabled = prefs[Keys.NOTIFICATIONS_SOUND_ENABLED] ?: true,
                vibrateEnabled = prefs[Keys.NOTIFICATIONS_VIBRATE_ENABLED] ?: true,
                lightsEnabled = prefs[Keys.NOTIFICATIONS_LIGHTS_ENABLED] ?: true,
                maxPerCheck = prefs[Keys.NOTIFICATIONS_MAX_PER_CHECK] ?: 10,
                libraryConfigs = try {
                    prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS]?.let {
                        json.decodeFromString<Map<String, LibraryNotificationConfig>>(it)
                    } ?: emptyMap()
                } catch (_: Exception) { emptyMap() },
                newEpisodesEnabled = prefs[Keys.NOTIFICATIONS_NEW_EPISODES_ENABLED] ?: false,
                seriesConfigs = try {
                    prefs[Keys.NOTIFICATIONS_SERIES_CONFIGS]?.let {
                        json.decodeFromString<Map<String, SeriesNotificationConfig>>(it)
                    } ?: emptyMap()
                } catch (_: Exception) { emptyMap() },
            )
            val updated = transform(current)
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.enabled
            prefs[Keys.NOTIFICATIONS_CHECK_FREQUENCY] = updated.checkFrequency.name
            prefs[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] = updated.quietHoursEnabled
            prefs[Keys.NOTIFICATIONS_QUIET_HOURS_START] = updated.quietHoursStart
            prefs[Keys.NOTIFICATIONS_QUIET_HOURS_END] = updated.quietHoursEnd
            prefs[Keys.NOTIFICATIONS_SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.NOTIFICATIONS_VIBRATE_ENABLED] = updated.vibrateEnabled
            prefs[Keys.NOTIFICATIONS_LIGHTS_ENABLED] = updated.lightsEnabled
            prefs[Keys.NOTIFICATIONS_MAX_PER_CHECK] = updated.maxPerCheck
            prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS] = json.encodeToString(updated.libraryConfigs)
            prefs[Keys.NOTIFICATIONS_NEW_EPISODES_ENABLED] = updated.newEpisodesEnabled
            prefs[Keys.NOTIFICATIONS_SERIES_CONFIGS] = json.encodeToString(updated.seriesConfigs)
        }
    }

    /**
     * Read-modify-write of the per-series config map (the library-configs
     * aggregate transform's series-granularity twin). The worker's per-series
     * write-back (diff baseline + check stamp) and the opt-in/out UI commands
     * both funnel through here, so concurrent writers cannot drop each other's
     * entries. Hand-written by decision: a read-modify-write cannot be a row
     * encoding (see the [NotificationPreferenceSpecs] KDoc's opt-outs).
     */
    suspend fun updateSeriesNotificationConfigs(
        transform: (Map<String, SeriesNotificationConfig>) -> Map<String, SeriesNotificationConfig>,
    ) {
        dataStore.edit { prefs ->
            val current = try {
                prefs[Keys.NOTIFICATIONS_SERIES_CONFIGS]?.let {
                    json.decodeFromString<Map<String, SeriesNotificationConfig>>(it)
                } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[Keys.NOTIFICATIONS_SERIES_CONFIGS] = json.encodeToString(transform(current))
        }
    }

    /**
     * Opt-in / opt-out command for one series (the ⋮ menu entry, the bell
     * and the management sheet all route here). Opt-out REMOVES the entry
     * (the management list shows opted-in series only — a disabled husk
     * would be dead weight), so a disable→re-enable pair starts with a fresh
     * baseline and the worker's first-check anti-spam rule re-records it
     * without notifying. Opt-in keeps a surviving entry's
     * [SeriesNotificationConfig.lastNotifiedEpisodeId] baseline (an older
     * config shape may have left one behind).
     */
    suspend fun setSeriesNotificationEnabled(seriesId: String, enabled: Boolean) {
        updateSeriesNotificationConfigs { configs ->
            if (enabled) {
                configs + (
                    seriesId to (configs[seriesId]?.copy(enabled = true)
                        ?: SeriesNotificationConfig())
                )
            } else {
                configs - seriesId
            }
        }
    }

    /** Removes every series opt-in (the management sheet's "Restore all"). */
    suspend fun clearSeriesNotificationConfigs() {
        updateSeriesNotificationConfigs { emptyMap() }
    }

    suspend fun setNewsletterEnabled(enabled: Boolean) {
        dataStore.edit { NotificationPreferenceSpecs.NEWSLETTER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setNewsletterDayOfWeek(day: Int) {
        dataStore.edit { NotificationPreferenceSpecs.NEWSLETTER_DAY_OF_WEEK.writeTo(it, day) }
    }

    /** One-time state — recorded but **not** part of [resetKeys]. */
    suspend fun setNewsletterLastViewed(timestampMs: Long) {
        dataStore.edit { NotificationPreferenceSpecs.NEWSLETTER_LAST_VIEWED_MS.writeTo(it, timestampMs) }
    }

    suspend fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) {
        dataStore.edit { NotificationPreferenceSpecs.ENABLED_NEWSLETTER_SECTIONS.writeTo(it, sections) }
    }

    suspend fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) {
        dataStore.edit { NotificationPreferenceSpecs.NEWSLETTER_SECTION_ORDER.writeTo(it, order) }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. Draws from both
     * `PreferenceResetCategory.NOTIFICATIONS` and
     * `PreferenceResetCategory.NEWSLETTER`. [Keys.NEWSLETTER_LAST_VIEWED_MS]
     * is excluded by appearing in no category list — it is one-time
     * viewed-state, not a user setting.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [NotificationPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. This store owns keys in
     * both `NOTIFICATIONS` (the whole notification aggregate) and `NEWSLETTER`
     * (the digest settings minus the one-time [Keys.NEWSLETTER_LAST_VIEWED_MS]
     * state).
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        NotificationPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore. The [NotificationPreferenceSpecs] rows with a derived write
     * go through it; the six declaration-only boolean rows keep hand-written
     * lines (they declare no derived write — see the spec KDoc's opt-outs).
     */
    suspend fun restore(slice: NotificationSlice) {
        dataStore.edit { it ->
            it[Keys.NOTIFICATIONS_ENABLED] = slice.notificationPreferences.enabled
            NotificationPreferenceSpecs.NOTIFICATIONS_CHECK_FREQUENCY.writeTo(
                it, slice.notificationPreferences.checkFrequency,
            )
            it[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] = slice.notificationPreferences.quietHoursEnabled
            NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_START.writeTo(
                it, slice.notificationPreferences.quietHoursStart,
            )
            NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_END.writeTo(
                it, slice.notificationPreferences.quietHoursEnd,
            )
            it[Keys.NOTIFICATIONS_SOUND_ENABLED] = slice.notificationPreferences.soundEnabled
            it[Keys.NOTIFICATIONS_VIBRATE_ENABLED] = slice.notificationPreferences.vibrateEnabled
            it[Keys.NOTIFICATIONS_LIGHTS_ENABLED] = slice.notificationPreferences.lightsEnabled
            NotificationPreferenceSpecs.NOTIFICATIONS_MAX_PER_CHECK.writeTo(
                it, slice.notificationPreferences.maxPerCheck,
            )
            NotificationPreferenceSpecs.NOTIFICATIONS_LIBRARY_CONFIGS.writeTo(
                it, slice.notificationPreferences.libraryConfigs,
            )
            it[Keys.NOTIFICATIONS_NEW_EPISODES_ENABLED] = slice.notificationPreferences.newEpisodesEnabled
            NotificationPreferenceSpecs.NOTIFICATIONS_SERIES_CONFIGS.writeTo(
                it, slice.notificationPreferences.seriesConfigs,
            )
            NotificationPreferenceSpecs.NEWSLETTER_ENABLED.writeTo(it, slice.newsletterEnabled)
            NotificationPreferenceSpecs.NEWSLETTER_DAY_OF_WEEK.writeTo(it, slice.newsletterDayOfWeek)
            NotificationPreferenceSpecs.NEWSLETTER_LAST_VIEWED_MS.writeTo(it, slice.newsletterLastViewedMs)
            NotificationPreferenceSpecs.ENABLED_NEWSLETTER_SECTIONS.writeTo(it, slice.enabledNewsletterSections)
            NotificationPreferenceSpecs.NEWSLETTER_SECTION_ORDER.writeTo(it, slice.newsletterSectionOrder)
        }
    }
}

/**
 * The notification + newsletter preference slice. Plain data class.
 *
 * The notification sub-domain is exposed as the nested [notificationPreferences]
 * aggregate (matching the legacy `UserPreferences.notificationPreferences`
 * shape). The newsletter sub-domain is flattened onto the slice because it has
 * no equivalent aggregate in `core.model`.
 *
 * The newsletter defaults ARE their [NotificationPreferenceSpecs] rows'
 * declared defaults — read off the rows via plain accessors, so a preference's
 * default is stated exactly once, in its spec. The aggregate keeps its
 * `core.model` defaults (the spec rows pin the same values —
 * `NotificationSpecDerivationTest`); the [NotificationPreferences] aggregate
 * is owned by `core.model`, which cannot read the spec rows. The spec-default
 * equality is pinned by that derivation test.
 */
@Immutable
@Serializable
data class NotificationSlice(
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val newsletterEnabled: Boolean = NotificationPreferenceSpecs.NEWSLETTER_ENABLED.default,
    val newsletterDayOfWeek: Int = NotificationPreferenceSpecs.NEWSLETTER_DAY_OF_WEEK.default,
    val newsletterLastViewedMs: Long = NotificationPreferenceSpecs.NEWSLETTER_LAST_VIEWED_MS.default,
    val enabledNewsletterSections: Set<NewsletterSectionType> = NotificationPreferenceSpecs.ENABLED_NEWSLETTER_SECTIONS.default,
    val newsletterSectionOrder: List<NewsletterSectionType> = NotificationPreferenceSpecs.NEWSLETTER_SECTION_ORDER.default,
)
