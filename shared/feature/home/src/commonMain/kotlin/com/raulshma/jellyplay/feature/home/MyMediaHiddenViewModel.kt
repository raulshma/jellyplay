package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One "Hidden libraries" list row: the hidden library's id plus the matched
 * folder metadata. [folder] stays null when the libraries read has not
 * succeeded (in flight, offline, or the library was deleted server-side) —
 * the row still renders and still restores by id, so a stale entry is never
 * un-restorable just because its metadata is.
 */
@Immutable
data class MyMediaHiddenLibrary(
    val id: String,
    val folder: LibraryFolder? = null,
)

/** The management screen's snapshot: the rows plus the libraries-in-flight flag. */
@Immutable
data class MyMediaHiddenUiState(
    val libraries: List<MyMediaHiddenLibrary> = emptyList(),
    val loading: Boolean = false,
)

/**
 * ViewModel for the "Hidden libraries" management screen (Route
 * .MyMediaHidden): collects the hidden-library id set from the
 * home-discovery store, resolves each id's display name against the one
 * [MediaRepository.getLibraryFolders] read (library names need no per-id
 * detail fetch — the folder list is the metadata), and restores singly
 * through the store's read-modify-write command or in bulk through
 * `clearMyMediaHiddenLibraries`.
 *
 * Restores route through [PreferencesEditor] so they land on the same store
 * commands the per-library config sheet's hide action writes through.
 */
class MyMediaHiddenViewModel(
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    private val _state = MutableStateFlow(MyMediaHiddenUiState())
    val state: StateFlow<MyMediaHiddenUiState> = _state.asStateFlow()

    /** The library list the hidden ids resolve their names against. */
    private var foldersById: Map<String, LibraryFolder> = emptyMap()

    init {
        scope.launch {
            mediaRepository.getLibraryFolders()
                .onSuccess { folders ->
                    foldersById = folders.associateBy { it.id }
                    republish()
                }
                // A failed read keeps the rows on their id-only placeholder
                // rendering; the restore actions never need the names.
                .onFailure { republish() }
        }
        scope.launch {
            homeDiscoveryStore.homeDiscovery
                .map { it.myMediaHiddenLibraryIds }
                .distinctUntilChanged()
                .collect { republish() }
        }
    }

    /** Re-renders the rows for the current hidden-id set (persisted order). */
    private fun republish() {
        val ids = homeDiscoveryStore.homeDiscovery.value.myMediaHiddenLibraryIds
        _state.value = MyMediaHiddenUiState(
            libraries = ids.map { MyMediaHiddenLibrary(id = it, folder = foldersById[it]) },
            loading = foldersById.isEmpty(),
        )
    }

    /** Restores one library (the row's action) — the store's RMW command. */
    fun restore(libraryId: String) {
        editor.edit { homeDiscovery.showLibraryInMyMedia(libraryId) }
    }

    /** Restores every hidden library (the top-bar "Restore all" action). */
    fun restoreAll() {
        editor.edit { homeDiscovery.clearMyMediaHiddenLibraries() }
    }
}
