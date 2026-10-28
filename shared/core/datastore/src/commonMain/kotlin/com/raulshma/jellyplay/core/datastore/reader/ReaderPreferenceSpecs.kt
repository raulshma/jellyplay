package com.raulshma.jellyplay.core.datastore.reader

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.serialization.Serializable

/**
 * The four JSON carriers + their lenient decodes, moved here from
 * `ReaderStore.kt` with the rows they belong to (the player-engine domain's
 * cache-move precedent): the decode is a pure function of the raw string, so
 * the row's derived read and the store's read-modify-write verb share the one
 * decoder beside the declaration. The carriers stay separate classes so the
 * stored JSON field names (`directions`, `layouts`, …) never change. Reader
 * keys were born in the typed-key era (they sit outside `PreferenceCodec`'s
 * migration lists), so the decodes have no legacy dance — a blank or
 * undecodable blob is an empty map, exactly as the pre-spec hand read always
 * degraded.
 */
/** JSON carrier so the direction map round-trips through the shared lenient codec. */
@Serializable
internal data class EncodedDirections(val directions: Map<String, ReadingDirection>)

/** JSON carrier so the per-book layout map round-trips through the shared lenient codec. */
@Serializable
internal data class EncodedLayouts(val layouts: Map<String, ReadingLayout>)

/** JSON carrier so the per-book appearance map round-trips through the shared lenient codec. */
@Serializable
internal data class EncodedPerBookAppearance(val appearances: Map<String, PerBookAppearance>)

/** JSON carrier so the last-CFI map round-trips through the shared lenient codec. */
@Serializable
internal data class EncodedLastCfis(val cfis: Map<String, String>)

/** Lenient carrier decode shared by all map prefs: a blank or undecodable blob is an empty map. */
internal inline fun <T> decodeMap(raw: String?, extract: (String) -> Map<String, T>): Map<String, T> =
    if (raw.isNullOrBlank()) emptyMap()
    else runCatching { extract(raw) }.getOrDefault(emptyMap())

internal fun decodeDirections(raw: String?): Map<String, ReadingDirection> =
    decodeMap(raw) { PreferenceCodec.json.decodeFromString<EncodedDirections>(it).directions }

internal fun decodeLayouts(raw: String?): Map<String, ReadingLayout> =
    decodeMap(raw) { PreferenceCodec.json.decodeFromString<EncodedLayouts>(it).layouts }

internal fun decodePerBookAppearance(raw: String?): Map<String, PerBookAppearance> =
    decodeMap(raw) { PreferenceCodec.json.decodeFromString<EncodedPerBookAppearance>(it).appearances }

internal fun decodeLastCfis(raw: String?): Map<String, String> =
    decodeMap(raw) { PreferenceCodec.json.decodeFromString<EncodedLastCfis>(it).cfis }

/**
 * The reader domain's single preference declaration: one [PreferenceSpec] row
 * per key `ReaderStore` owns — the canonical persisted key name, the default,
 * the reset category, and the read/write encoding ([PreferenceSpec.enumRow] /
 * [plainInt] / [derived] / the plain declarations).
 *
 * **Read policy:** every reader key was born in the typed-key era — none sit
 * in `PreferenceCodec`'s legacy migration lists — so the rows whose pre-spec
 * hand read was the plain `prefs[key] ?: default` keep a plain declaration
 * ([PreferenceSpec.boolean]; the generic [PreferenceSpec.readStored]) instead
 * of the encoded `plainBoolean`, and the eight clamped-int rows use
 * [plainInt] (the only INT-storage declaration; its dormant legacy-string
 * fallback can never match a key no string slot ever existed under — the
 * audioeffects `volume_boost_*` precedent). The int rows declare their `min`/
 * `max` (the store visibly clamps, setter AND read); the read-time clamp
 * itself stays in the store's `read()` — [plainInt] has no read transform, and
 * the store keeps the decode defense independent of the setters, exactly as
 * the pre-spec `decodeClampedInt` did.
 *
 * **Reset:** every row carries `resetCategory = null` — the reader domain sits
 * in no facade reset category (its keys are the reading experience plus
 * per-book runtime state), so [resetKeysFor] is empty for every category and
 * the store's own `clearAll` derives from [all] instead.
 *
 * **The documented opt-outs (encode = null):** the four per-book map rows'
 * writes are never single-slot — every write site is the store's
 * decode→edit→encode transaction ([ReaderStore]'s `EncodedMapPreference`
 * verbs: upsert / remove-one-key), preserving the other books' entries in the
 * same atomic edit. ADR-0003 local-first: the resume-CFI map is deliberately
 * install-local exact-resume state (the marks themselves live in the
 * annotations repository, not this store), and the whole reader slice
 * participates in no sync wire or backup envelope. The rows still declare the
 * wire name, default and read so the read projection and reset ledger derive;
 * the store keeps its hand-written write lines.
 *
 * **Search:** no row carries a [PreferenceSearchSpec] — the reader knobs live
 * on the in-reader sheets and the book-player feature, not in the settings
 * search ([searchEntries] is derived and empty), so the domain contributes no
 * rows to the settings-catalog diff ratchet.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [ReaderSlice] property whose default reads the row, one `read()`
 * row, one setter (and its clearAll participation is automatic) — plus one
 * write-through test line; the derivation covers key identity, encodings,
 * reset lists and slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object ReaderPreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [ReaderSlice] property order. A row is constructed exactly once, here;
    // [all], the reset derivation and [searchEntries] all derive from the
    // groups.
    // ------------------------------------------------------------------

    /** The per-book page-flow maps (direction + paged layout), keyed by item id. */
    private val perBookFlow: List<PreferenceSpec<*>> = listOf(
        /*
         * Reads [ReaderSlice.readingDirections] (itemId → direction). The
         * absence of a book's entry IS the LTR default (identity-by-absence —
         * the reader store's defaulting verb owns the `?: LTR`), so the row
         * default is the empty map.
         */
        PreferenceSpec.derived(
            keyName = "reader_reading_directions",
            default = emptyMap<String, ReadingDirection>(),
            resetCategory = null,
            read = { _, raw -> decodeDirections(raw) },
        ),
        PreferenceSpec.derived(
            keyName = "reader_reading_layouts",
            default = emptyMap<String, ReadingLayout>(),
            resetCategory = null,
            read = { _, raw -> decodeLayouts(raw) },
        ),
    )

    /** Global reflowable appearance (theme, font, spacing, paragraph flow). */
    private val globalAppearance: List<PreferenceSpec<*>> = listOf(
        // Enum-by-name string slot; a corrupt stored name degrades to DARK.
        PreferenceSpec.enumRow(
            keyName = "reader_theme",
            default = ReaderTheme.DARK,
            resetCategory = null,
        ),
        /*
         * The body font size in px; the read clamps into 12..32 in the store's
         * read() (a hand-edited / pre-clamp stored value degrades into the
         * band independently of the setter, which clamps its write).
         */
        PreferenceSpec.plainInt(
            keyName = "reader_font_size_px",
            default = ReaderStore.DEFAULT_FONT_SIZE_PX,
            resetCategory = null,
            min = ReaderStore.MIN_FONT_SIZE_PX,
            max = ReaderStore.MAX_FONT_SIZE_PX,
        ),
        // Enum-by-name string slot; a corrupt stored name degrades to SYSTEM.
        PreferenceSpec.enumRow(
            keyName = "reader_font_family",
            default = ReaderFontFamily.SYSTEM,
            resetCategory = null,
        ),
        PreferenceSpec.plainInt(
            keyName = "reader_line_height_pct",
            default = ReaderStore.DEFAULT_LINE_HEIGHT_PCT,
            resetCategory = null,
            min = ReaderStore.MIN_LINE_HEIGHT_PCT,
            max = ReaderStore.MAX_LINE_HEIGHT_PCT,
        ),
        PreferenceSpec.plainInt(
            keyName = "reader_margin_pct",
            default = ReaderStore.DEFAULT_MARGIN_PCT,
            resetCategory = null,
            min = ReaderStore.MIN_MARGIN_PCT,
            max = ReaderStore.MAX_MARGIN_PCT,
        ),
        // Plain declarations: the pre-spec hand read was the plain typed read
        // (prefs[key] ?: default), so the rows keep the generic readStored —
        // the setters stay the store's hand lines (no derived write).
        PreferenceSpec.boolean(
            keyName = "reader_justify",
            default = false,
            resetCategory = null,
        ),
        PreferenceSpec.boolean(
            keyName = "reader_scroll_mode",
            default = false,
            resetCategory = null,
        ),
    )

    /** Display behavior while reading. */
    private val displayBehavior: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "reader_brightness_pct",
            default = ReaderStore.DEFAULT_BRIGHTNESS_PCT,
            resetCategory = null,
            min = ReaderStore.MIN_BRIGHTNESS_PCT,
            max = ReaderStore.MAX_BRIGHTNESS_PCT,
        ),
        PreferenceSpec.boolean(
            keyName = "reader_volume_key_paging",
            default = false,
            resetCategory = null,
        ),
        PreferenceSpec.boolean(
            keyName = "reader_animated_page_turns",
            default = true,
            resetCategory = null,
        ),
        // Chapter tick rail; off by default (opt-in via the reader controls).
        PreferenceSpec.boolean(
            keyName = "reader_toc_rail",
            default = false,
            resetCategory = null,
        ),
    )

    /** Reading / speech pacing. */
    private val pacing: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "reader_speech_rate",
            default = ReaderStore.DEFAULT_SPEECH_RATE,
            resetCategory = null,
            min = ReaderStore.MIN_SPEECH_RATE,
            max = ReaderStore.MAX_SPEECH_RATE,
        ),
        PreferenceSpec.plainInt(
            keyName = "reader_speech_pitch",
            default = ReaderStore.DEFAULT_SPEECH_PITCH,
            resetCategory = null,
            min = ReaderStore.MIN_SPEECH_PITCH,
            max = ReaderStore.MAX_SPEECH_PITCH,
        ),
        PreferenceSpec.plainInt(
            keyName = "reader_reading_speed_wpm",
            default = ReaderStore.DEFAULT_READING_SPEED_WPM,
            resetCategory = null,
            min = ReaderStore.MIN_READING_SPEED_WPM,
            max = ReaderStore.MAX_READING_SPEED_WPM,
        ),
        PreferenceSpec.plainInt(
            keyName = "reader_auto_scroll_speed_px",
            default = ReaderStore.DEFAULT_AUTO_SCROLL_SPEED_PX_PER_SEC,
            resetCategory = null,
            min = ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC,
            max = ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC,
        ),
    )

    /**
     * The per-book appearance overrides + the exact-resume CFI map — runtime
     * state with the documented write opt-out (see the object KDoc).
     */
    private val perBookState: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "reader_per_book_appearance",
            default = emptyMap<String, PerBookAppearance>(),
            resetCategory = null,
            read = { _, raw -> decodePerBookAppearance(raw) },
        ),
        PreferenceSpec.derived(
            keyName = "reader_last_cfis",
            default = emptyMap<String, String>(),
            resetCategory = null,
            read = { _, raw -> decodeLastCfis(raw) },
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [ReaderSlice] property order. Nothing re-lists the rows: a row
     * is registered exactly once, in its group, and appears here (and in the
     * reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        perBookFlow +
            globalAppearance +
            displayBehavior +
            pacing +
            perBookState

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — which is none of them:
     * every reader row carries no category (the reading experience plus
     * per-book runtime state sits outside the facade's category-reset
     * machinery), so this returns an empty list for every category. The
     * store's own `clearAll` derives from [all] instead.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a search entry — none do. The reader knobs are adjusted in the
     * reader's own sheets (the book-player feature), not through settings
     * search, so the list stays empty by construction; a future searchable
     * row needs only its [PreferenceSearchSpec] on the row.
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

    val READING_DIRECTIONS: PreferenceSpec<Map<String, ReadingDirection>> get() = row("reader_reading_directions")

    val READER_READING_LAYOUTS: PreferenceSpec<Map<String, ReadingLayout>> get() = row("reader_reading_layouts")

    val READER_THEME: PreferenceSpec<ReaderTheme> get() = row("reader_theme")

    val READER_FONT_SIZE_PX: PreferenceSpec<Int> get() = row("reader_font_size_px")

    val READER_FONT_FAMILY: PreferenceSpec<ReaderFontFamily> get() = row("reader_font_family")

    val READER_LINE_HEIGHT_PCT: PreferenceSpec<Int> get() = row("reader_line_height_pct")

    val READER_MARGIN_PCT: PreferenceSpec<Int> get() = row("reader_margin_pct")

    val READER_JUSTIFY: PreferenceSpec<Boolean> get() = row("reader_justify")

    val READER_SCROLL_MODE: PreferenceSpec<Boolean> get() = row("reader_scroll_mode")

    val READER_BRIGHTNESS_PCT: PreferenceSpec<Int> get() = row("reader_brightness_pct")

    val READER_VOLUME_KEY_PAGING: PreferenceSpec<Boolean> get() = row("reader_volume_key_paging")

    val READER_ANIMATED_PAGE_TURNS: PreferenceSpec<Boolean> get() = row("reader_animated_page_turns")

    val READER_TOC_RAIL: PreferenceSpec<Boolean> get() = row("reader_toc_rail")

    val READER_SPEECH_RATE: PreferenceSpec<Int> get() = row("reader_speech_rate")

    val READER_SPEECH_PITCH: PreferenceSpec<Int> get() = row("reader_speech_pitch")

    val READER_READING_SPEED_WPM: PreferenceSpec<Int> get() = row("reader_reading_speed_wpm")

    val READER_AUTO_SCROLL_SPEED_PX: PreferenceSpec<Int> get() = row("reader_auto_scroll_speed_px")

    val READER_PER_BOOK_APPEARANCE: PreferenceSpec<Map<String, PerBookAppearance>> get() =
        row("reader_per_book_appearance")

    val READER_LAST_CFIS: PreferenceSpec<Map<String, String>> get() = row("reader_last_cfis")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [ReaderStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "ReaderPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
