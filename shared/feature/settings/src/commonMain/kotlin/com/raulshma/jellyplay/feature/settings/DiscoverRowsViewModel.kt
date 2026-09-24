package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.SeerrRowFilters
import com.raulshma.jellyplay.core.model.SeerrRowMedia
import com.raulshma.jellyplay.core.model.SeerrRowSort
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.StudioRef
import com.raulshma.jellyplay.core.model.TmdbGenreRef
import com.raulshma.jellyplay.core.model.newDiscoverRowId
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One user-configurable Discover row, ready to save — the editor's draft
 * (a plain [DiscoverRowConfig] plus the transient editor fields). Mutated
 * only through the `update` folds below, same write-algebra convention as
 * [LibraryFilters].
 */
@Immutable
data class DiscoverRowDraft(
    val row: DiscoverRowConfig,
    /** True while the (debounced) preview fetch is in flight. */
    val previewLoading: Boolean = false,
    /** Preview items for the CURRENT draft (Jellyfin sources only). */
    val previewItems: List<MediaItem> = emptyList(),
    val previewError: String? = null,
)

/**
 * Quick-start row templates offered on the manage screen's empty state (and
 * the add flow). Pre-filled [DiscoverRowConfig]s the user then edits freely.
 */
object DiscoverRowTemplates {
    fun unwatchedMovies(): DiscoverRowConfig = DiscoverRowConfig(
        id = newDiscoverRowId(),
        title = "Unwatched Movies",
        filters = LibraryFilters(mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.MOVIE), sortBy = SortOption.RANDOM),
    )

    fun highlyRated(): DiscoverRowConfig = DiscoverRowConfig(
        id = newDiscoverRowId(),
        title = "Highly Rated Gems",
        filters = LibraryFilters(minRating = 7.5f, playedStatus = PlayedStatus.UNPLAYED, sortBy = SortOption.RATING),
    )

    fun newThisMonth(): DiscoverRowConfig = DiscoverRowConfig(
        id = newDiscoverRowId(),
        title = "New This Month",
        filters = LibraryFilters(sortBy = SortOption.DATE_ADDED),
        addedWithinDays = 30,
    )

    fun randomSurprise(): DiscoverRowConfig = DiscoverRowConfig(
        id = newDiscoverRowId(),
        title = "Random Surprise",
    )

    fun trendingSeerr(): DiscoverRowConfig = DiscoverRowConfig(
        id = newDiscoverRowId(),
        title = "Trending on Seerr",
        source = DiscoverRowSource.SEERR,
        seerrFilters = SeerrRowFilters(media = SeerrRowMedia.MOVIE, sort = SeerrRowSort.POPULARITY),
    )
}

/**
 * ViewModel for both Discover surfaces: the manage screen (list, reorder,
 * toggle, delete) and the editor (draft + preview + save). The store's
 * commands are the single write path; the editor holds an unattached draft
 * until Save.
 */
class DiscoverRowsViewModel(
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    /** The user's rows, config order — the manage screen's list. */
    val discoverRowsFlow: StateFlow<List<DiscoverRowConfig>> =
        homeDiscoveryStore.homeDiscovery
            .map { it.discoverRows }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Editor state ────────────────────────────────────────────────────────

    private val _draft = MutableStateFlow<DiscoverRowDraft?>(null)
    val draft: StateFlow<DiscoverRowDraft?> = _draft.asStateFlow()

    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _studios = MutableStateFlow<List<Studio>>(emptyList())
    val studios: StateFlow<List<Studio>> = _studios.asStateFlow()

    /** Live People-picker results for the current search keystroke (debounced). */
    private val _peopleResults = MutableStateFlow<List<PersonRef>>(emptyList())
    val peopleResults: StateFlow<List<PersonRef>> = _peopleResults.asStateFlow()

    private var peopleSearchJob: Job? = null

    private var previewJob: Job? = null

    init {
        loadEditorCatalogs()
    }

    private fun loadEditorCatalogs() {
        launch {
            mediaRepository.getLibraryFolders().onSuccess { _libraryFolders.value = it }
            mediaRepository.getGenres().onSuccess { _genres.value = it }
            mediaRepository.getStudios().onSuccess { _studios.value = it }
            mediaRepository.getTags().onSuccess { _tags.value = it }
        }
    }

    /** Opens the editor on a new draft seeded from [template] (or defaults). */
    fun startNew(template: DiscoverRowConfig? = null) {
        _draft.value = DiscoverRowDraft(row = template ?: DiscoverRowConfig(id = newDiscoverRowId(), title = ""))
        refreshPreview()
    }

    /** Opens the editor on an existing row (copy — Save writes the upsert). */
    fun startEdit(rowId: String) {
        val row = homeDiscoveryStore.homeDiscovery.value.discoverRows.find { it.id == rowId } ?: return
        _draft.value = DiscoverRowDraft(row = row)
        refreshPreview()
    }

    fun closeEditor() {
        previewJob?.cancel()
        _draft.value = null
    }

    /** Applies [transform] to the draft's row and re-rolls the preview. */
    fun updateRow(transform: (DiscoverRowConfig) -> DiscoverRowConfig) {
        val current = _draft.value ?: return
        _draft.value = current.copy(row = transform(current.row), previewItems = emptyList(), previewError = null)
        refreshPreview()
    }

    /** Saves the draft (add-or-replace by id) and closes the editor. */
    fun saveDraft() {
        val row = _draft.value?.row ?: return
        if (row.title.isBlank()) return
        editor.edit { homeDiscovery.upsertDiscoverRow(row) }
        closeEditor()
    }

    /** Saves the draft as a NEW row (new id), keeping the editor open on the original. */
    fun duplicateDraft() {
        val row = _draft.value?.row ?: return
        if (row.title.isBlank()) return
        editor.edit { homeDiscovery.upsertDiscoverRow(row.copy(id = newDiscoverRowId(), title = "${row.title} (2)")) }
    }

    // ── Manage-screen commands (store write path) ───────────────────────────

    fun removeDiscoverRow(rowId: String) {
        editor.edit { homeDiscovery.removeDiscoverRow(rowId) }
    }

    // These two route through the store's own read-modify-write commands
    // (which read the CURRENT persisted state inside the edit) — NOT through
    // setDiscoverRows with a value snapshotted off the StateFlow mirror,
    // which loses an update when a rapid toggle+move pair interleaves before
    // the mirror republishes.
    fun setDiscoverRowEnabled(rowId: String, enabled: Boolean) {
        editor.edit { homeDiscovery.setDiscoverRowEnabled(rowId, enabled) }
    }

    fun moveDiscoverRow(rowId: String, up: Boolean) {
        editor.edit { homeDiscovery.moveDiscoverRow(rowId, up) }
    }

    /** Creates a row from a template directly on the manage screen. */
    fun addFromTemplate(template: DiscoverRowConfig) {
        editor.edit { homeDiscovery.upsertDiscoverRow(template) }
    }

    // ── People picker ───────────────────────────────────────────────────────

    /**
     * Person lookup for the editor's People picker. Debounced so a burst of
     * keystrokes costs one query per typing pause; a blank term clears the
     * results instead of querying (the sheet falls back to the selected list).
     */
    fun searchPeople(query: String) {
        peopleSearchJob?.cancel()
        val term = query.trim()
        if (term.isEmpty()) {
            _peopleResults.value = emptyList()
            return
        }
        peopleSearchJob = launch {
            delay(300)
            mediaRepository.getPeople(term, limit = 30).onSuccess { _peopleResults.value = it }
        }
    }

    // ── Preview ─────────────────────────────────────────────────────────────

    /**
     * Re-fetches the draft's first items (Jellyfin sources only — Seerr rows
     * have no server-side preview path in the editor). Debounced + cancelled
     * by the next update so a burst of chip toggles costs at most one fetch.
     */
    private fun refreshPreview() {
        previewJob?.cancel()
        val current = _draft.value ?: return
        if (current.row.source != DiscoverRowSource.JELLYFIN) return
        previewJob = launch {
            _draft.value = current.copy(previewLoading = true)
            val result = mediaRepository.getDiscoverRowItems(current.row.copy(limit = 12))
            // The draft may have moved on while the fetch ran — patch only if
            // the row id still matches.
            val latest = _draft.value ?: return@launch
            if (latest.row.id != current.row.id) return@launch
            _draft.value = result.fold(
                onSuccess = { latest.copy(previewLoading = false, previewItems = it, previewError = null) },
                onFailure = { latest.copy(previewLoading = false, previewItems = emptyList(), previewError = it.message) },
            )
        }
    }
}
