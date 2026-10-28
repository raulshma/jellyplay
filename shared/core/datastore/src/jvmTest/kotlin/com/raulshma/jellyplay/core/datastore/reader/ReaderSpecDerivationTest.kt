package com.raulshma.jellyplay.core.datastore.reader

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Guards the derivation integrity for the reader domain: the
 * [ReaderPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / (non-)reset participation, and `ReaderStore`
 * derives its `Keys` members, reset list (and its read/enum-write machinery)
 * from them — so a wire-name, storage-type or reset-category drift would
 * silently change what is read from disk or wiped on reset. These tests pin
 * the derivation to the exact legacy wire strings; the behavioral safety net
 * (defaults, round-trips, clamps, corrupt-blob degradation) stays in
 * [ReaderStoreTest].
 */
class ReaderSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [ReaderStore.Keys] members — the exact expectations
     * the hand-written pre-spec store encoded at every read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "reader_reading_directions" to String::class,
        "reader_reading_layouts" to String::class,
        "reader_theme" to String::class,
        "reader_font_size_px" to Int::class,
        "reader_font_family" to String::class,
        "reader_line_height_pct" to Int::class,
        "reader_margin_pct" to Int::class,
        "reader_justify" to Boolean::class,
        "reader_scroll_mode" to Boolean::class,
        "reader_brightness_pct" to Int::class,
        "reader_volume_key_paging" to Boolean::class,
        "reader_animated_page_turns" to Boolean::class,
        "reader_toc_rail" to Boolean::class,
        "reader_speech_rate" to Int::class,
        "reader_speech_pitch" to Int::class,
        "reader_reading_speed_wpm" to Int::class,
        "reader_auto_scroll_speed_px" to Int::class,
        "reader_per_book_appearance" to String::class,
        "reader_last_cfis" to String::class,
    )

    /**
     * The rows carrying the documented write opt-out (the five plain boolean
     * declarations — their keys were born outside `PreferenceCodec`'s
     * migration lists, so they carry no derived encoding — and the four
     * per-book map rows whose writes are the store's read-modify-write verbs),
     * each with a representative wire value the storage pin writes the way
     * the store writes it.
     */
    private val optOutRepresentativeWrites: Map<String, Any> = mapOf(
        "reader_justify" to true,
        "reader_scroll_mode" to false,
        "reader_volume_key_paging" to true,
        "reader_animated_page_turns" to false,
        "reader_toc_rail" to true,
        "reader_reading_directions" to "{}",
        "reader_reading_layouts" to "{}",
        "reader_per_book_appearance" to "{}",
        "reader_last_cfis" to "{}",
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(ReaderStore.Keys).associateBy { it.name }
        val rows = ReaderPreferenceSpecs.all.associateBy { it.keyName }

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
        // Write each row's default through its derived write (the opt-out rows
        // through their representative verb-shaped value) and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        ReaderPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
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
    fun `resetKeysFor is empty for every category - the reader domain sits in no reset category`() {
        // Every reader row declares no PreferenceResetCategory (the reading
        // experience plus per-book runtime state is outside the facade's
        // category-reset machinery), so no category may ever carry a reader
        // key — a future row that names one is a deliberate reset-scope change
        // and must update this pin.
        PreferenceResetCategory.entries.forEach { category ->
            assertEquals(
                emptyList<String>(),
                ReaderPreferenceSpecs.resetKeysFor(category).map { it.name },
                "$category carries no reader key",
            )
        }
    }

    @Test
    fun `resetKeys is every declared row in slice order`() {
        // The full-store list (`clearAll`'s wipe set) derives from the rows —
        // a row added to a group is wiped by construction; a hand list drifting
        // out of the groups fails here. Order pinned as declaration order
        // (= the pre-spec hand list's order).
        val store = ReaderStore(TestDataStoreProvider.get(), scope)
        assertEquals(
            listOf(
                "reader_reading_directions",
                "reader_reading_layouts",
                "reader_theme",
                "reader_font_size_px",
                "reader_font_family",
                "reader_line_height_pct",
                "reader_margin_pct",
                "reader_justify",
                "reader_scroll_mode",
                "reader_brightness_pct",
                "reader_volume_key_paging",
                "reader_animated_page_turns",
                "reader_toc_rail",
                "reader_speech_rate",
                "reader_speech_pitch",
                "reader_reading_speed_wpm",
                "reader_auto_scroll_speed_px",
                "reader_per_book_appearance",
                "reader_last_cfis",
            ),
            store.resetKeys.map { it.name },
        )
    }

    @Test
    fun `searchEntries is empty - no reader row declares a search face`() {
        // The reader knobs are adjusted on the reader's own sheets (the
        // book-player feature), not through settings search; a future
        // searchable row needs its PreferenceSearchSpec on the row.
        assertEquals(emptyList(), ReaderPreferenceSpecs.searchEntries)
        assertEquals(0, ReaderPreferenceSpecs.all.count { it.search != null })
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [ReaderSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails.
        val slice = ReaderSlice()
        assertEquals(ReaderPreferenceSpecs.READING_DIRECTIONS.default, slice.readingDirections)
        assertEquals(ReaderPreferenceSpecs.READER_READING_LAYOUTS.default, slice.readingLayouts)
        assertEquals(ReaderPreferenceSpecs.READER_THEME.default, slice.readerTheme)
        assertEquals(ReaderPreferenceSpecs.READER_FONT_SIZE_PX.default, slice.readerFontSizePx)
        assertEquals(ReaderPreferenceSpecs.READER_FONT_FAMILY.default, slice.fontFamily)
        assertEquals(ReaderPreferenceSpecs.READER_LINE_HEIGHT_PCT.default, slice.lineHeightPct)
        assertEquals(ReaderPreferenceSpecs.READER_MARGIN_PCT.default, slice.marginPct)
        assertEquals(ReaderPreferenceSpecs.READER_JUSTIFY.default, slice.justify)
        assertEquals(ReaderPreferenceSpecs.READER_SCROLL_MODE.default, slice.scrollMode)
        assertEquals(ReaderPreferenceSpecs.READER_BRIGHTNESS_PCT.default, slice.brightnessPct)
        assertEquals(ReaderPreferenceSpecs.READER_VOLUME_KEY_PAGING.default, slice.volumeKeyPaging)
        assertEquals(ReaderPreferenceSpecs.READER_ANIMATED_PAGE_TURNS.default, slice.animatedPageTurns)
        assertEquals(ReaderPreferenceSpecs.READER_TOC_RAIL.default, slice.tocRailVisible)
        assertEquals(ReaderPreferenceSpecs.READER_SPEECH_RATE.default, slice.speechRate)
        assertEquals(ReaderPreferenceSpecs.READER_SPEECH_PITCH.default, slice.speechPitch)
        assertEquals(ReaderPreferenceSpecs.READER_READING_SPEED_WPM.default, slice.readingSpeedWpm)
        assertEquals(ReaderPreferenceSpecs.READER_AUTO_SCROLL_SPEED_PX.default, slice.autoScrollSpeedPxPerSec)
        assertEquals(ReaderPreferenceSpecs.READER_PER_BOOK_APPEARANCE.default, slice.perBookAppearance)
        assertEquals(ReaderPreferenceSpecs.READER_LAST_CFIS.default, slice.lastCfis)
        // Totality: the 19 pinned properties are ALL the slice's fields. A
        // field added without a spec-default pin above (and without bumping
        // this count) fails here. Statics (the @Serializable Companion,
        // Compose's $stable) don't count.
        val instanceFields = ReaderSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(19, instanceFields)
    }

    @Test
    fun `the born-typed boolean rows keep plain declarations beside the encoded rows`() {
        // Read-policy pin: the five booleans were born outside
        // PreferenceCodec's legacy migration lists (no string slot can ever
        // have existed under their names), so their rows are the plain
        // declarations (generic readStored, no derived encoding) — while the
        // enum / int / JSON-map rows keep their derived encodings.
        val plainRowNames = setOf(
            "reader_justify",
            "reader_scroll_mode",
            "reader_volume_key_paging",
            "reader_animated_page_turns",
            "reader_toc_rail",
        )
        assertEquals(5, plainRowNames.size)
        ReaderPreferenceSpecs.all.forEach { spec ->
            if (spec.keyName in plainRowNames) {
                assertNull(
                    spec.encoding,
                    "${spec.keyName} stays a plain declaration — no encoded read for a born-typed boolean",
                )
            } else {
                assertNotNull(spec.encoding, "${spec.keyName} declares its encoding")
            }
        }
    }

    /** Writes [spec]'s default (opt-out rows via their representative wire value). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        if (encoding?.write != null) {
            (this as PreferenceSpec<Any>).writeTo(prefs, default)
        } else {
            val representative = optOutRepresentativeWrites.getValue(keyName)
            prefs[typedKey() as Preferences.Key<Any>] = representative
        }
    }
}
