package com.raulshma.jellyplay.feature.search.di

import com.raulshma.jellyplay.feature.search.SearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the search feature (docs/kmp-migration-plan.md
 *,  first conveyor item). The HiltViewModel/@Inject annotations were
 * stripped at the move — Koin is the single constructor owner (one framework
 * per type). The three ctor deps whose impls are still Hilt-owned in the
 * legacy data shim (MediaRepository, UserDataMutator, MediaSearchEngine,
 * pending the DownloadRepository flip) reach Koin through the app
 * composition root's Hilt interop module; the rest resolve from the C4
 * shared-module graph. QuickDownloadActions is core:data's own wall-crossing
 * seam (declared, implemented and bound there — jvmShared
 * adapter in dataJvmModule), so this module
 * needs no platform fragment for it.
 */
val searchModule: Module = module {
    viewModel {
        SearchViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
            seerrRepository = get(),
            seerrRequestDelegate = get(),
            mediaSearchEngine = get(),
            offlineRepository = get(),
            searchFiltersStore = get(),
            quickDownloadActions = get(),
        )
    }
}
