package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.model.Genre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared owner of the two filter-sheet dimensions — genres and tags — that
 * library, search, and the Discover-row editor all load off [MediaRepository]/
 * `LibraryApiClient` the same way. Extracted from the three ViewModels'
 * byte-identical `_genres`/`_tags` StateFlow pairs + `loadListWithRetry`
 * launchers; each VM now holds one instance and exposes the holder's flows
 * (public exposure shape unchanged), so the retry policy and the
 * empty-until-loaded lifecycle live in exactly one place.
 *
 * The two loads run as independent coroutines on the caller's [scope] (the
 * VMs' `viewModelScope`), mirroring the former one-launch-per-dimension
 * shape. Failure semantics are [loadListWithRetry]'s: one best-effort retry
 * after [FILTER_RETRY_DELAY_MS], then the dimension simply stays empty — the
 * filter sheet renders without the missing section, exactly as before.
 *
 * Note the holder deliberately covers ONLY genres/tags — the studios and
 * library-folder catalogs have different shapes (no force lever on studios;
 * see the TtlCache drift note in CONTEXT) and stay with their owners.
 */
class FilterDimensionsHolder(
    private val scope: CoroutineScope,
    private val getGenres: suspend (force: Boolean) -> Result<List<Genre>>,
    private val getTags: suspend () -> Result<List<String>>,
) {

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())

    /** Genres for the filter sheets; empty until a load succeeds. */
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())

    /** Tag names for the filter sheets; empty until a load succeeds. */
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    /**
     * (Re)loads both dimensions. [force] bypasses the genres cache for a
     * caller-owned refresh (the library screen's pull-to-refresh path); tags
     * have no force lever and always read the browse cache, as before.
     * Safe to call repeatedly — every call launches a fresh best-effort pair.
     */
    fun load(force: Boolean = false) {
        scope.launch {
            // Retry once after a short delay so a transient network blip doesn't
            // leave the filter sheet permanently missing its Genres section.
            loadListWithRetry({ getGenres(force) }) { _genres.value = it }
        }
        scope.launch {
            loadListWithRetry(getTags) { _tags.value = it }
        }
    }
}
