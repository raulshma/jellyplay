package com.raulshma.jellyplay.core.datastore.reader

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Page-flow direction for a book: left-to-right (Western books, comics) or
 * right-to-left (manga). Lives in the datastore module (not core/model) — it is
 * a pure reader-preference concern with no server-side counterpart, so keeping
 * it here avoids coupling the shared model surface to the reader.
 */
@Serializable
enum class ReadingDirection {
    LTR,
    RTL,
}

/**
 * Paged-book layout: one page per screen turn, or a two-page spread (manga /
 * double-page mode). Lives beside [ReadingDirection] for the same reason —
 * a pure reader preference with no server-side counterpart.
 */
@Serializable
enum class ReadingLayout {
    SINGLE,
    DOUBLE,
}

/**
 * Global reflowable-book appearance theme. Lives in the datastore module (not
 * core/model) for the same reason as [ReadingDirection]: a pure reader
 * preference with no server-side counterpart.
 */
@Serializable
enum class ReaderTheme {
    DARK,
    SEPIA,
    LIGHT,
}

/**
 * Reflowable body font family. Lives in the datastore module for the same
 * reason as [ReadingDirection]: a pure reader preference with no server-side
 * counterpart. SYSTEM defers to the platform default.
 */
@Serializable
enum class ReaderFontFamily {
    SYSTEM,
    SERIF,
    SANS,
    MONO,
}

/**
 * Per-book appearance override: an unset axis (null) inherits the global
 * [ReaderTheme] / reader font size. Carried inside the
 * `reader_per_book_appearance` JSON map, so it must stay [Serializable]
 * with defaults (an older blob decoding against a newer field set is the
 * forward-compat contract of the shared lenient codec).
 */
@Serializable
data class PerBookAppearance(
    val theme: ReaderTheme? = null,
    val fontSizePx: Int? = null,
)

/**
 * Reader preference domain (book reading): the per-book page-flow direction
 * map (itemId → [ReadingDirection]), per-book appearance overrides
 * (itemId → [PerBookAppearance]), the per-book exact-resume CFI map
 * (itemId → last `epubcfi(...)`, preferred over the server percent on the
 * same install), plus
 * the global reflowable appearance ([ReaderTheme], font family/size, line
 * height, margins, justify, scroll), display behavior (brightness,
 * volume-key paging, animated page turns) and reading/speech pacing
 * (speech rate + pitch percents, words-per-minute estimate, auto-scroll
 * speed). Defaults to
 * [ReadingDirection.LTR] for any book without an explicit choice; RTL is
 * the manga reader's per-title override.
 *
 * Each map is stored as one JSON-encoded string under a single key rather
 * than per-item preference keys: the reset-key list stays static (per-item
 * keys could not be enumerated for factory reset), and a write is one atomic
 * edit. Map size is bounded by the user's downloaded-library count, so the
 * blobs stay small.
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [ReaderPreferenceSpecs] (wire name, default, no-reset-category
 * participation, read/write encoding) and the machinery below is derived from
 * those rows — each [Keys] member rebuilds its row's typed key from the row's
 * wire name, each [read] projection row delegates to its row encoding, the
 * enum/int setters delegate to the rows' derived writes, and [resetKeys]
 * derives from the rows (the reader domain sits in no
 * `PreferenceResetCategory`, so its full-store reset list is simply every
 * declared row). The map decodes live in the rows now, beside the
 * declarations.
 *
 * **Hand-written residuals (the documented opt-outs):**
 *  - the five boolean rows keep the plain typed read their keys always had
 *    (they were born outside `PreferenceCodec`'s legacy migration lists, so
 *    their declarations carry no derived encoding) and their setters keep the
 *    hand write lines;
 *  - the eight int rows' read-time clamp stays in [read] (`plainInt` has no
 *    read transform — the decode defends out-of-band values independently of
 *    the setters, exactly as the pre-spec `decodeClampedInt` did), beside the
 *    setter clamps;
 *  - the four per-book map rows' writes are the decode→edit→encode
 *    transactions below ([EncodedMapPreference]) — a read-modify-write
 *    preserving the other books' entries in one atomic edit is not a
 *    single-slot row encoding.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; follows the
 * per-domain store shape (private [Keys], derived slice StateFlow, edit-based
 * setters, reset-key list).
 */
class ReaderStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The four JSON-carrier map preferences (directions / layouts /
     * per-book appearance / last CFIs), each declared once as a
     * [EncodedMapPreference] — the decode→edit→encode transaction shape
     * lives in that class, not pasted per setter.
     */
    private val directionsPref = EncodedMapPreference(
        dataStore = dataStore,
        key = Keys.READING_DIRECTIONS,
        decode = ::decodeDirections,
        encode = { PreferenceCodec.json.encodeToString(EncodedDirections.serializer(), EncodedDirections(it)) },
    )
    private val layoutsPref = EncodedMapPreference(
        dataStore = dataStore,
        key = Keys.READER_READING_LAYOUTS,
        decode = ::decodeLayouts,
        encode = { PreferenceCodec.json.encodeToString(EncodedLayouts.serializer(), EncodedLayouts(it)) },
    )
    private val perBookAppearancePref = EncodedMapPreference(
        dataStore = dataStore,
        key = Keys.READER_PER_BOOK_APPEARANCE,
        decode = ::decodePerBookAppearance,
        encode = { PreferenceCodec.json.encodeToString(EncodedPerBookAppearance.serializer(), EncodedPerBookAppearance(it)) },
    )
    private val lastCfisPref = EncodedMapPreference(
        dataStore = dataStore,
        key = Keys.READER_LAST_CFIS,
        decode = ::decodeLastCfis,
        encode = { PreferenceCodec.json.encodeToString(EncodedLastCfis.serializer(), EncodedLastCfis(it)) },
    )

    /**
     * The store's DataStore keys, each derived from its
     * [ReaderPreferenceSpecs] row — the member rebuilds the row's typed key
     * from the row's single-declared wire name (`Preferences.Key` equality is
     * name-based, so these interoperate with any hand-built key of the same
     * name). Kept as a plain object rather than folded into the rows because
     * it is the reflection anchor for [ReaderSpecDerivationTest] (this store's
     * Keys are deliberately outside the facade's reset-coverage guard — see
     * [resetKeys]) and the key-identity reference for the hand-written map
     * verbs and boolean setters below.
     */
    internal object Keys {
        val READING_DIRECTIONS = stringPreferencesKey(ReaderPreferenceSpecs.READING_DIRECTIONS.keyName)
        val READER_READING_LAYOUTS = stringPreferencesKey(ReaderPreferenceSpecs.READER_READING_LAYOUTS.keyName)
        val READER_THEME = stringPreferencesKey(ReaderPreferenceSpecs.READER_THEME.keyName)
        val READER_FONT_SIZE_PX = intPreferencesKey(ReaderPreferenceSpecs.READER_FONT_SIZE_PX.keyName)
        val READER_FONT_FAMILY = stringPreferencesKey(ReaderPreferenceSpecs.READER_FONT_FAMILY.keyName)
        val READER_LINE_HEIGHT_PCT = intPreferencesKey(ReaderPreferenceSpecs.READER_LINE_HEIGHT_PCT.keyName)
        val READER_MARGIN_PCT = intPreferencesKey(ReaderPreferenceSpecs.READER_MARGIN_PCT.keyName)
        val READER_JUSTIFY = booleanPreferencesKey(ReaderPreferenceSpecs.READER_JUSTIFY.keyName)
        val READER_SCROLL_MODE = booleanPreferencesKey(ReaderPreferenceSpecs.READER_SCROLL_MODE.keyName)
        val READER_BRIGHTNESS_PCT = intPreferencesKey(ReaderPreferenceSpecs.READER_BRIGHTNESS_PCT.keyName)
        val READER_VOLUME_KEY_PAGING = booleanPreferencesKey(ReaderPreferenceSpecs.READER_VOLUME_KEY_PAGING.keyName)
        val READER_ANIMATED_PAGE_TURNS = booleanPreferencesKey(ReaderPreferenceSpecs.READER_ANIMATED_PAGE_TURNS.keyName)
        val READER_TOC_RAIL = booleanPreferencesKey(ReaderPreferenceSpecs.READER_TOC_RAIL.keyName)
        val READER_SPEECH_RATE = intPreferencesKey(ReaderPreferenceSpecs.READER_SPEECH_RATE.keyName)
        val READER_SPEECH_PITCH = intPreferencesKey(ReaderPreferenceSpecs.READER_SPEECH_PITCH.keyName)
        val READER_READING_SPEED_WPM = intPreferencesKey(ReaderPreferenceSpecs.READER_READING_SPEED_WPM.keyName)
        val READER_AUTO_SCROLL_SPEED_PX = intPreferencesKey(ReaderPreferenceSpecs.READER_AUTO_SCROLL_SPEED_PX.keyName)
        val READER_PER_BOOK_APPEARANCE = stringPreferencesKey(ReaderPreferenceSpecs.READER_PER_BOOK_APPEARANCE.keyName)
        val READER_LAST_CFIS = stringPreferencesKey(ReaderPreferenceSpecs.READER_LAST_CFIS.keyName)
    }

    companion object {
        const val DEFAULT_FONT_SIZE_PX = 17
        const val MIN_FONT_SIZE_PX = 12
        const val MAX_FONT_SIZE_PX = 32
        const val DEFAULT_LINE_HEIGHT_PCT = 160
        const val MIN_LINE_HEIGHT_PCT = 100
        const val MAX_LINE_HEIGHT_PCT = 200
        const val DEFAULT_MARGIN_PCT = 8
        const val MIN_MARGIN_PCT = 0
        const val MAX_MARGIN_PCT = 100
        const val DEFAULT_BRIGHTNESS_PCT = 100
        const val MIN_BRIGHTNESS_PCT = 0
        const val MAX_BRIGHTNESS_PCT = 100
        const val DEFAULT_SPEECH_RATE = 100
        const val MIN_SPEECH_RATE = 50
        const val MAX_SPEECH_RATE = 200
        const val DEFAULT_SPEECH_PITCH = 100
        const val MIN_SPEECH_PITCH = 50
        const val MAX_SPEECH_PITCH = 200
        const val DEFAULT_READING_SPEED_WPM = 238
        const val MIN_READING_SPEED_WPM = 100
        const val MAX_READING_SPEED_WPM = 1000
        const val DEFAULT_AUTO_SCROLL_SPEED_PX_PER_SEC = 40
        const val MIN_AUTO_SCROLL_SPEED_PX_PER_SEC = 20
        const val MAX_AUTO_SCROLL_SPEED_PX_PER_SEC = 120
    }

    /**
     * The reader preference slice, derived directly from the raw DataStore so
     * a write to an unrelated preference does not re-derive these fields.
     */
    val reader: StateFlow<ReaderSlice> =
        dataStore.sliceStateFlow(scope, seed = ReaderSlice(), read = ::read)

    /**
     * Pure read of the reader fields from a raw [Preferences] snapshot, each
     * field delegated to its [ReaderPreferenceSpecs] row encoding — the eight
     * int rows post-clamped into their bands here (the rows declare no read
     * transform; the decode defends out-of-band values independently of the
     * setters). Exposed so the preference facade can fold this into its whole
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): ReaderSlice = ReaderSlice(
        readingDirections = ReaderPreferenceSpecs.READING_DIRECTIONS.readFrom(prefs),
        readingLayouts = ReaderPreferenceSpecs.READER_READING_LAYOUTS.readFrom(prefs),
        readerTheme = ReaderPreferenceSpecs.READER_THEME.readFrom(prefs),
        readerFontSizePx = ReaderPreferenceSpecs.READER_FONT_SIZE_PX.readFrom(prefs)
            .coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX),
        fontFamily = ReaderPreferenceSpecs.READER_FONT_FAMILY.readFrom(prefs),
        lineHeightPct = ReaderPreferenceSpecs.READER_LINE_HEIGHT_PCT.readFrom(prefs)
            .coerceIn(MIN_LINE_HEIGHT_PCT, MAX_LINE_HEIGHT_PCT),
        marginPct = ReaderPreferenceSpecs.READER_MARGIN_PCT.readFrom(prefs)
            .coerceIn(MIN_MARGIN_PCT, MAX_MARGIN_PCT),
        justify = ReaderPreferenceSpecs.READER_JUSTIFY.readStored(prefs),
        scrollMode = ReaderPreferenceSpecs.READER_SCROLL_MODE.readStored(prefs),
        brightnessPct = ReaderPreferenceSpecs.READER_BRIGHTNESS_PCT.readFrom(prefs)
            .coerceIn(MIN_BRIGHTNESS_PCT, MAX_BRIGHTNESS_PCT),
        volumeKeyPaging = ReaderPreferenceSpecs.READER_VOLUME_KEY_PAGING.readStored(prefs),
        animatedPageTurns = ReaderPreferenceSpecs.READER_ANIMATED_PAGE_TURNS.readStored(prefs),
        tocRailVisible = ReaderPreferenceSpecs.READER_TOC_RAIL.readStored(prefs),
        speechRate = ReaderPreferenceSpecs.READER_SPEECH_RATE.readFrom(prefs)
            .coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
        speechPitch = ReaderPreferenceSpecs.READER_SPEECH_PITCH.readFrom(prefs)
            .coerceIn(MIN_SPEECH_PITCH, MAX_SPEECH_PITCH),
        readingSpeedWpm = ReaderPreferenceSpecs.READER_READING_SPEED_WPM.readFrom(prefs)
            .coerceIn(MIN_READING_SPEED_WPM, MAX_READING_SPEED_WPM),
        autoScrollSpeedPxPerSec = ReaderPreferenceSpecs.READER_AUTO_SCROLL_SPEED_PX.readFrom(prefs)
            .coerceIn(MIN_AUTO_SCROLL_SPEED_PX_PER_SEC, MAX_AUTO_SCROLL_SPEED_PX_PER_SEC),
        perBookAppearance = ReaderPreferenceSpecs.READER_PER_BOOK_APPEARANCE.readFrom(prefs),
        lastCfis = ReaderPreferenceSpecs.READER_LAST_CFIS.readFrom(prefs),
    )

    /**
     * The page-flow direction for [itemId], defaulting to [ReadingDirection.LTR]
     * when the book has no stored override.
     */
    fun readingDirection(itemId: String): ReadingDirection =
        reader.value.readingDirections[itemId] ?: ReadingDirection.LTR

    suspend fun setReadingDirection(itemId: String, direction: ReadingDirection) {
        directionsPref.edit { it + (itemId to direction) }
    }

    /** The paged layout for [itemId], defaulting to [ReadingLayout.SINGLE]. */
    fun readingLayout(itemId: String): ReadingLayout =
        reader.value.readingLayouts[itemId] ?: ReadingLayout.SINGLE

    suspend fun setReadingLayout(itemId: String, layout: ReadingLayout) {
        layoutsPref.edit { it + (itemId to layout) }
    }

    suspend fun setReaderTheme(theme: ReaderTheme) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_THEME.writeTo(prefs, theme)
        }
    }

    /** Stored clamped into [MIN_FONT_SIZE_PX]..[MAX_FONT_SIZE_PX]. */
    suspend fun setReaderFontSizePx(px: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_FONT_SIZE_PX.writeTo(
                prefs,
                px.coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX),
            )
        }
    }

    suspend fun setFontFamily(fontFamily: ReaderFontFamily) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_FONT_FAMILY.writeTo(prefs, fontFamily)
        }
    }

    /** Stored clamped into [MIN_LINE_HEIGHT_PCT]..[MAX_LINE_HEIGHT_PCT] (percent of font size). */
    suspend fun setLineHeightPct(pct: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_LINE_HEIGHT_PCT.writeTo(
                prefs,
                pct.coerceIn(MIN_LINE_HEIGHT_PCT, MAX_LINE_HEIGHT_PCT),
            )
        }
    }

    /** Stored clamped into [MIN_MARGIN_PCT]..[MAX_MARGIN_PCT] (percent of viewport width). */
    suspend fun setMarginPct(pct: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_MARGIN_PCT.writeTo(
                prefs,
                pct.coerceIn(MIN_MARGIN_PCT, MAX_MARGIN_PCT),
            )
        }
    }

    // The five boolean rows are plain declarations (no derived write — their
    // keys were born outside PreferenceCodec's migration lists), so their
    // setters keep the hand write lines.

    suspend fun setJustify(justify: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_JUSTIFY] = justify
        }
    }

    suspend fun setScrollMode(scrollMode: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_SCROLL_MODE] = scrollMode
        }
    }

    /** Stored clamped into [MIN_BRIGHTNESS_PCT]..[MAX_BRIGHTNESS_PCT] (100 = system brightness). */
    suspend fun setBrightnessPct(pct: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_BRIGHTNESS_PCT.writeTo(
                prefs,
                pct.coerceIn(MIN_BRIGHTNESS_PCT, MAX_BRIGHTNESS_PCT),
            )
        }
    }

    suspend fun setVolumeKeyPaging(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_VOLUME_KEY_PAGING] = enabled
        }
    }

    suspend fun setAnimatedPageTurns(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_ANIMATED_PAGE_TURNS] = enabled
        }
    }

    /** Show the chapter tick rail while reading (off by default — opt-in via the reader controls). */
    suspend fun setTocRailVisible(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_TOC_RAIL] = enabled
        }
    }

    /** Stored clamped into [MIN_SPEECH_RATE]..[MAX_SPEECH_RATE] (percent of normal rate). */
    suspend fun setSpeechRate(pct: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_SPEECH_RATE.writeTo(
                prefs,
                pct.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
            )
        }
    }

    /** Stored clamped into [MIN_SPEECH_PITCH]..[MAX_SPEECH_PITCH] (percent of normal pitch). */
    suspend fun setSpeechPitch(pct: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_SPEECH_PITCH.writeTo(
                prefs,
                pct.coerceIn(MIN_SPEECH_PITCH, MAX_SPEECH_PITCH),
            )
        }
    }

    /** Stored clamped into [MIN_READING_SPEED_WPM]..[MAX_READING_SPEED_WPM]. */
    suspend fun setReadingSpeedWpm(wpm: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_READING_SPEED_WPM.writeTo(
                prefs,
                wpm.coerceIn(MIN_READING_SPEED_WPM, MAX_READING_SPEED_WPM),
            )
        }
    }

    /** Auto-scroll speed for scrolled-flow EPUBs; clamped into its 20..120 px/s band. */
    suspend fun setAutoScrollSpeedPxPerSec(pxPerSec: Int) {
        dataStore.edit { prefs ->
            ReaderPreferenceSpecs.READER_AUTO_SCROLL_SPEED_PX.writeTo(
                prefs,
                pxPerSec.coerceIn(MIN_AUTO_SCROLL_SPEED_PX_PER_SEC, MAX_AUTO_SCROLL_SPEED_PX_PER_SEC),
            )
        }
    }

    /**
     * The appearance override for [itemId], or null when the book inherits
     * the global theme/font size.
     */
    fun perBookAppearance(itemId: String): PerBookAppearance? =
        reader.value.perBookAppearance[itemId]

    /**
     * Sets (or, with a null [appearance], clears) the per-book override for
     * [itemId]; other books' entries are untouched. A [PerBookAppearance.fontSizePx]
     * is clamped into the global font band like the global setter.
     */
    suspend fun setPerBookAppearance(itemId: String, appearance: PerBookAppearance?) {
        perBookAppearancePref.edit { current ->
            if (appearance == null) {
                current - itemId
            } else {
                current + (itemId to appearance.copy(
                    fontSizePx = appearance.fontSizePx?.coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX),
                ))
            }
        }
    }

    /** The exact-resume CFI last recorded for [itemId], or null when reopening at the server percent. */
    fun lastCfi(itemId: String): String? =
        reader.value.lastCfis[itemId]

    /**
     * Records the book's exact resume CFI: local exact resume is preferred
     * over the server percent on the same install, while the server keeps
     * receiving the ticks protocol.
     */
    suspend fun setLastCfi(itemId: String, cfi: String) {
        lastCfisPref.edit { it + (itemId to cfi) }
    }

    /** Drops [itemId]'s resume CFI (book removed / marks cleared); other books are untouched. */
    suspend fun clearLastCfi(itemId: String) {
        lastCfisPref.edit { it - itemId }
    }

    /**
     * Clears every reader preference owned by this store by removing exactly
     * the [resetKeys] list — the store's single declaration of the keys it
     * owns, so a future key cannot be added to one path and forgotten on the
     * other. Reader keys share the `"user_prefs"` DataStore with the facade
     * domain, so they are also wiped by a full settings reset
     * (`UserPreferencesStore.clearAllPreferencesOnly`, which clears the whole
     * DataStore); the reader domain sits in no `PreferenceResetCategory`, so
     * no per-category reset touches it. This is the programmatic single-store
     * form.
     */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            resetKeys.forEach { key -> prefs.remove(key) }
        }
    }

    /**
     * Keys owned by this store, removed by [clearAll]. Derived as every
     * [ReaderPreferenceSpecs] row's typed key — the reader domain sits outside
     * the facade's category-reset machinery (every row declares no
     * `PreferenceResetCategory`, so [ReaderPreferenceSpecs.resetKeysFor] is
     * empty for every category and the facade's `resetCategoryKeys` never
     * calls into this store), which makes the full-row list exactly the keys
     * this store wipes; deriving it from the rows (instead of a parallel hand
     * list) keeps a future row from being added to one path and forgotten on
     * the other. The reset-coverage guard — which reflectively enumerates the
     * facade-owned store `Keys` objects — deliberately does not include
     * `ReaderStore.Keys`; this list is asserted directly by `ReaderStoreTest`
     * and by `ReaderSpecDerivationTest` instead of by the guard.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        ReaderPreferenceSpecs.all.map { it.typedKey() }
}

/**
 * The reader preference slice. Every default IS its [ReaderPreferenceSpecs]
 * row's declared default — read off the row via a plain accessor, so a
 * preference's default is stated exactly once, in its spec, and cannot drift
 * between the spec (what an absent key reads) and the slice (what a fresh
 * in-memory instance serves). The spec-default equality is pinned
 * field-for-field by `ReaderSpecDerivationTest`.
 */
@Immutable
@Serializable
data class ReaderSlice(
    val readingDirections: Map<String, ReadingDirection> = ReaderPreferenceSpecs.READING_DIRECTIONS.default,
    val readingLayouts: Map<String, ReadingLayout> = ReaderPreferenceSpecs.READER_READING_LAYOUTS.default,
    val readerTheme: ReaderTheme = ReaderPreferenceSpecs.READER_THEME.default,
    val readerFontSizePx: Int = ReaderPreferenceSpecs.READER_FONT_SIZE_PX.default,
    val fontFamily: ReaderFontFamily = ReaderPreferenceSpecs.READER_FONT_FAMILY.default,
    val lineHeightPct: Int = ReaderPreferenceSpecs.READER_LINE_HEIGHT_PCT.default,
    val marginPct: Int = ReaderPreferenceSpecs.READER_MARGIN_PCT.default,
    val justify: Boolean = ReaderPreferenceSpecs.READER_JUSTIFY.default,
    val scrollMode: Boolean = ReaderPreferenceSpecs.READER_SCROLL_MODE.default,
    val brightnessPct: Int = ReaderPreferenceSpecs.READER_BRIGHTNESS_PCT.default,
    val volumeKeyPaging: Boolean = ReaderPreferenceSpecs.READER_VOLUME_KEY_PAGING.default,
    val animatedPageTurns: Boolean = ReaderPreferenceSpecs.READER_ANIMATED_PAGE_TURNS.default,
    val tocRailVisible: Boolean = ReaderPreferenceSpecs.READER_TOC_RAIL.default,
    val speechRate: Int = ReaderPreferenceSpecs.READER_SPEECH_RATE.default,
    val speechPitch: Int = ReaderPreferenceSpecs.READER_SPEECH_PITCH.default,
    val readingSpeedWpm: Int = ReaderPreferenceSpecs.READER_READING_SPEED_WPM.default,
    val autoScrollSpeedPxPerSec: Int = ReaderPreferenceSpecs.READER_AUTO_SCROLL_SPEED_PX.default,
    val perBookAppearance: Map<String, PerBookAppearance> = ReaderPreferenceSpecs.READER_PER_BOOK_APPEARANCE.default,
    val lastCfis: Map<String, String> = ReaderPreferenceSpecs.READER_LAST_CFIS.default,
)

/**
 * One JSON-encoded map preference under a single [key]: the decode → edit →
 * encode transaction every reader map shares, written once. The per-map
 * [Encoded*][EncodedDirections] carrier classes stay separate so the stored
 * JSON field names (`directions`, `layouts`, …) never change. The carriers
 * and the shared decode live beside the rows in [ReaderPreferenceSpecs.kt]
 * (the row reads and these verbs share the one decoder); this class owns the
 * transaction shape — the documented write opt-out of the four per-book map
 * rows.
 */
private class EncodedMapPreference<T>(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String>,
    private val decode: (String?) -> Map<String, T>,
    private val encode: (Map<String, T>) -> String,
) {
    suspend fun edit(edit: (Map<String, T>) -> Map<String, T>) {
        dataStore.edit { prefs ->
            prefs[key] = encode(edit(decode(prefs[key])))
        }
    }
}
