package com.raulshma.jellyplay.core.datastore.screensaver

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the screensaver / dream domain:
 * the [ScreensaverPreferenceSpecs] rows are the single declaration of every
 * key's wire name / storage type / default / reset category, and
 * `ScreensaverStore` derives its `Keys` members, reset lists (and its
 * read/restore machinery) from them — so a wire-name, storage-type or
 * reset-category drift would silently change what is read from disk or wiped
 * on reset. These tests pin the derivation to the exact legacy strings the
 * pre-spec hand-written store carried; the behavioral safety net (defaults,
 * round-trips, the parental-rating codec, the coercions) stays in
 * [ScreensaverStoreTest].
 */
class ScreensaverSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "dream_image_categories" to String::class,
        "dream_slideshow_interval_ms" to Long::class,
        "dream_ken_burns_enabled" to Boolean::class,
        "dream_transition_style" to String::class,
        "dream_show_title" to Boolean::class,
        "dream_max_parental_rating" to Int::class,
        "dream_dim_after_ms" to Long::class,
        "dream_dim_percent" to Int::class,
        "idle_ambient_enabled" to Boolean::class,
        "idle_ambient_timeout_min" to Long::class,
        "discord_presence_enabled" to Boolean::class,
        "hooks_enabled" to Boolean::class,
        "hooks_play_cmd" to String::class,
        "hooks_stop_cmd" to String::class,
        "hooks_ended_cmd" to String::class,
        "hooks_idle_cmd" to String::class,
        "hooks_idle_ended_cmd" to String::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(ScreensaverStore.Keys).associateBy { it.name }
        val rows = ScreensaverPreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each row's default through its derived write and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        ScreensaverPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
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
    fun `resetKeysFor returns exactly the SCREENSAVER-category keys`() {
        val store = ScreensaverStore(TestDataStoreProvider.get(), scope)
        val screensaverKeys = store.resetKeysFor(PreferenceResetCategory.SCREENSAVER).map { it.name }
        // The exact 17 keys the hand-written pre-spec reset list carried.
        assertEquals(expectedStoredType.keys.sorted(), screensaverKeys.sorted())
        // Every key owned here sits in the single SCREENSAVER bucket.
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.SCREENSAVER }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    /**
     * Pins ONE knob's full row — key, default, JSON restore-encoding
     * round-trip and reset category — derived from the spec (the write-through
     * pin the spec KDoc calls for, mirroring the playback domain's derivation
     * test). The dream image categories are the domain's JSON row, so the
     * round-trip also proves the blob encoding is the row's own.
     */
    @Test
    fun `dream image categories full row is derived from its spec`() {
        val row = ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES

        // Key: the row's rebuilt typed key IS the store's Keys member. Both
        // sides widen to Preferences.Key so the pin is runtime (name-based)
        // equality — what reset lists and prefs interop rely on.
        assertEquals("dream_image_categories", row.keyName)
        assertEquals<Preferences.Key<*>>(
            ScreensaverStore.Keys.DREAM_IMAGE_CATEGORIES,
            row.typedKey(),
        )
        // Default: the movies+series set, served on absence.
        assertEquals(DEFAULT_DREAM_IMAGE_CATEGORIES, row.default)
        assertEquals(DEFAULT_DREAM_IMAGE_CATEGORIES, row.readFrom(emptyPreferences()))
        // Restore: the derived write re-encodes the JSON blob the former
        // setter wrote; reading it back decodes through the same row.
        val categories = setOf(DreamImageCategory.MUSIC, DreamImageCategory.PHOTOS)
        val prefs = emptyPreferences().toMutablePreferences()
        row.writeTo(prefs, categories)
        assertEquals(categories, row.readFrom(prefs))
        // The stored slot is the raw JSON string under the legacy wire name.
        val raw = prefs.asMap().entries.single { it.key.name == "dream_image_categories" }
        assertEquals(String::class, raw.value::class)
        // Reset: participates in the SCREENSAVER category.
        assertTrue(
            ScreensaverPreferenceSpecs.resetKeysFor(PreferenceResetCategory.SCREENSAVER)
                .contains(row.typedKey()),
        )
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
