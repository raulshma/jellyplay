package com.raulshma.jellyplay.feature.library.di

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.feature.library.FavoritesViewModel
import com.raulshma.jellyplay.feature.library.LibraryViewModel
import com.raulshma.jellyplay.feature.library.StudioDetailViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the library feature. Koin is the single
 * constructor owner (one framework per type). Ctor deps split three ways:
 *  - MediaRepository / UserDataMutator / PhotoFolderPrefetcher are Hilt-owned
 *    and reach Koin through the app
 *    composition root's Hilt interop module;
 *  - ImageUrlProvider / ArtworkUrls (shared data) and LibraryStore (shared
 *    datastore) resolve from the shared-module graph;
 *  - PhotoExport resolves from feature/photos' photosModule, which carries the
 *    per-platform export modules (androidPhotoExportModule /
 *    desktopPhotoExportModule) along with the photo suite (PhotoAlbum/PhotoViewer
 *    VMs + the export seam).
 *
 * StudioDetailViewModel's SavedStateHandle is pulled from the definition
 * parameters: on Android, Koin synthesizes it from the CreationExtras of the
 * LocalViewModelStoreOwner current at the call site (nav3 1.1.5 installs no
 * per-entry ViewModelStoreOwner, so that owner is the Activity).
 *
 * QuickDownloadActions is core:data's own wall-crossing seam (declared,
 * implemented and bound there — jvmShared adapter in
 * dataJvmModule), so this module needs no
 * platform fragment for it.
 */
val libraryModule: Module = module {
    viewModel {
        LibraryViewModel(
            mediaRepository = get(),
            mediaCatalogueEnrichments = get(),
            offlineRepository = get(),
            quickDownloadActions = get(),
            offlineModeManager = get(),
            userMessageBus = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
            artworkUrls = get(),
            photoFolderPrefetcher = get(),
            libraryStore = get(),
        )
    }
    viewModel {
        FavoritesViewModel(
            libraryFeed = get(),
            userDataChanges = get<com.raulshma.jellyplay.core.data.repository.UserDataChanges>().userDataChanges,
            userDataMutator = get(),
            imageUrlProvider = get(),
            artworkUrls = get(),
            quickDownloadActions = get(),
            photoFolderPrefetcher = get(),
        )
    }
    viewModel { params ->
        StudioDetailViewModel(
            savedStateHandle = params.get<SavedStateHandle>(),
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
            artworkUrls = get(),
            quickDownloadActions = get(),
        )
    }
}
