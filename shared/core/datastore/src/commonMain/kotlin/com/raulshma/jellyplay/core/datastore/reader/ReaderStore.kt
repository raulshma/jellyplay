package com.raulshma.jellyplay.core.datastore.reader

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * same install — docs/adr/0003-local-first-reader-marks.md point 4), plus
 * the global reflowable appearance ([ReaderTheme], font family/size, line
 * height, margins, justify, scroll), display behavior (brightness,
 * volume-key paging, animated page turns) and reading/speech pacing
 * (speech rate + pitch percents, words-per-minute estimate). Defaults to
 * [ReadingDirection.LTR] for any book without an explicit choice; RTL is
 * the manga reader's per-title override.
 *
 * Each map is stored as one JSON-encoded string under a single key rather
 * than per-item preference keys: the reset-key list stays static (per-item
 * keys could not be enumerated for factory reset), and a write is one atomic
 * edit. Map size is bounded by the user's downloaded-library count, so the
 * blobs stay small.
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

    internal object Keys {
        val READING_DIRECTIONS = stringPreferencesKey("reader_reading_directions")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT_SIZE_PX = intPreferencesKey("reader_font_size_px")
        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val READER_LINE_HEIGHT_PCT = intPreferencesKey("reader_line_height_pct")
        val READER_MARGIN_PCT = intPreferencesKey("reader_margin_pct")
        val READER_JUSTIFY = booleanPreferencesKey("reader_justify")
        val READER_SCROLL_MODE = booleanPreferencesKey("reader_scroll_mode")
        val READER_BRIGHTNESS_PCT = intPreferencesKey("reader_brightness_pct")
        val READER_VOLUME_KEY_PAGING = booleanPreferencesKey("reader_volume_key_paging")
        val READER_ANIMATED_PAGE_TURNS = booleanPreferencesKey("reader_animated_page_turns")
        val READER_SPEECH_RATE = intPreferencesKey("reader_speech_rate")
        val READER_SPEECH_PITCH = intPreferencesKey("reader_speech_pitch")
        val READER_READING_SPEED_WPM = intPreferencesKey("reader_reading_speed_wpm")
        val READER_PER_BOOK_APPEARANCE = stringPreferencesKey("reader_per_book_appearance")
        val READER_LAST_CFIS = stringPreferencesKey("reader_last_cfis")
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
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    /**
     * The reader preference slice, derived directly from the raw DataStore so
     * a write to an unrelated preference does not re-derive these fields.
     */
    val reader: StateFlow<ReaderSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ReaderSlice())

    /**
     * Pure read of the reader fields from a raw [Preferences] snapshot.
     * Exposed so the preference facade can fold this into its whole
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): ReaderSlice = ReaderSlice(
        readingDirections = decodeDirections(prefs[Keys.READING_DIRECTIONS]),
        readerTheme = decodeTheme(prefs[Keys.READER_THEME]),
        readerFontSizePx = decodeFontSize(prefs[Keys.READER_FONT_SIZE_PX]),
        fontFamily = decodeFontFamily(prefs[Keys.READER_FONT_FAMILY]),
        lineHeightPct = decodeClampedInt(prefs[Keys.READER_LINE_HEIGHT_PCT], MIN_LINE_HEIGHT_PCT, MAX_LINE_HEIGHT_PCT, DEFAULT_LINE_HEIGHT_PCT),
        marginPct = decodeClampedInt(prefs[Keys.READER_MARGIN_PCT], MIN_MARGIN_PCT, MAX_MARGIN_PCT, DEFAULT_MARGIN_PCT),
        justify = prefs[Keys.READER_JUSTIFY] ?: false,
        scrollMode = prefs[Keys.READER_SCROLL_MODE] ?: false,
        brightnessPct = decodeClampedInt(prefs[Keys.READER_BRIGHTNESS_PCT], MIN_BRIGHTNESS_PCT, MAX_BRIGHTNESS_PCT, DEFAULT_BRIGHTNESS_PCT),
        volumeKeyPaging = prefs[Keys.READER_VOLUME_KEY_PAGING] ?: false,
        animatedPageTurns = prefs[Keys.READER_ANIMATED_PAGE_TURNS] ?: true,
        speechRate = decodeClampedInt(prefs[Keys.READER_SPEECH_RATE], MIN_SPEECH_RATE, MAX_SPEECH_RATE, DEFAULT_SPEECH_RATE),
        speechPitch = decodeClampedInt(prefs[Keys.READER_SPEECH_PITCH], MIN_SPEECH_PITCH, MAX_SPEECH_PITCH, DEFAULT_SPEECH_PITCH),
        readingSpeedWpm = decodeClampedInt(prefs[Keys.READER_READING_SPEED_WPM], MIN_READING_SPEED_WPM, MAX_READING_SPEED_WPM, DEFAULT_READING_SPEED_WPM),
        perBookAppearance = decodePerBookAppearance(prefs[Keys.READER_PER_BOOK_APPEARANCE]),
        lastCfis = decodeLastCfis(prefs[Keys.READER_LAST_CFIS]),
    )

    /**
     * The page-flow direction for [itemId], defaulting to [ReadingDirection.LTR]
     * when the book has no stored override.
     */
    fun readingDirection(itemId: String): ReadingDirection =
        reader.value.readingDirections[itemId] ?: ReadingDirection.LTR

    suspend fun setReadingDirection(itemId: String, direction: ReadingDirection) {
        dataStore.edit { prefs ->
            val updated = decodeDirections(prefs[Keys.READING_DIRECTIONS]) + (itemId to direction)
            prefs[Keys.READING_DIRECTIONS] = PreferenceCodec.json.encodeToString(
                EncodedDirections.serializer(),
                EncodedDirections(updated),
            )
        }
    }

    suspend fun setReaderTheme(theme: ReaderTheme) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_THEME] = theme.name
        }
    }

    /** Stored clamped into [MIN_FONT_SIZE_PX]..[MAX_FONT_SIZE_PX]. */
    suspend fun setReaderFontSizePx(px: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_FONT_SIZE_PX] =
                px.coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX)
        }
    }

    suspend fun setFontFamily(fontFamily: ReaderFontFamily) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_FONT_FAMILY] = fontFamily.name
        }
    }

    /** Stored clamped into [MIN_LINE_HEIGHT_PCT]..[MAX_LINE_HEIGHT_PCT] (percent of font size). */
    suspend fun setLineHeightPct(pct: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_LINE_HEIGHT_PCT] = pct.coerceIn(MIN_LINE_HEIGHT_PCT, MAX_LINE_HEIGHT_PCT)
        }
    }

    /** Stored clamped into [MIN_MARGIN_PCT]..[MAX_MARGIN_PCT] (percent of viewport width). */
    suspend fun setMarginPct(pct: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_MARGIN_PCT] = pct.coerceIn(MIN_MARGIN_PCT, MAX_MARGIN_PCT)
        }
    }

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
            prefs[Keys.READER_BRIGHTNESS_PCT] = pct.coerceIn(MIN_BRIGHTNESS_PCT, MAX_BRIGHTNESS_PCT)
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

    /** Stored clamped into [MIN_SPEECH_RATE]..[MAX_SPEECH_RATE] (percent of normal rate). */
    suspend fun setSpeechRate(pct: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_SPEECH_RATE] = pct.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        }
    }

    /** Stored clamped into [MIN_SPEECH_PITCH]..[MAX_SPEECH_PITCH] (percent of normal pitch). */
    suspend fun setSpeechPitch(pct: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_SPEECH_PITCH] = pct.coerceIn(MIN_SPEECH_PITCH, MAX_SPEECH_PITCH)
        }
    }

    /** Stored clamped into [MIN_READING_SPEED_WPM]..[MAX_READING_SPEED_WPM]. */
    suspend fun setReadingSpeedWpm(wpm: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_READING_SPEED_WPM] = wpm.coerceIn(MIN_READING_SPEED_WPM, MAX_READING_SPEED_WPM)
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
        dataStore.edit { prefs ->
            val current = decodePerBookAppearance(prefs[Keys.READER_PER_BOOK_APPEARANCE])
            val updated = if (appearance == null) {
                current - itemId
            } else {
                current + (itemId to appearance.copy(
                    fontSizePx = appearance.fontSizePx?.coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX),
                ))
            }
            prefs[Keys.READER_PER_BOOK_APPEARANCE] = PreferenceCodec.json.encodeToString(
                EncodedPerBookAppearance.serializer(),
                EncodedPerBookAppearance(updated),
            )
        }
    }

    /** The exact-resume CFI last recorded for [itemId], or null when reopening at the server percent. */
    fun lastCfi(itemId: String): String? =
        reader.value.lastCfis[itemId]

    /**
     * Records the book's exact resume CFI (docs/adr/0003-local-first-reader-marks.md
     * point 4: local exact resume preferred over the server percent on the
     * same install, while the server keeps receiving the ticks protocol).
     */
    suspend fun setLastCfi(itemId: String, cfi: String) {
        dataStore.edit { prefs ->
            val updated = decodeLastCfis(prefs[Keys.READER_LAST_CFIS]) + (itemId to cfi)
            prefs[Keys.READER_LAST_CFIS] = PreferenceCodec.json.encodeToString(
                EncodedLastCfis.serializer(),
                EncodedLastCfis(updated),
            )
        }
    }

    /** Drops [itemId]'s resume CFI (book removed / marks cleared); other books are untouched. */
    suspend fun clearLastCfi(itemId: String) {
        dataStore.edit { prefs ->
            val updated = decodeLastCfis(prefs[Keys.READER_LAST_CFIS]) - itemId
            prefs[Keys.READER_LAST_CFIS] = PreferenceCodec.json.encodeToString(
                EncodedLastCfis.serializer(),
                EncodedLastCfis(updated),
            )
        }
    }

    /**
     * Clears every reader preference owned by this store. Also reachable via
     * factory reset through [resetKeys]; this is the programmatic single-store
     * form.
     */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.READING_DIRECTIONS)
            prefs.remove(Keys.READER_THEME)
            prefs.remove(Keys.READER_FONT_SIZE_PX)
            prefs.remove(Keys.READER_FONT_FAMILY)
            prefs.remove(Keys.READER_LINE_HEIGHT_PCT)
            prefs.remove(Keys.READER_MARGIN_PCT)
            prefs.remove(Keys.READER_JUSTIFY)
            prefs.remove(Keys.READER_SCROLL_MODE)
            prefs.remove(Keys.READER_BRIGHTNESS_PCT)
            prefs.remove(Keys.READER_VOLUME_KEY_PAGING)
            prefs.remove(Keys.READER_ANIMATED_PAGE_TURNS)
            prefs.remove(Keys.READER_SPEECH_RATE)
            prefs.remove(Keys.READER_SPEECH_PITCH)
            prefs.remove(Keys.READER_READING_SPEED_WPM)
            prefs.remove(Keys.READER_PER_BOOK_APPEARANCE)
            prefs.remove(Keys.READER_LAST_CFIS)
        }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Aggregated by
     * the facade's reset-coverage guard.
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.READING_DIRECTIONS,
        Keys.READER_THEME,
        Keys.READER_FONT_SIZE_PX,
        Keys.READER_FONT_FAMILY,
        Keys.READER_LINE_HEIGHT_PCT,
        Keys.READER_MARGIN_PCT,
        Keys.READER_JUSTIFY,
        Keys.READER_SCROLL_MODE,
        Keys.READER_BRIGHTNESS_PCT,
        Keys.READER_VOLUME_KEY_PAGING,
        Keys.READER_ANIMATED_PAGE_TURNS,
        Keys.READER_SPEECH_RATE,
        Keys.READER_SPEECH_PITCH,
        Keys.READER_READING_SPEED_WPM,
        Keys.READER_PER_BOOK_APPEARANCE,
        Keys.READER_LAST_CFIS,
    )
}

/** The reader preference slice. Defaults mirror [ReaderStore.read]. */
@Immutable
@Serializable
data class ReaderSlice(
    val readingDirections: Map<String, ReadingDirection> = emptyMap(),
    val readerTheme: ReaderTheme = ReaderTheme.DARK,
    val readerFontSizePx: Int = ReaderStore.DEFAULT_FONT_SIZE_PX,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    val lineHeightPct: Int = ReaderStore.DEFAULT_LINE_HEIGHT_PCT,
    val marginPct: Int = ReaderStore.DEFAULT_MARGIN_PCT,
    val justify: Boolean = false,
    val scrollMode: Boolean = false,
    val brightnessPct: Int = ReaderStore.DEFAULT_BRIGHTNESS_PCT,
    val volumeKeyPaging: Boolean = false,
    val animatedPageTurns: Boolean = true,
    val speechRate: Int = ReaderStore.DEFAULT_SPEECH_RATE,
    val speechPitch: Int = ReaderStore.DEFAULT_SPEECH_PITCH,
    val readingSpeedWpm: Int = ReaderStore.DEFAULT_READING_SPEED_WPM,
    val perBookAppearance: Map<String, PerBookAppearance> = emptyMap(),
    val lastCfis: Map<String, String> = emptyMap(),
)

/** JSON carrier so the direction map round-trips through the shared lenient codec. */
@Serializable
private data class EncodedDirections(val directions: Map<String, ReadingDirection>)

/** JSON carrier so the per-book appearance map round-trips through the shared lenient codec. */
@Serializable
private data class EncodedPerBookAppearance(val appearances: Map<String, PerBookAppearance>)

/** JSON carrier so the last-CFI map round-trips through the shared lenient codec. */
@Serializable
private data class EncodedLastCfis(val cfis: Map<String, String>)

private fun decodeDirections(raw: String?): Map<String, ReadingDirection> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        PreferenceCodec.json
            .decodeFromString<EncodedDirections>(raw)
            .directions
    }.getOrDefault(emptyMap())
}

private fun decodePerBookAppearance(raw: String?): Map<String, PerBookAppearance> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        PreferenceCodec.json
            .decodeFromString<EncodedPerBookAppearance>(raw)
            .appearances
    }.getOrDefault(emptyMap())
}

private fun decodeLastCfis(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        PreferenceCodec.json
            .decodeFromString<EncodedLastCfis>(raw)
            .cfis
    }.getOrDefault(emptyMap())
}

private fun decodeTheme(raw: String?): ReaderTheme =
    ReaderTheme.entries.firstOrNull { it.name == raw } ?: ReaderTheme.DARK

private fun decodeFontFamily(raw: String?): ReaderFontFamily =
    ReaderFontFamily.entries.firstOrNull { it.name == raw } ?: ReaderFontFamily.SYSTEM

private fun decodeFontSize(raw: Int?): Int =
    raw?.coerceIn(ReaderStore.MIN_FONT_SIZE_PX, ReaderStore.MAX_FONT_SIZE_PX)
        ?: ReaderStore.DEFAULT_FONT_SIZE_PX

private fun decodeClampedInt(raw: Int?, min: Int, max: Int, default: Int): Int =
    raw?.coerceIn(min, max) ?: default
