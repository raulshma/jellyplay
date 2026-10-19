package com.raulshma.jellyplay.feature.arrqueue.di

import com.raulshma.jellyplay.feature.arrqueue.ArrQueueViewModel
import com.raulshma.jellyplay.feature.arrqueue.ReleaseSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the arrqueue feature. Koin is the single
 * constructor owner (one framework per type). Both ctor deps resolve from the
 * shared graph:
 *  - ArrRepository resolves from dataJvmModule (the ArrModule @Provides
 *    binding bridges Hilt→Koin);
 *  - ExperimentalFeatureGate resolves from the shared-datastore graph
 *    (datastoreCommonModule; SharedStoreModule bridges Hilt injectors) — the
 *    shared eagerly-created Direct-*arr gate.
 *
 * The whole dep graph resolves on BOTH platforms — the desktop startKoin
 * (dataJvmModule + datastoreCommonModule) can instantiate it directly once a
 * desktop nav entry exists.
 */
val arrqueueModule: Module = module {
    viewModel {
        ArrQueueViewModel(
            arrRepository = get(),
            experimentalGate = get(),
        )
    }
    // The release sheet's driver over the ArrReleaseOperations seam (the
    // dataSeerrArrModule binding over ArrRepositoryImpl).
    viewModel {
        ReleaseSearchViewModel(
            arrReleaseOperations = get(),
        )
    }
}
