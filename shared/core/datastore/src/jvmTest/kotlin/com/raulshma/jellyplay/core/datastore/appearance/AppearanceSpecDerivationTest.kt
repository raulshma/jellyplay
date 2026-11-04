package com.raulshma.jellyplay.core.datastore.appearance

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the appearance domain: the
 * [AppearancePreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / default / reset category, and `AppearanceStore`
 * derives its `Keys` members, reset lists (and its read/restore machinery)
 * from them — so a wire-name, storage-type or reset-category drift would
 * silently change what is read from disk or wiped on reset. These tests pin
 * the derivation to the exact legacy strings the pre-spec hand-written store
 * carried; the behavioral safety net (defaults, round-trips, the legacy
 * theme-variant booleans) stays in [AppearanceStoreTest].
 */
class AppearanceSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [AppearanceStore.Keys] members — the exact expectations
     * the hand-written pre-spec store encoded at every read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "theme_mode" to String::class,
        "contrast_level" to String::class,
        "dynamic_theming" to Boolean::class,
        "oled_mode" to Boolean::class,
        "accent_color_swatch" to String::class,
        "color_style" to String::class,
        "performance_mode" to Boolean::class,
        "performance_level" to String::class,
        "perf_animations" to String::class,
        "perf_shared_elements" to String::class,
        "perf_nav_transitions" to String::class,
        "perf_image_decode_cap" to String::class,
        "perf_image_crossfade" to String::class,
        "perf_blurhash" to String::class,
        "perf_quality_badges" to String::class,
        "perf_ambient_hero_video" to String::class,
        "perf_backdrop_blur" to String::class,
        "perf_screensaver_motion" to String::class,
        "image_memory_cache_percent" to Int::class,
        "reduce_motion_enabled" to Boolean::class,
        "theme_variant" to String::class,
        "synthwave_mode" to Boolean::class,
        "synthwave_accent" to String::class,
        "soothing_mode" to Boolean::class,
        "soothing_accent" to String::class,
        "monochrome_mode" to Boolean::class,
        "vivid_accent" to String::class,
        "aurora_accent" to String::class,
        "sakura_accent" to String::class,
        "vector_pop_accent" to String::class,
        "backdrop_theme_music_enabled" to Boolean::class,
        "details_awards_badges_enabled" to Boolean::class,
        "blue_light_filter_enabled" to Boolean::class,
        "blue_light_filter_strength" to Float::class,
        "date_format_preference" to String::class,
        "app_font_scale" to String::class,
        "scheduled_theme_start_hour" to Int::class,
        "scheduled_theme_end_hour" to Int::class,
        "color_blind_mode" to String::class,
        "hand_mode" to String::class,
        "layout_mode" to String::class,
        "tv_overscan" to String::class,
        "haptics_enabled" to Boolean::class,
        "show_advanced_settings" to Boolean::class,
        "time_use_24h" to Boolean::class,
        "card_press_animations" to Boolean::class,
        "pull_to_refresh_enabled" to Boolean::class,
        "app_backdrop_dim" to String::class,
        "haptics_intensity" to String::class,
        "image_quality_preset" to String::class,
        "app_list_density" to String::class,
        "app_press_effect" to String::class,
        "app_flat_cards" to Boolean::class,
        "app_overscroll_style" to String::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(AppearanceStore.Keys).associateBy { it.name }
        val rows = AppearancePreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        // Each Keys member IS the row's key rebuilt from the single-declared
        // wire name (Preferences.Key equality is name-based) — the migration
        // fallback consults keyName itself, so this equality is what keeps a
        // stored value readable across the derivation.
        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each row's default through its derived write and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        AppearancePreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
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
    fun `resetKeysFor returns exactly the APPEARANCE-category keys`() {
        val store = AppearanceStore(TestDataStoreProvider.get(), scope)
        val appearanceKeys = store.resetKeysFor(PreferenceResetCategory.APPEARANCE).map { it.name }
        // The exact pre-spec reset list plus the tv_overscan spec row and the
        // performance rows (the three legacy theme booleans and their accents,
        // and the legacy performance_mode boolean, included — the legacy
        // surfaces still reset with their category).
        assertEquals(
            listOf(
                "theme_mode",
                "contrast_level",
                "dynamic_theming",
                "oled_mode",
                "accent_color_swatch",
                "color_style",
                "performance_mode",
                "performance_level",
                "perf_animations",
                "perf_shared_elements",
                "perf_nav_transitions",
                "perf_image_decode_cap",
                "perf_image_crossfade",
                "perf_blurhash",
                "perf_quality_badges",
                "perf_ambient_hero_video",
                "perf_backdrop_blur",
                "perf_screensaver_motion",
                "image_memory_cache_percent",
                "reduce_motion_enabled",
                "theme_variant",
                "synthwave_mode",
                "synthwave_accent",
                "soothing_mode",
                "soothing_accent",
                "monochrome_mode",
                "vivid_accent",
                "aurora_accent",
                "sakura_accent",
                "vector_pop_accent",
                "backdrop_theme_music_enabled",
                "details_awards_badges_enabled",
                "blue_light_filter_enabled",
                "blue_light_filter_strength",
                "date_format_preference",
                "app_font_scale",
                "scheduled_theme_start_hour",
                "scheduled_theme_end_hour",
                "color_blind_mode",
                "hand_mode",
                "layout_mode",
                "tv_overscan",
                "time_use_24h",
                "card_press_animations",
                "app_backdrop_dim",
                "haptics_intensity",
                "image_quality_preset",
                "app_list_density",
                "app_press_effect",
                "app_flat_cards",
                "app_overscroll_style",
            ).sorted(),
            appearanceKeys.sorted(),
        )
    }

    @Test
    fun `haptics resets under MISC_APP and advanced-settings contributes to no category here`() {
        val store = AppearanceStore(TestDataStoreProvider.get(), scope)
        assertEquals(
            listOf("haptics_enabled", "pull_to_refresh_enabled").sorted(),
            store.resetKeysFor(PreferenceResetCategory.MISC_APP).map { it.name }.sorted(),
        )
        // The reset-owner ledger: show_advanced_settings' EXPERIMENTAL list
        // entry is ExperimentalStore's, exactly as the hand-written lists did.
        assertEquals(
            emptyList<androidx.datastore.preferences.core.Preferences.Key<*>>(),
            store.resetKeysFor(PreferenceResetCategory.EXPERIMENTAL),
        )
        assertNull(AppearancePreferenceSpecs.SHOW_ADVANCED_SETTINGS.resetCategory)
        // No row lands in any category but APPEARANCE and MISC_APP.
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.APPEARANCE && it != PreferenceResetCategory.MISC_APP }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    /**
     * Pins ONE knob's full row — key, default, restore-encoding round-trip and
     * reset category — derived from the spec (the write-through pin the spec
     * KDoc calls for, mirroring the playback domain's derivation test).
     */
    @Test
    fun `blue light filter strength full row is derived from its spec`() {
        val row = AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_STRENGTH

        // Key: the row's rebuilt typed key IS the store's Keys member.
        assertEquals("blue_light_filter_strength", row.keyName)
        assertEquals(AppearanceStore.Keys.BLUE_LIGHT_FILTER_STRENGTH, row.typedKey())
        // Default: served on absence.
        assertEquals(0.3f, row.default)
        assertEquals(0.3f, row.readFrom(emptyPreferences()))
        // Restore: the derived write is the inverse of the read.
        val prefs = emptyPreferences().toMutablePreferences()
        row.writeTo(prefs, 0.7f)
        assertEquals(0.7f, row.readFrom(prefs))
        // Legacy string fallback: the pre-typed-era wire name IS keyName.
        val legacy = emptyPreferences().toMutablePreferences()
        legacy[androidx.datastore.preferences.core.stringPreferencesKey("blue_light_filter_strength")] = "0.9"
        assertEquals(0.9f, row.readFrom(legacy))
        // Reset: participates in the APPEARANCE category, no other.
        assertEquals(
            listOf(row.typedKey()),
            AppearancePreferenceSpecs.resetKeysFor(PreferenceResetCategory.APPEARANCE)
                .filter { it.name == row.keyName },
        )
    }

    /**
     * Pins the master performance row — key, default, the legacy-boolean
     * migration read, the name-encoding round-trip, the corruption default
     * and the reset category — derived from the spec (the write-through pin
     * the spec KDoc calls for).
     */
    @Test
    fun `performance level full row is derived from its spec`() {
        val row = AppearancePreferenceSpecs.PERFORMANCE_LEVEL

        // Key: the row's rebuilt typed key IS the store's Keys member, and
        // the legacy boolean row beside it keeps its own key. (Compared by
        // name: Preferences.Key equality is name-based, but the row's value
        // type parameter differs from the Keys member's declared one.)
        assertEquals("performance_level", row.keyName)
        assertEquals(AppearanceStore.Keys.PERFORMANCE_LEVEL.name, row.typedKey().name)
        assertEquals(
            AppearanceStore.Keys.PERFORMANCE_MODE.name,
            AppearancePreferenceSpecs.PERFORMANCE_MODE.typedKey().name,
        )
        // Default: served on absence (with no legacy boolean either).
        assertEquals(PerformanceLevel.STANDARD, row.default)
        assertEquals(PerformanceLevel.STANDARD, row.readFrom(emptyPreferences()))
        // Migration read: absent level key + legacy `performance_mode=true`
        // maps up to PERFORMANCE; false stays STANDARD.
        val legacyOn = emptyPreferences().toMutablePreferences()
        legacyOn[androidx.datastore.preferences.core.booleanPreferencesKey("performance_mode")] = true
        assertEquals(PerformanceLevel.PERFORMANCE, row.readFrom(legacyOn))
        val legacyOff = emptyPreferences().toMutablePreferences()
        legacyOff[androidx.datastore.preferences.core.booleanPreferencesKey("performance_mode")] = false
        assertEquals(PerformanceLevel.STANDARD, row.readFrom(legacyOff))
        // Round-trip: the derived write persists the enum NAME.
        val stored = emptyPreferences().toMutablePreferences()
        row.writeTo(stored, PerformanceLevel.BALANCED)
        assertEquals(
            "BALANCED",
            stored[androidx.datastore.preferences.core.stringPreferencesKey("performance_level")],
        )
        assertEquals(PerformanceLevel.BALANCED, row.readFrom(stored))
        // Corruption: an unparseable name serves the default (no legacy
        // re-entry).
        val corrupt = emptyPreferences().toMutablePreferences()
        corrupt[androidx.datastore.preferences.core.stringPreferencesKey("performance_level")] = "nonsense"
        assertEquals(PerformanceLevel.STANDARD, row.readFrom(corrupt))
        // Reset: participates in the APPEARANCE category, no other.
        assertEquals(
            listOf(row.typedKey()),
            AppearancePreferenceSpecs.resetKeysFor(PreferenceResetCategory.APPEARANCE)
                .filter { it.name == row.keyName },
        )
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
