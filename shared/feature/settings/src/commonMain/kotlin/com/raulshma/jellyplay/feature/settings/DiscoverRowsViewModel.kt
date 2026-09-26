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
import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.uiTextOf
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_template_highly_rated
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_template_new_this_month
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_template_random_surprise
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_template_trending_seerr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_template_unwatched_movies
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
 * Typing-pause window (ms) shared by the editor's two debounced queries: the
 * People-picker search and the draft preview refresh.
 */
private const val EDITOR_DEBOUNCE_MS = 300L

/** How many first items the editor's preview strip fetches for a draft. */
private const val PREVIEW_ITEM_COUNT = 12

/**
 * One user-configurable Discover row, ready to save — the editor's draft
 * (a plain [DiscoverRowConfig] plus the transient editor fields). Mutated
 * only through the `update` folds below, same write-algebra convention as
 * [LibraryFilters].
 */
@Immutable
data class DiscoverRowDraft(
    val row: DiscoverRowConfig,
    /** True while a preview refresh is pending or in flight (latches on with the first edit and holds across the debounce). */
    val previewLoading: Boolean = false,
    /** Preview items for the CURRENT draft (Jellyfin sources only). */
    val previewItems: List<MediaItem> = emptyList(),
    val previewError: String? = null,
)

/**
 * One quick-start row template: the localized display title ([UiText],
 * resolved by the render site) paired with the pre-filled config factory.
 * The factory takes the RESOLVED title because a saved row's title is user
 * data — the localized string is baked in at the moment of creation, exactly
 * as if the user had typed it, and the user then edits freely.
 */
data class DiscoverRowTemplate(
    /** Localized template title; render sites resolve via [UiText.asString]. */
    val title: UiText,
    /** Builds the pre-filled [DiscoverRowConfig] under the resolved title (a fresh id per call). */
    val create: (title: String) -> DiscoverRowConfig,
)

/**
 * Quick-start row templates offered on the manage screen's empty state (and
 * the add flow). Pre-filled [DiscoverRowConfig]s the user then edits freely.
 */
object DiscoverRowTemplates {
    val unwatchedMovies: DiscoverRowTemplate = DiscoverRowTemplate(
        title = uiTextOf(Res.string.settings_discover_row_template_unwatched_movies),
        create = { title ->
            DiscoverRowConfig(
                id = newDiscoverRowId(),
                title = title,
                filters = LibraryFilters(mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.MOVIE), sortBy = SortOption.RANDOM),
            )
        },
    )

    val highlyRated: DiscoverRowTemplate = DiscoverRowTemplate(
        title = uiTextOf(Res.string.settings_discover_row_template_highly_rated),
        create = { title ->
            DiscoverRowConfig(
                id = newDiscoverRowId(),
                title = title,
                filters = LibraryFilters(minRating = 7.5f, playedStatus = PlayedStatus.UNPLAYED, sortBy = SortOption.RATING),
            )
        },
    )

    val newThisMonth: DiscoverRowTemplate = DiscoverRowTemplate(
        title = uiTextOf(Res.string.settings_discover_row_template_new_this_month),
        create = { title ->
            DiscoverRowConfig(
                id = newDiscoverRowId(),
                title = title,
                filters = LibraryFilters(sortBy = SortOption.DATE_ADDED),
                addedWithinDays = 30,
            )
        },
    )

    val randomSurprise: DiscoverRowTemplate = DiscoverRowTemplate(
        title = uiTextOf(Res.string.settings_discover_row_template_random_surprise),
        create = { title ->
            DiscoverRowConfig(
                id = newDiscoverRowId(),
                title = title,
            )
        },
    )

    val trendingSeerr: DiscoverRowTemplate = DiscoverRowTemplate(
        title = uiTextOf(Res.string.settings_discover_row_template_trending_seerr),
        create = { title ->
            DiscoverRowConfig(
                id = newDiscoverRowId(),
                title = title,
                source = DiscoverRowSource.SEERR,
                seerrFilters = SeerrRowFilters(media = SeerrRowMedia.MOVIE, sort = SeerrRowSort.POPULARITY),
            )
        },
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
     * Relaunches [block] after the [EDITOR_DEBOUNCE_MS] typing pause,
     * cancelling [previous] first — the shared debounce shape of
     * [searchPeople] and [refreshPreview]: a burst of keystrokes or chip
     * toggles costs at most one downstream query.
     */
    private fun debounced(previous: Job?, block: suspend () -> Unit): Job {
        previous?.cancel()
        return launch {
            delay(EDITOR_DEBOUNCE_MS)
            block()
        }
    }

    /**
     * Person lookup for the editor's People picker. Debounced ([debounced])
     * so a burst of keystrokes costs one query per typing pause; a blank term
     * clears the results instead of querying (the sheet falls back to the
     * selected list).
     */
    fun searchPeople(query: String) {
        val term = query.trim()
        if (term.isEmpty()) {
            peopleSearchJob?.cancel()
            _peopleResults.value = emptyList()
            return
        }
        peopleSearchJob = debounced(peopleSearchJob) {
            mediaRepository.getPeople(term, limit = 30).onSuccess { _peopleResults.value = it }
        }
    }

    // ── Preview ─────────────────────────────────────────────────────────────

    /**
     * Re-fetches the draft's first items (Jellyfin sources only — Seerr rows
     * have no server-side preview path in the editor). The fetch itself is
     * debounced ([debounced], the same typing pause as [searchPeople]) and
     * cancelled by the next draft update, so a burst of title keystrokes or
     * chip toggles costs at most one fetch; [DiscoverRowDraft.previewLoading]
     * latches on immediately so the editor shows the loading state across the
     * burst. The result guard compares the whole row, not just the id — a
     * fetch answering an older query (say, a repository that swallows
     * cancellation into a `Result`) must never land on a draft the user has
     * since mutated.
     */
    private fun refreshPreview() {
        val current = _draft.value ?: return
        previewJob?.cancel()
        if (current.row.source != DiscoverRowSource.JELLYFIN) {
            // No preview surface for Seerr rows — also drop any latched
            // loading flag so a mid-flight source switch can't stick it.
            if (current.previewLoading) _draft.value = current.copy(previewLoading = false)
            return
        }
        if (!current.previewLoading) _draft.value = current.copy(previewLoading = true)
        val queriedRow = current.row
        previewJob = debounced(previewJob) {
            val result = mediaRepository.getDiscoverRowItems(queriedRow.copy(limit = PREVIEW_ITEM_COUNT))
            // The draft may have moved on while the debounce window and the
            // fetch ran — apply only onto the exact row that was queried
            // (guards a draft SWITCH and an in-place chip MUTATION alike;
            // row.id alone would miss the latter).
            val latest = _draft.value ?: return@debounced
            if (latest.row != queriedRow) return@debounced
            _draft.value = result.fold(
                onSuccess = { latest.copy(previewLoading = false, previewItems = it, previewError = null) },
                onFailure = { latest.copy(previewLoading = false, previewItems = emptyList(), previewError = it.message) },
            )
        }
    }
}
