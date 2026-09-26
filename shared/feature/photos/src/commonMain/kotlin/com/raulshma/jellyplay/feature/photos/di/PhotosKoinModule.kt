package com.raulshma.jellyplay.feature.photos.di

import com.raulshma.jellyplay.feature.photos.PhotoAlbumViewModel
import com.raulshma.jellyplay.feature.photos.PhotoViewerViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the photo suite (extracted from feature/library
 * at the photo-suite move — these were the trailing two definitions of the
 * former libraryModule, feature/library's di/LibraryKoinModule.kt). Ctor
 * deps: MediaRepository / ImageUrlProvider resolve from the shared data
 * graph; PhotoExport comes from the per-platform export module
 * (androidPhotoExportModule / desktopPhotoExportModule, the shells' inline
 * platform registrations — this module moved with the suite).
 *
 * Registered through shared/feature/shell's sharedFeatureModules (the one
 * declaration both JVM shells spread — KoinModuleRegistrationGuardTest
 * derives the expected set from it).
 */
val photosModule: Module = module {
    viewModel {
        PhotoAlbumViewModel(
            mediaRepository = get(),
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
