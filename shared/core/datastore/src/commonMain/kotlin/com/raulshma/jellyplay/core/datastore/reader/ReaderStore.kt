package com.raulshma.jellyplay.core.datastore.reader

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * Reader preference domain (book reading): the per-book page-flow direction
 * map (itemId → [ReadingDirection]) plus the global reflowable appearance
 * ([ReaderTheme], font size). Defaults to [ReadingDirection.LTR] for
 * any book without an explicit choice; RTL is the manga reader's per-title
 * override.
 *
 * The map is stored as one JSON-encoded string under a single key rather than
 * per-item preference keys: the reset-key list stays static (per-item keys
 * could not be enumerated for factory reset), and a write is one atomic edit.
 * Map size is bounded by the user's downloaded-library count, so the blob
 * stays small.
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
    }

    companion object {
        const val DEFAULT_FONT_SIZE_PX = 17
        const val MIN_FONT_SIZE_PX = 12
        const val MAX_FONT_SIZE_PX = 32
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
    )
}

/** The reader preference slice. Defaults mirror [ReaderStore.read]. */
@Immutable
@Serializable
data class ReaderSlice(
    val readingDirections: Map<String, ReadingDirection> = emptyMap(),
    val readerTheme: ReaderTheme = ReaderTheme.DARK,
    val readerFontSizePx: Int = ReaderStore.DEFAULT_FONT_SIZE_PX,
)

/** JSON carrier so the direction map round-trips through the shared lenient codec. */
@Serializable
private data class EncodedDirections(val directions: Map<String, ReadingDirection>)

private fun decodeDirections(raw: String?): Map<String, ReadingDirection> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        PreferenceCodec.json
            .decodeFromString<EncodedDirections>(raw)
            .directions
    }.getOrDefault(emptyMap())
}

private fun decodeTheme(raw: String?): ReaderTheme =
    ReaderTheme.entries.firstOrNull { it.name == raw } ?: ReaderTheme.DARK

private fun decodeFontSize(raw: Int?): Int =
    raw?.coerceIn(ReaderStore.MIN_FONT_SIZE_PX, ReaderStore.MAX_FONT_SIZE_PX)
        ?: ReaderStore.DEFAULT_FONT_SIZE_PX
