package com.raulshma.jellyplay.core.datastore.notification

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the derivation integrity for the notification + newsletter domain:
 * the [NotificationPreferenceSpecs] rows are the single declaration of every
 * key's wire name / storage type / reset category, and `NotificationStore`
 * derives its `Keys` members, reset lists (and its read machinery) from them —
 * so a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (defaults,
 * round-trips, the aggregate update) stays in [NotificationStoreTest].
 */
class NotificationSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [NotificationStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "notifications_enabled" to Boolean::class,
        "notifications_check_frequency" to String::class,
        "notifications_quiet_hours_enabled" to Boolean::class,
        "notifications_quiet_hours_start" to Int::class,
        "notifications_quiet_hours_end" to Int::class,
        "notifications_sound_enabled" to Boolean::class,
        "notifications_vibrate_enabled" to Boolean::class,
        "notifications_lights_enabled" to Boolean::class,
        "notifications_max_per_check" to Int::class,
        "notifications_library_configs" to String::class,
        "notifications_new_episodes_enabled" to Boolean::class,
        "notifications_series_configs" to String::class,
        "newsletter_enabled" to Boolean::class,
        "newsletter_day_of_week" to Int::class,
        "newsletter_last_viewed_ms" to Long::class,
        "enabled_newsletter_sections" to String::class,
        "newsletter_section_order" to String::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(NotificationStore.Keys).associateBy { it.name }
        val rows = NotificationPreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        // Each Keys member IS the row's key rebuilt from the single-declared
        // wire name (Preferences.Key equality is name-based).
        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row's write stores its declared storage's value type`() {
        // Write each row's default through its derived write (the blob rows
        // through their representative encoded value) and assert the stored
        // value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        NotificationPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(expectedStoredType.keys, storedByName.keys)
        expectedStoredType.forEach { (name, expectedType) ->
            assertEquals(
                expectedType,
                storedByName.getValue(name).value::class,
                "storage drift on $name",
            )
        }
    }

    @Test
    fun `resetKeysFor returns exactly the NOTIFICATIONS-category keys`() {
        val store = NotificationStore(TestDataStoreProvider.get(), scope)
        val notificationKeys = store.resetKeysFor(PreferenceResetCategory.NOTIFICATIONS).map { it.name }
        // The whole notification aggregate, in row order.
        assertEquals(
            listOf(
                "notifications_enabled",
                "notifications_check_frequency",
                "notifications_quiet_hours_enabled",
                "notifications_quiet_hours_start",
                "notifications_quiet_hours_end",
                "notifications_sound_enabled",
                "notifications_vibrate_enabled",
                "notifications_lights_enabled",
                "notifications_max_per_check",
                "notifications_library_configs",
                "notifications_new_episodes_enabled",
                "notifications_series_configs",
            ).sorted(),
            notificationKeys.sorted(),
        )
    }

    @Test
    fun `resetKeysFor scopes the NEWSLETTER category to its own rows`() {
        val store = NotificationStore(TestDataStoreProvider.get(), scope)
        // The digest settings minus the one-time newsletter_last_viewed_ms
        // viewed state (which carries no category).
        assertEquals(
            listOf(
                "newsletter_enabled",
                "newsletter_day_of_week",
                "enabled_newsletter_sections",
                "newsletter_section_order",
            ).sorted(),
            store.resetKeysFor(PreferenceResetCategory.NEWSLETTER).map { it.name }.sorted(),
        )
        assertEquals(
            emptyList<Preferences.Key<*>>(),
            store.resetKeysFor(PreferenceResetCategory.APPEARANCE),
        )
        assertEquals(
            emptyList<String>(),
            store.resetKeysFor(PreferenceResetCategory.NOTIFICATIONS).intersect(store.resetKeysFor(PreferenceResetCategory.NEWSLETTER).toSet()).map { it.name },
            "no key resets under both categories",
        )
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries`() {
        // 1:1 totality: the catalog is the rows' declared search specs (in
        // `all` order) — the search-less rows are the newsletter_last_viewed_ms
        // viewed stamp (not a setting) and the newsletter_section_order blob
        // (its face is the sections-config row). Nothing re-listed, nothing
        // hand-appended. Order is pinned as declaration order.
        val entries = NotificationPreferenceSpecs.searchEntries
        assertEquals(
            NotificationPreferenceSpecs.all.mapNotNull { it.search }.map { it.id },
            entries.map { it.id },
        )
        // …and no entry id is declared twice.
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [NotificationSlice] property's
        // default reads its row's `.default` (the newsletter fields) or the
        // aggregate's core.model defaults (the spec rows pin the same values
        // below — the model class cannot read the spec rows). If a future
        // edit restates a literal and the two drift, this fails.
        val slice = NotificationSlice()
        // The newsletter fields read their rows.
        assertEquals(NotificationPreferenceSpecs.NEWSLETTER_ENABLED.default, slice.newsletterEnabled)
        assertEquals(NotificationPreferenceSpecs.NEWSLETTER_DAY_OF_WEEK.default, slice.newsletterDayOfWeek)
        assertEquals(NotificationPreferenceSpecs.NEWSLETTER_LAST_VIEWED_MS.default, slice.newsletterLastViewedMs)
        assertEquals(NotificationPreferenceSpecs.ENABLED_NEWSLETTER_SECTIONS.default, slice.enabledNewsletterSections)
        assertEquals(NotificationPreferenceSpecs.NEWSLETTER_SECTION_ORDER.default, slice.newsletterSectionOrder)
        // The aggregate's defaults ARE the notification rows' declared
        // defaults (the read-policy split pins these exact values).
        val np = slice.notificationPreferences
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_ENABLED.default, np.enabled)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_CHECK_FREQUENCY.default, np.checkFrequency)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_ENABLED.default, np.quietHoursEnabled)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_START.default, np.quietHoursStart)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_QUIET_HOURS_END.default, np.quietHoursEnd)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_SOUND_ENABLED.default, np.soundEnabled)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_VIBRATE_ENABLED.default, np.vibrateEnabled)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_LIGHTS_ENABLED.default, np.lightsEnabled)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_MAX_PER_CHECK.default, np.maxPerCheck)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_LIBRARY_CONFIGS.default, np.libraryConfigs)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_NEW_EPISODES_ENABLED.default, np.newEpisodesEnabled)
        assertEquals(NotificationPreferenceSpecs.NOTIFICATIONS_SERIES_CONFIGS.default, np.seriesConfigs)
        assertEquals(CheckFrequency.EVERY_6_HOURS, np.checkFrequency)
        assertEquals(NewsletterSectionType.entries.toSet(), slice.enabledNewsletterSections)
        assertEquals(NewsletterSectionType.DEFAULT_ORDER, slice.newsletterSectionOrder)
        // Totality: the 6 pinned properties are ALL the slice's fields. A
        // field added without a spec-default pin above (and without bumping
        // this count) fails here. Statics (the @Serializable Companion,
        // Compose's $stable) don't count.
        val instanceFields = NotificationSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(6, instanceFields)
    }

    /**
     * Writes [spec]'s default through its derived write, or through the typed
     * key for the declaration-only rows (the six plain booleans — their write
     * sites are the store's hand-written ones).
     */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        if (encoding?.write != null) {
            (this as PreferenceSpec<Any>).writeTo(prefs, default)
        } else {
            prefs[typedKey() as Preferences.Key<Any>] = default as Any
        }
    }
}