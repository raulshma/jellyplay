package com.raulshma.jellyplay.feature.library.di

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.feature.library.FavoritesViewModel
import com.raulshma.jellyplay.feature.library.LibraryViewModel
import com.raulshma.jellyplay.feature.library.PhotoAlbumViewModel
import com.raulshma.jellyplay.feature.library.PhotoViewerViewModel
import com.raulshma.jellyplay.feature.library.StudioDetailViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the library feature (docs/kmp-migration-plan.md
 * §Phase V3, second conveyor item after search). The HiltViewModel/@Inject
 * annotations were stripped at the move — Koin is the single constructor owner
 * (one framework per type). Ctor deps split three ways:
 *  - MediaRepository / UserDataMutator / PhotoFolderPrefetcher are still
 *    Hilt-owned in the legacy data shim and reach Koin through the app
 *    composition root's Hilt interop module (dies at Phase X);
 *  - ImageUrlProvider (shared data) and LibraryStore (shared datastore)
 *    resolve from the C4 shared-module graph;
 *  - PhotoExport comes from the per-platform export module
 *    (androidPhotoExportModule / desktopPhotoExportModule).
 *
 * StudioDetailViewModel's SavedStateHandle is pulled from the definition
 * parameters: on Android, Koin synthesizes it from the CreationExtras of the
 * LocalViewModelStoreOwner current at the call site (nav3 1.1.5 installs no
 * per-entry ViewModelStoreOwner, so that owner is the Activity — the exact
 * same extras source the Hilt factory consumed at HEAD).
 */
val libraryModule: Module = module {
    viewModel {
        LibraryViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
            photoFolderPrefetcher = get(),
            libraryStore = get(),
        )
    }
    viewModel {
        FavoritesViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        PhotoAlbumViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel { params ->
        StudioDetailViewModel(
            savedStateHandle = params.get<SavedStateHandle>(),
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        PhotoViewerViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            photoExport = get(),
        )
    }
}
