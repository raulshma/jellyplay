package com.raulshma.jellyplay.core.datastore.notification

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SeriesNotificationConfig
import kotlinx.serialization.encodeToString

/**
 * Memoisation holders for the four JSON blob rows' decodes, keyed on the raw
 * string so a decode is skipped when its key has not changed on a given
 * `dataStore.data` emission. Moved here from per-`NotificationStore` fields
 * with the rows they belong to (the playback domain's passthrough-codec cache
 * precedent): each decode is a pure function of its raw string, and a null raw
 * memoises its result exactly once ([CachedJsonNullPolicy.MemoizeNull] — this
 * store's pre-promotion policy at every site). See [PreferenceCodec.cachedJson].
 */
private var cachedNotificationLibraryConfigs: ParsedCache<Map<String, LibraryNotificationConfig>> =
    ParsedCache(null, emptyMap())
private var cachedNotificationSeriesConfigs: ParsedCache<Map<String, SeriesNotificationConfig>> =
    ParsedCache(null, emptyMap())
private var cachedEnabledNewsletterSections: ParsedCache<Set<NewsletterSectionType>> =
    ParsedCache(null, NewsletterSectionType.entries.toSet())
private var cachedNewsletterSectionOrder: ParsedCache<List<NewsletterSectionType>> =
    ParsedCache(null, NewsletterSectionType.DEFAULT_ORDER)

/**
 * The notification + newsletter domain's single preference declaration: one
 * [PreferenceSpec] row per key `NotificationStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.boolean] / [plainBoolean] / [plainInt] /
 * [plainLong] / [enumRow] / [derived]).
 *
 * Two related sub-domains fold into one declaration because the legacy
 * projection already grouped them as user-facing "what gets surfaced to me,
 * and how often" settings: the notification aggregate's twelve keys (the
 * `NOTIFICATIONS` reset category) and the newsletter's five (the `NEWSLETTER`
 * category, minus [NEWSLETTER_LAST_VIEWED_MS] — one-time viewed state that
 * carries no category).
 *
 * **Read policy (the store KDoc's codec note, now declared per row):** the
 * notification booleans (`notifications_enabled`,
 * `notifications_quiet_hours_enabled`, `notifications_sound_enabled`,
 * `notifications_vibrate_enabled`, `notifications_lights_enabled`,
 * `notifications_new_episodes_enabled`) and the notification ints
 * (`notifications_quiet_hours_start/end`, `notifications_max_per_check`) are
 * deliberately NOT in `PreferenceCodec`'s legacy-string → typed-key migration
 * lists — they were born in the typed-key era (a legacy string slot cannot
 * exist under their names), so their rows use the plain [PreferenceSpec.boolean]
 * declaration wherever one exists (the read is exactly the hand
 * `prefs[key] ?: default`). The ints have no plain declaration variant (the
 * spec tier's declaration-only constructors are boolean/string/custom, and
 * custom's STRING storage would misstate an INT slot), so they ride
 * [plainInt] — whose extra legacy fallback branch can never match for the
 * same born-typed reason, making the read behaviorally identical to the hand
 * plain read. The newsletter keys (`newsletter_enabled`,
 * `newsletter_day_of_week`, `newsletter_last_viewed_ms`) ARE migrated, so
 * they are [plainBoolean] / [plainInt] / [plainLong] rows.
 *
 * `NotificationStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the newsletter single-key setters and
 * the [PreferenceSpec]-encoded rows of `restore` delegate to the rows'
 * derived writes, and `resetKeysFor` filters the rows by reset category. The
 * six plain boolean rows keep the store's hand-written write lines (the
 * documented opt-out — a declaration-only row has no derived write): they are
 * written only by the coalesced [NotificationStore.updateNotificationPreferences]
 * aggregate transform and `restore`, both hand-written.
 *
 * **The documented opt-outs (hand-written residuals, cross-key or
 * read-modify-write by decision):** [NotificationStore.updateNotificationPreferences]
 * (a coalesced multi-key write — reads the current aggregate inside the edit,
 * applies the transform, writes every field back in one transaction) and
 * [NotificationStore.updateSeriesNotificationConfigs] (a read-modify-write of
 * the per-series map — the worker's per-series write-back and the opt-in/out
 * commands funnel through it so concurrent writers cannot drop each other's
 * entries) plus its two command wrappers. A coalesced or read-modify-write
 * write cannot be a row encoding.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one `read()` row, one `restore()` row (and its setter) — plus one
 * write-through test line; the derivation covers key identity, encodings,
 * reset lists and search registration, not the slice plumbing or its
 * coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object NotificationPreferenceSpecs {

    /**
     * Route kind shared by the notification settings-search rows — the plain
     * id the feature layer maps to `Route.NotificationSettings()`.
     */
    const val ROUTE_NOTIFICATION_SETTINGS = "notification_settings"

    /**
     * Route kind of the newsletter rows — the digest group renders on the
     * appearance settings screen (the pre-spec fused rows' home), so its
     * search entries deep-link there.
     */
    const val ROUTE_NEWSLETTER_SETTINGS = "appearance_settings"

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [NotificationSlice] property order (the nested aggregate's field
    // order, then the newsletter fields). A row is constructed exactly
    // once, here; [all], the reset derivation and [searchEntries] all
    // derive from the groups.
    // ------------------------------------------------------------------

    /** The notification aggregate's twelve persisted keys (`NOTIFICATIONS`). */
    private val notificationAggregate: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.boolean(
            keyName = "notifications_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "notifications_enable",
                titleKey = "ss_notifications_enable_title",
                subtitleKey = "ss_notifications_enable_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("notifications", "frequency", "bell", "check frequency", "alerts"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.enumRow(
            keyName = "notifications_check_frequency",
            default = CheckFrequency.EVERY_6_HOURS,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "notification_check_frequency",
                titleKey = "ss_notification_check_frequency_title",
                subtitleKey = "ss_notification_check_frequency_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("notification", "check", "frequency", "interval", "polling", "new media"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.boolean(
            keyName = "notifications_quiet_hours_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "quiet_hours",
                titleKey = "ss_quiet_hours_title",
                subtitleKey = "ss_quiet_hours_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("quiet hours", "suppress", "silent", "night", "do not disturb"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "notifications_quiet_hours_start",
            default = 1380,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "quiet_start",
                titleKey = "ss_quiet_start_title",
                subtitleKey = "ss_quiet_start_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("quiet hours", "start", "begin", "night", "silent"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "notifications_quiet_hours_end",
            default = 420,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "quiet_end",
                titleKey = "ss_quiet_end_title",
                subtitleKey = "ss_quiet_end_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("quiet hours", "end", "morning", "silent"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.boolean(
            keyName = "notifications_sound_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "notification_sound",
                titleKey = "ss_notification_sound_title",
                subtitleKey = "ss_notification_sound_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("notification", "sound", "audio", "alert", "tone"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.boolean(
            keyName = "notifications_vibrate_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "notification_vibrate",
                titleKey = "ss_notification_vibrate_title",
                subtitleKey = "ss_notification_vibrate_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("notification", "vibrate", "vibration", "haptic", "buzz"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.boolean(
            keyName = "notifications_lights_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "notification_lights",
                titleKey = "ss_notification_lights_title",
                subtitleKey = "ss_notification_lights_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("notification", "lights", "led", "pulse", "blink"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "notifications_max_per_check",
            default = 10,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "max_per_check",
                titleKey = "ss_max_per_check_title",
                subtitleKey = "ss_max_per_check_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("max", "per check", "batch", "items", "limit", "notification"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        /*
         * The per-library notification configs, keyed by Jellyfin library id.
         * The decode is memoised on the raw string — see
         * [cachedNotificationLibraryConfigs]. The user-facing write is the
         * coalesced [NotificationStore.updateNotificationPreferences]
         * transform (hand-written — see the object KDoc's opt-outs); the row's
         * derived write is the same whole-map encode [NotificationStore.restore]
         * writes.
         */
        PreferenceSpec.derived(
            keyName = "notifications_library_configs",
            default = emptyMap<String, LibraryNotificationConfig>(),
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedNotificationLibraryConfigs,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, LibraryNotificationConfig>>(it) },
                    cacheRef = { cachedNotificationLibraryConfigs = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
            search = PreferenceSearchSpec(
                id = "notification_libraries",
                titleKey = "ss_notification_libraries_title",
                subtitleKey = "ss_notification_libraries_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("notification", "libraries", "folders", "monitor", "per library"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.boolean(
            keyName = "notifications_new_episodes_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            search = PreferenceSearchSpec(
                id = "notification_new_episodes",
                titleKey = "ss_new_episodes_title",
                subtitleKey = "ss_new_episodes_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("new episodes", "episode", "episodes", "season", "series", "tv", "notification"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        /*
         * The per-series new-episode opt-ins. The user-facing write is the
         * read-modify-write [NotificationStore.updateSeriesNotificationConfigs]
         * verb (hand-written — see the object KDoc's opt-outs); the row's
         * derived write is the same whole-map encode [NotificationStore.restore]
         * writes. The decode is memoised on the raw string — see
         * [cachedNotificationSeriesConfigs].
         */
        PreferenceSpec.derived(
            keyName = "notifications_series_configs",
            default = emptyMap<String, SeriesNotificationConfig>(),
            resetCategory = PreferenceResetCategory.NOTIFICATIONS,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedNotificationSeriesConfigs,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, SeriesNotificationConfig>>(it) },
                    cacheRef = { cachedNotificationSeriesConfigs = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
            search = PreferenceSearchSpec(
                id = "series_notifications",
                titleKey = "ss_series_notifications_title",
                subtitleKey = "ss_series_notifications_subtitle",
                categoryKey = "ss_cat_notifications",
                keywords = listOf("series", "new episodes", "notify", "bell", "per series", "sonarr", "notification"),
                routeKind = ROUTE_NOTIFICATION_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
    )

    /** The newsletter sub-domain's five keys (`NEWSLETTER`, minus the viewed stamp). */
    private val newsletter: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "newsletter_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.NEWSLETTER,
            search = PreferenceSearchSpec(
                id = "newsletter_enabled",
                titleKey = "ss_newsletter_enabled_title",
                subtitleKey = "ss_newsletter_enabled_subtitle",
                categoryKey = "ss_cat_appearance",
                keywords = listOf("newsletter", "digest", "email", "periodic", "report"),
                routeKind = ROUTE_NEWSLETTER_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "newsletter_day_of_week",
            default = 7,
            resetCategory = PreferenceResetCategory.NEWSLETTER,
            search = PreferenceSearchSpec(
                id = "newsletter_delivery_day",
                titleKey = "ss_newsletter_delivery_day_title",
                subtitleKey = "ss_newsletter_delivery_day_subtitle",
                categoryKey = "ss_cat_appearance",
                keywords = listOf("newsletter", "delivery", "day", "schedule", "weekday", "send"),
                routeKind = ROUTE_NEWSLETTER_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * One-time viewed-state — recorded but deliberately NOT part of any
         * reset list (a reset would re-serve already-seen digests as new) and
         * not a setting, so no search face and no slice-default drift risk.
         * The key IS in the legacy longs migration list (the pre-typed era
         * wrote it), hence the [plainLong] encoding.
         */
        PreferenceSpec.plainLong(
            keyName = "newsletter_last_viewed_ms",
            default = 0L,
            resetCategory = null,
        ),
        /*
         * The enabled-section set (JSON blob; decode memoised on the raw
         * string — see [cachedEnabledNewsletterSections]). The search hit
         * restates the sections-config row's screen title (the fold): the
         * catalog row declares no ss_*_title twin.
         */
        PreferenceSpec.derived(
            keyName = "enabled_newsletter_sections",
            default = NewsletterSectionType.entries.toSet(),
            resetCategory = PreferenceResetCategory.NEWSLETTER,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedEnabledNewsletterSections,
                    default = NewsletterSectionType.entries.toSet(),
                    parse = { PreferenceCodec.json.decodeFromString<Set<NewsletterSectionType>>(it) },
                    cacheRef = { cachedEnabledNewsletterSections = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
            search = PreferenceSearchSpec(
                id = "newsletter_sections",
                titleKey = "settings_newsletter_config",
                subtitleKey = "ss_newsletter_sections_subtitle",
                categoryKey = "ss_cat_appearance",
                keywords = listOf(
                    "newsletter", "sections", "recently added", "activity log", "library stats",
                    "continue watching", "next up", "curated picks", "content", "digest",
                ),
                routeKind = ROUTE_NEWSLETTER_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The section render order (JSON blob; decode memoised on the raw
         * string — see [cachedNewsletterSectionOrder]). Not searchable — the
         * order is configured through the sections-config surface whose face
         * the row above reuses; the reset review covers it under its
         * diff_newsletter_section_order row.
         */
        PreferenceSpec.derived(
            keyName = "newsletter_section_order",
            default = NewsletterSectionType.DEFAULT_ORDER,
            resetCategory = PreferenceResetCategory.NEWSLETTER,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedNewsletterSectionOrder,
                    default = NewsletterSectionType.DEFAULT_ORDER,
                    parse = { PreferenceCodec.json.decodeFromString<List<NewsletterSectionType>>(it) },
                    cacheRef = { cachedNewsletterSectionOrder = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [NotificationSlice] property order (the nested aggregate's
     * field order, then the newsletter fields). Nothing re-lists the rows: a
     * row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        notificationAggregate +
            newsletter

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — this store owns keys in
     * both `NOTIFICATIONS` (the whole notification aggregate) and `NEWSLETTER`
     * (the digest settings minus the one-time [NEWSLETTER_LAST_VIEWED_MS]
     * state, which is excluded by carrying no category). The facade aggregates
     * these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order). Nothing re-lists the
     * searchable rows — a row that declares a search entry is in the catalog
     * by construction, and one without one is out by construction (the
     * [NEWSLETTER_LAST_VIEWED_MS] viewed stamp and the
     * [NEWSLETTER_SECTION_ORDER] blob are the search-less rows). The settings
     * feature matches entries to rows BY ID, so this list's order is
     * declaration order, not catalog order: the row lists on the feature side
     * own the catalog order.
     */
    val searchEntries: List<PreferenceSearchSpec> =
        all.mapNotNull { it.search }

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val NOTIFICATIONS_ENABLED: PreferenceSpec<Boolean> get() = row("notifications_enabled")

    val NOTIFICATIONS_CHECK_FREQUENCY: PreferenceSpec<CheckFrequency> get() = row("notifications_check_frequency")

    val NOTIFICATIONS_QUIET_HOURS_ENABLED: PreferenceSpec<Boolean> get() = row("notifications_quiet_hours_enabled")

    val NOTIFICATIONS_QUIET_HOURS_START: PreferenceSpec<Int> get() = row("notifications_quiet_hours_start")

    val NOTIFICATIONS_QUIET_HOURS_END: PreferenceSpec<Int> get() = row("notifications_quiet_hours_end")

    val NOTIFICATIONS_SOUND_ENABLED: PreferenceSpec<Boolean> get() = row("notifications_sound_enabled")

    val NOTIFICATIONS_VIBRATE_ENABLED: PreferenceSpec<Boolean> get() = row("notifications_vibrate_enabled")

    val NOTIFICATIONS_LIGHTS_ENABLED: PreferenceSpec<Boolean> get() = row("notifications_lights_enabled")

    val NOTIFICATIONS_MAX_PER_CHECK: PreferenceSpec<Int> get() = row("notifications_max_per_check")

    val NOTIFICATIONS_LIBRARY_CONFIGS: PreferenceSpec<Map<String, LibraryNotificationConfig>>
        get() = row("notifications_library_configs")

    val NOTIFICATIONS_NEW_EPISODES_ENABLED: PreferenceSpec<Boolean> get() = row("notifications_new_episodes_enabled")

    val NOTIFICATIONS_SERIES_CONFIGS: PreferenceSpec<Map<String, SeriesNotificationConfig>>
        get() = row("notifications_series_configs")

    val NEWSLETTER_ENABLED: PreferenceSpec<Boolean> get() = row("newsletter_enabled")

    val NEWSLETTER_DAY_OF_WEEK: PreferenceSpec<Int> get() = row("newsletter_day_of_week")

    val NEWSLETTER_LAST_VIEWED_MS: PreferenceSpec<Long> get() = row("newsletter_last_viewed_ms")

    val ENABLED_NEWSLETTER_SECTIONS: PreferenceSpec<Set<NewsletterSectionType>> get() = row("enabled_newsletter_sections")

    val NEWSLETTER_SECTION_ORDER: PreferenceSpec<List<NewsletterSectionType>> get() = row("newsletter_section_order")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [NotificationStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "NotificationPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
