package com.raulshma.jellyplay.core.datastore.notification

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
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
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Codec note:** the notification booleans (`notifications_enabled`,
 * `notifications_quiet_hours_enabled`, `notifications_sound_enabled`,
 * `notifications_vibrate_enabled`, `notifications_lights_enabled`) and the
 * notification ints (`notifications_quiet_hours_start/end`,
 * `notifications_max_per_check`) are **not** in the legacy-string → typed-key
 * migration lists, so they are read with plain `prefs[key] ?: default`. The
 * newsletter keys (`newsletter_enabled`, `newsletter_day_of_week`,
 * `newsletter_last_viewed_ms`) **are** migrated, so they go through
 * [PreferenceCodec.readBool]/[readInt]/[readLong].
 */
class NotificationStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATIONS_CHECK_FREQUENCY = stringPreferencesKey("notifications_check_frequency")
        val NOTIFICATIONS_QUIET_HOURS_ENABLED = booleanPreferencesKey("notifications_quiet_hours_enabled")
        val NOTIFICATIONS_QUIET_HOURS_START = intPreferencesKey("notifications_quiet_hours_start")
        val NOTIFICATIONS_QUIET_HOURS_END = intPreferencesKey("notifications_quiet_hours_end")
        val NOTIFICATIONS_SOUND_ENABLED = booleanPreferencesKey("notifications_sound_enabled")
        val NOTIFICATIONS_VIBRATE_ENABLED = booleanPreferencesKey("notifications_vibrate_enabled")
        val NOTIFICATIONS_LIGHTS_ENABLED = booleanPreferencesKey("notifications_lights_enabled")
        val NOTIFICATIONS_MAX_PER_CHECK = intPreferencesKey("notifications_max_per_check")
        val NOTIFICATIONS_LIBRARY_CONFIGS = stringPreferencesKey("notifications_library_configs")

        val NEWSLETTER_ENABLED = booleanPreferencesKey("newsletter_enabled")
        val NEWSLETTER_DAY_OF_WEEK = intPreferencesKey("newsletter_day_of_week")
        val NEWSLETTER_LAST_VIEWED_MS = longPreferencesKey("newsletter_last_viewed_ms")
        val ENABLED_NEWSLETTER_SECTIONS = stringPreferencesKey("enabled_newsletter_sections")
        val NEWSLETTER_SECTION_ORDER = stringPreferencesKey("newsletter_section_order")
    }

    // JSON memoisation — decode is skipped when the raw key is unchanged.
    private var cachedNotificationLibraryConfigs: ParsedCache<Map<String, LibraryNotificationConfig>> =
        ParsedCache(null, emptyMap())
    private var cachedEnabledNewsletterSections: ParsedCache<Set<NewsletterSectionType>> =
        ParsedCache(null, NewsletterSectionType.entries.toSet())
    private var cachedNewsletterSectionOrder: ParsedCache<List<NewsletterSectionType>> =
        ParsedCache(null, NewsletterSectionType.DEFAULT_ORDER)

    val notification: StateFlow<NotificationSlice> =
        dataStore.sliceStateFlow(scope, seed = NotificationSlice(), read = ::read)

    internal fun read(prefs: Preferences): NotificationSlice = NotificationSlice(
        notificationPreferences = NotificationPreferences(
            enabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            checkFrequency = readCheckFrequency(prefs),
            quietHoursEnabled = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] ?: false,
            quietHoursStart = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_START] ?: 1380,
            quietHoursEnd = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_END] ?: 420,
            soundEnabled = prefs[Keys.NOTIFICATIONS_SOUND_ENABLED] ?: true,
            vibrateEnabled = prefs[Keys.NOTIFICATIONS_VIBRATE_ENABLED] ?: true,
            lightsEnabled = prefs[Keys.NOTIFICATIONS_LIGHTS_ENABLED] ?: true,
            maxPerCheck = prefs[Keys.NOTIFICATIONS_MAX_PER_CHECK] ?: 10,
            libraryConfigs = readNotificationLibraryConfigs(prefs),
        ),
        newsletterEnabled = PreferenceCodec.readBool(prefs, Keys.NEWSLETTER_ENABLED, "newsletter_enabled", true),
        newsletterDayOfWeek = PreferenceCodec.readInt(prefs, Keys.NEWSLETTER_DAY_OF_WEEK, "newsletter_day_of_week", 7),
        newsletterLastViewedMs = PreferenceCodec.readLong(prefs, Keys.NEWSLETTER_LAST_VIEWED_MS, "newsletter_last_viewed_ms", 0L),
        enabledNewsletterSections = readEnabledNewsletterSections(prefs),
        newsletterSectionOrder = readNewsletterSectionOrder(prefs),
    )

    private fun readCheckFrequency(prefs: Preferences): CheckFrequency =
        prefs[Keys.NOTIFICATIONS_CHECK_FREQUENCY].toEnumOrNull() ?: CheckFrequency.EVERY_6_HOURS

    // MemoizeNull (this store's pre-promotion policy at every site): a null
    // raw is a cacheable input — the decoded value depends only on the raw
    // string, so a memoised default is safe to serve.
    private fun readNotificationLibraryConfigs(prefs: Preferences): Map<String, LibraryNotificationConfig> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS],
            cache = cachedNotificationLibraryConfigs,
            default = emptyMap(),
            parse = { json.decodeFromString<Map<String, LibraryNotificationConfig>>(it) },
            cacheRef = { cachedNotificationLibraryConfigs = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

    private fun readEnabledNewsletterSections(prefs: Preferences): Set<NewsletterSectionType> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.ENABLED_NEWSLETTER_SECTIONS],
            cache = cachedEnabledNewsletterSections,
            default = NewsletterSectionType.entries.toSet(),
            parse = { json.decodeFromString<Set<NewsletterSectionType>>(it) },
            cacheRef = { cachedEnabledNewsletterSections = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

    private fun readNewsletterSectionOrder(prefs: Preferences): List<NewsletterSectionType> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.NEWSLETTER_SECTION_ORDER],
            cache = cachedNewsletterSectionOrder,
            default = NewsletterSectionType.DEFAULT_ORDER,
            parse = { json.decodeFromString<List<NewsletterSectionType>>(it) },
            cacheRef = { cachedNewsletterSectionOrder = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    /**
     * Atomic update of the whole [NotificationPreferences] aggregate. Reads the
     * current values inside the edit, applies [transform], and writes every
     * field back in one transaction — mirrors the legacy
     * `UserPreferencesStore.updateNotificationPreferences`.
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
        }
    }

    suspend fun setNewsletterEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NEWSLETTER_ENABLED] = enabled }
    }

    suspend fun setNewsletterDayOfWeek(day: Int) {
        dataStore.edit { it[Keys.NEWSLETTER_DAY_OF_WEEK] = day }
    }

    /** One-time state — recorded but **not** part of [resetKeys]. */
    suspend fun setNewsletterLastViewed(timestampMs: Long) {
        dataStore.edit { it[Keys.NEWSLETTER_LAST_VIEWED_MS] = timestampMs }
    }

    suspend fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) {
        dataStore.edit { it[Keys.ENABLED_NEWSLETTER_SECTIONS] = json.encodeToString(sections) }
    }

    suspend fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) {
        dataStore.edit { it[Keys.NEWSLETTER_SECTION_ORDER] = json.encodeToString(order) }
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
     * [category]. This store owns keys in both `NOTIFICATIONS` (the whole
     * notification aggregate) and `NEWSLETTER` (the digest settings minus the
     * one-time [Keys.NEWSLETTER_LAST_VIEWED_MS] state).
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.NOTIFICATIONS -> listOf(
            Keys.NOTIFICATIONS_ENABLED,
            Keys.NOTIFICATIONS_CHECK_FREQUENCY,
            Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED,
            Keys.NOTIFICATIONS_QUIET_HOURS_START,
            Keys.NOTIFICATIONS_QUIET_HOURS_END,
            Keys.NOTIFICATIONS_SOUND_ENABLED,
            Keys.NOTIFICATIONS_VIBRATE_ENABLED,
            Keys.NOTIFICATIONS_LIGHTS_ENABLED,
            Keys.NOTIFICATIONS_MAX_PER_CHECK,
            Keys.NOTIFICATIONS_LIBRARY_CONFIGS,
        )
        PreferenceResetCategory.NEWSLETTER -> listOf(
            Keys.NEWSLETTER_ENABLED,
            Keys.NEWSLETTER_DAY_OF_WEEK,
            Keys.ENABLED_NEWSLETTER_SECTIONS,
            Keys.NEWSLETTER_SECTION_ORDER,
        )
        else -> emptyList()
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences]. The notification
     * sub-domain is read from [NotificationSlice.notificationPreferences];
     * libraryConfigs / enabledNewsletterSections / newsletterSectionOrder are
     * JSON-encoded via this store's [json] codec.
     */
    suspend fun restore(slice: NotificationSlice) {
        dataStore.edit { it ->
            it[Keys.NOTIFICATIONS_ENABLED] = slice.notificationPreferences.enabled
            it[Keys.NOTIFICATIONS_CHECK_FREQUENCY] = slice.notificationPreferences.checkFrequency.name
            it[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] = slice.notificationPreferences.quietHoursEnabled
            it[Keys.NOTIFICATIONS_QUIET_HOURS_START] = slice.notificationPreferences.quietHoursStart
            it[Keys.NOTIFICATIONS_QUIET_HOURS_END] = slice.notificationPreferences.quietHoursEnd
            it[Keys.NOTIFICATIONS_SOUND_ENABLED] = slice.notificationPreferences.soundEnabled
            it[Keys.NOTIFICATIONS_VIBRATE_ENABLED] = slice.notificationPreferences.vibrateEnabled
            it[Keys.NOTIFICATIONS_LIGHTS_ENABLED] = slice.notificationPreferences.lightsEnabled
            it[Keys.NOTIFICATIONS_MAX_PER_CHECK] = slice.notificationPreferences.maxPerCheck
            it[Keys.NOTIFICATIONS_LIBRARY_CONFIGS] = json.encodeToString(slice.notificationPreferences.libraryConfigs)
            it[Keys.NEWSLETTER_ENABLED] = slice.newsletterEnabled
            it[Keys.NEWSLETTER_DAY_OF_WEEK] = slice.newsletterDayOfWeek
            it[Keys.NEWSLETTER_LAST_VIEWED_MS] = slice.newsletterLastViewedMs
            it[Keys.ENABLED_NEWSLETTER_SECTIONS] = json.encodeToString(slice.enabledNewsletterSections)
            it[Keys.NEWSLETTER_SECTION_ORDER] = json.encodeToString(slice.newsletterSectionOrder)
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
 * Defaults mirror the projection defaults in [NotificationStore.read].
 */
@Immutable
@Serializable
data class NotificationSlice(
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val newsletterEnabled: Boolean = true,
    val newsletterDayOfWeek: Int = 7,
    val newsletterLastViewedMs: Long = 0L,
    val enabledNewsletterSections: Set<NewsletterSectionType> = NewsletterSectionType.entries.toSet(),
    val newsletterSectionOrder: List<NewsletterSectionType> = NewsletterSectionType.DEFAULT_ORDER,
)
