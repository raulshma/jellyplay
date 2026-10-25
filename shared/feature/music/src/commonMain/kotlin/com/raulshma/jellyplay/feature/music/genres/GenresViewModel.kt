package com.raulshma.jellyplay.feature.music.genres

import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.collection.SimpleListCollection

/**
 * Thin adapter over the list-sourced collection for the standalone genres
 * route: the load/refresh/error ladder lives in [SimpleListCollection]; this
 * class only names it. The genre drill-down (navigate to [GenreDetailScreen])
 * is screen-side navigation, not ViewModel state.
 */
class GenresViewModel(
    mediaRepository: LibraryFeed,
) : JellyPlayViewModel() {

    private val collection = SimpleListCollection<Genre>(scope = scope) { force ->
        mediaRepository.getGenres(parentId = null, force = force)
    }

    val genres = collection.items

    val isLoading = collection.isLoading

    val error = collection.error

    fun refresh() = collection.refresh(force = true)
}
