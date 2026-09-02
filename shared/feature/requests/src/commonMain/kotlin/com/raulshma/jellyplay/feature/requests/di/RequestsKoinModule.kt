package com.raulshma.jellyplay.feature.requests.di

import com.raulshma.jellyplay.feature.requests.RequestsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the Requests feature (docs/kmp-migration-plan.md
 * §Phase V3, eleventh conveyor item after search, library, music, livetv,
 * downloads, syncplay, settings, admin, editor and calendar). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). All three ctor deps were
 * already Koin-native before this feature moved (no Hilt interop at all):
 *  - SeerrRepository and ArrRepository resolve from dataJvmModule (the legacy
 *    SeerrModule/ArrModule @Provides are the reverse bridge, Hilt→Koin);
 *  - ExperimentalStore resolves from the C4 shared-datastore graph
 *    (datastoreCommonModule; SharedStoreModule bridges legacy injectors).
 * The whole dep graph resolves on BOTH platforms — the desktop startKoin
 * (dataJvmModule + datastoreCommonModule) can instantiate it directly once a
 * desktop nav entry exists (calendar, landed just before, was the first with
 * this property; requests shares the same three-dep shape).
 */
val requestsModule: Module = module {
    viewModel {
        RequestsViewModel(
            seerrRepository = get(),
            arrRepository = get(),
            experimentalStore = get(),
        )
    }
}
