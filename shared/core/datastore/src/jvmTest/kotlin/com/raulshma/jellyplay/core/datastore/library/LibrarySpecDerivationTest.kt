package com.raulshma.jellyplay.core.datastore.library

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
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
 * Guards the derivation integrity for the library domain: the
 * [LibraryPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `LibraryStore` derives its
 * `Keys` members, reset lists (and its read/restore machinery) from them — so
 * a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (defaults,
 * round-trips, the override-map merges) stays in [LibraryStoreTest].
 */
class LibrarySpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [LibraryStore.Keys] members — the exact expectations
     * the hand-written pre-spec store encoded at every read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "library_view_mode" to String::class,
        "default_library_sort_orders" to String::class,
        "library_view_modes" to String::class,
        "library_filters" to String::class,
        "hide_episode_thumbnails" to Boolean::class,
        "episodes_descending" to Boolean::class,
        "skip_specials" to Boolean::class,
        "compact_episode_list" to Boolean::class,
        "show_detail_up_next" to Boolean::class,
        "show_missing_episodes" to Boolean::class,
        "library_poster_size" to Float::class,
        "prefer_logos" to Boolean::class,
        "library_group_by" to String::class,
        "confirm_library_reset" to Boolean::class,
        "show_external_links" to Boolean::class,
        "show_tagline" to Boolean::class,
        "prefer_local_trailers" to Boolean::class,
        "show_more_in_collection" to Boolean::class,
        "show_upcoming_strip" to Boolean::class,
        "play_theme_music" to Boolean::class,
        "show_reviews" to Boolean::class,
        "show_episode_stream_badges" to Boolean::class,
        "show_filmography_roles" to Boolean::class,
        "show_frequent_costars" to Boolean::class,
        "trailer_preview_on_focus" to Boolean::class,
        "show_promoted_actions_landscape" to Boolean::class,
        "show_download_progress" to Boolean::class,
    )

    private fun expectedKey(name: String, type: KClass<*>): Preferences.Key<*> = when (type) {
        Boolean::class -> booleanPreferencesKey(name)
        Float::class -> floatPreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        else -> error("unexpected storage type for $name")
    }

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(LibraryStore.Keys).associateBy { it.name }
        val rows = LibraryPreferenceSpecs.all.associateBy { it.keyName }

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
    fun `every row rebuilds its legacy storage type`() {
        // The declaration-only confirm-reset row has no derived write, so the
        // STORAGE pin is the typed key each row rebuilds from its wire name
        // (Preferences.Key equality is class-sensitive, so this pins the slot
        // type per row — the home domain's storage-pin shape).
        LibraryPreferenceSpecs.all.forEach { spec ->
            assertEquals(
                expectedKey(spec.keyName, expectedStoredType.getValue(spec.keyName)),
                spec.typedKey(),
                "storage drift on ${spec.keyName}",
            )
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each derived-write row's default through its derived write and
        // assert the stored value's runtime type matches the pinned legacy
        // storage table (the playback pin, scoped to the rows that carry an
        // encoding — the declaration-only confirm-reset row is pinned by the
        // storage-type test above).
        val prefs = emptyPreferences().toMutablePreferences()
        LibraryPreferenceSpecs.all
            .filter { it.encoding?.write != null }
            .forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(
            expectedStoredType.keys - "confirm_library_reset",
            storedByName.keys,
        )
        expectedStoredType
            .filterKeys { it != "confirm_library_reset" }
            .forEach { (name, expectedType) ->
                assertEquals(
                    expectedType,
                    storedByName.getValue(name).value::class,
                    "storage drift on $name",
                )
            }
    }

    @Test
    fun `resetKeysFor returns exactly the HOME_DISCOVERY-category keys`() {
        val store = LibraryStore(TestDataStoreProvider.get(), scope)
        val homeKeys = store.resetKeysFor(PreferenceResetCategory.HOME_DISCOVERY).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried — every
        // key this store owns sits in the legacy HOME_DISCOVERY bucket.
        assertEquals(expectedStoredType.keys.sorted(), homeKeys.sorted())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.HOME_DISCOVERY }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is empty - the search faces stay the feature-side residuals`() {
        // No row declares a search face (the appearance screen's
        // "Library & Cards" group owns the domain's catalog rows), so the
        // derived list is empty by construction — and no entry id is declared
        // twice (trivially true, pinned so a future search-bearing row cannot
        // land without this test noticing the shape change).
        val entries = LibraryPreferenceSpecs.searchEntries
        assertEquals(LibraryPreferenceSpecs.all.mapNotNull { it.search }, entries)
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [LibrarySlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails. LIBRARY_POSTER_SIZE's row default is the
        // unclamped 1.0 multiplier, which is what the slice always served.
        val slice = LibrarySlice()
        assertEquals(LibraryPreferenceSpecs.LIBRARY_VIEW_MODE.default, slice.libraryViewMode)
        assertEquals(LibraryPreferenceSpecs.DEFAULT_LIBRARY_SORT_ORDERS.default, slice.defaultLibrarySortOrders)
        assertEquals(LibraryPreferenceSpecs.LIBRARY_VIEW_MODES.default, slice.libraryViewModes)
        assertEquals(LibraryPreferenceSpecs.LIBRARY_FILTERS.default, slice.libraryFilters)
        assertEquals(LibraryPreferenceSpecs.HIDE_EPISODE_THUMBNAILS.default, slice.hideEpisodeThumbnails)
        assertEquals(LibraryPreferenceSpecs.EPISODES_DESCENDING.default, slice.episodesDescending)
        assertEquals(LibraryPreferenceSpecs.SKIP_SPECIALS.default, slice.skipSpecials)
        assertEquals(LibraryPreferenceSpecs.COMPACT_EPISODE_LIST.default, slice.compactEpisodeList)
        assertEquals(LibraryPreferenceSpecs.SHOW_DETAIL_UP_NEXT.default, slice.showDetailUpNext)
        assertEquals(LibraryPreferenceSpecs.SHOW_MISSING_EPISODES.default, slice.showMissingEpisodes)
        assertEquals(LibraryPreferenceSpecs.LIBRARY_POSTER_SIZE.default, slice.libraryPosterSize)
        assertEquals(LibraryPreferenceSpecs.PREFER_LOGOS.default, slice.preferLogos)
        assertEquals(LibraryPreferenceSpecs.LIBRARY_GROUP_BY.default, slice.libraryGroupBy)
        assertEquals(LibraryPreferenceSpecs.CONFIRM_LIBRARY_RESET.default, slice.confirmLibraryReset)
        assertEquals(LibraryPreferenceSpecs.SHOW_EXTERNAL_LINKS.default, slice.showExternalLinks)
        assertEquals(LibraryPreferenceSpecs.SHOW_TAGLINE.default, slice.showTagline)
        assertEquals(LibraryPreferenceSpecs.PREFER_LOCAL_TRAILERS.default, slice.preferLocalTrailers)
        assertEquals(LibraryPreferenceSpecs.SHOW_MORE_IN_COLLECTION.default, slice.showMoreInCollection)
        assertEquals(LibraryPreferenceSpecs.SHOW_UPCOMING_STRIP.default, slice.showUpcomingStrip)
        assertEquals(LibraryPreferenceSpecs.PLAY_THEME_MUSIC.default, slice.playThemeMusic)
        assertEquals(LibraryPreferenceSpecs.SHOW_REVIEWS.default, slice.showReviews)
        assertEquals(LibraryPreferenceSpecs.SHOW_EPISODE_STREAM_BADGES.default, slice.showEpisodeStreamBadges)
        assertEquals(LibraryPreferenceSpecs.SHOW_FILMOGRAPHY_ROLES.default, slice.showFilmographyRoles)
        assertEquals(LibraryPreferenceSpecs.SHOW_FREQUENT_COSTARS.default, slice.showFrequentCostars)
        assertEquals(LibraryPreferenceSpecs.TRAILER_PREVIEW_ON_FOCUS.default, slice.trailerPreviewOnFocus)
        assertEquals(LibraryPreferenceSpecs.SHOW_PROMOTED_ACTIONS_LANDSCAPE.default, slice.showPromotedActionsLandscape)
        assertEquals(LibraryPreferenceSpecs.SHOW_DOWNLOAD_PROGRESS.default, slice.showDownloadProgress)
        // Totality: the 27 pinned properties are ALL the slice's fields (27
        // rows, every row slice-bound). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = LibrarySlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(27, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
