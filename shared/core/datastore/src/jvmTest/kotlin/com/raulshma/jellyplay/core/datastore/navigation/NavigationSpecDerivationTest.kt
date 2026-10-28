package com.raulshma.jellyplay.core.datastore.navigation

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the bottom-navigation customisation
 * domain: the [NavigationPreferenceSpecs] rows are the single declaration of
 * every key's wire name / storage type / reset category, and
 * `NavigationStore` derives its `Keys` members, reset lists (and its
 * read/restore machinery) from them — so a wire-name, storage-type or
 * reset-category drift would silently change what is read from disk or wiped
 * on reset. These tests pin the derivation to the exact legacy wire strings;
 * the behavioral safety net (defaults, round-trips) stays in
 * [NavigationStoreTest].
 */
class NavigationSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [NavigationStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "nav_bar_show_labels" to Boolean::class,
        "hide_bottom_nav_on_scroll" to Boolean::class,
        "hidden_nav_items" to String::class,
        "nav_item_order" to String::class,
    )

    private fun expectedKey(name: String, type: KClass<*>): Preferences.Key<*> = when (type) {
        Boolean::class -> booleanPreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        else -> error("unexpected storage type for $name")
    }

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(NavigationStore.Keys).associateBy { it.name }
        val rows = NavigationPreferenceSpecs.all.associateBy { it.keyName }

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
        // stored value's runtime type matches the pinned legacy storage table
        // (the playback pin — every row here carries a derived write, the two
        // blobs' encodes included).
        val prefs = emptyPreferences().toMutablePreferences()
        NavigationPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
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
    fun `resetKeysFor returns exactly the HOME_DISCOVERY-category keys`() {
        val store = NavigationStore(TestDataStoreProvider.get(), scope)
        val homeKeys = store.resetKeysFor(PreferenceResetCategory.HOME_DISCOVERY).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried — the
        // nav keys split out of the legacy HOME_DISCOVERY bucket.
        assertEquals(expectedStoredType.keys.sorted(), homeKeys.sorted())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.HOME_DISCOVERY }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries`() {
        // The two toggles carry the domain's only search faces (reusing the
        // hand fused rows' ss_* keys and the one-key-family fold); the
        // customization blobs are sheet-managed data. 1:1 totality, and no
        // entry id is declared twice.
        val entries = NavigationPreferenceSpecs.searchEntries
        assertEquals(
            NavigationPreferenceSpecs.all.mapNotNull { it.search }.map { it.id },
            entries.map { it.id },
        )
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [NavigationSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails.
        val slice = NavigationSlice()
        assertEquals(NavigationPreferenceSpecs.NAV_BAR_SHOW_LABELS.default, slice.navBarShowLabels)
        assertEquals(NavigationPreferenceSpecs.HIDE_BOTTOM_NAV_ON_SCROLL.default, slice.hideBottomNavOnScroll)
        assertEquals(NavigationPreferenceSpecs.HIDDEN_NAV_ITEMS.default, slice.hiddenNavItems)
        assertEquals(NavigationPreferenceSpecs.NAV_ITEM_ORDER.default, slice.navItemOrder)
        // Totality: the 4 pinned properties are ALL the slice's fields (4
        // rows, every row slice-bound). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = NavigationSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(4, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
