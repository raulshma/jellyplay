package com.raulshma.jellyplay.feature.syncplay.di

import com.raulshma.jellyplay.feature.syncplay.SyncPlayViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the SyncPlay feature (docs/kmp-migration-plan.md
 * §Phase V3, sixth conveyor item after search, library, music, livetv and
 * downloads). The HiltViewModel/@Inject/@ApplicationContext annotations were
 * stripped at the move — Koin is the single constructor owner (one framework
 * per type). Ctor deps split three ways:
 *  - MediaRepository is still Hilt-owned in the legacy data shim and reaches
 *    Koin through the app composition root's Hilt interop module (dies at
 *    Phase X);
 *  - SyncPlayCastStore resolves from the C4 shared-datastore graph;
 *  - SyncPlayManager resolves from dataJvmModule (the SyncPlay stack moved
 *    into :shared:core:data's jvmShared source set during the engine phase).
 */
val syncPlayModule: Module = module {
    viewModel {
        SyncPlayViewModel(
            syncPlayRepository = get(),
            syncPlayManager = get(),
            syncPlayCastStore = get(),
        )
    }
}
