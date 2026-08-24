package com.raulshma.jellyplay.feature.arrqueue.di

import com.raulshma.jellyplay.feature.arrqueue.ArrQueueViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the arrqueue feature (docs/kmp-migration-plan.md
 * §Phase V3, V3 conveyor item). The HiltViewModel/@Inject annotations
 * were stripped at the move — Koin is the single constructor owner (one
 * framework per type). Both remaining ctor deps were already Koin-native
 * before this feature moved (no Hilt interop at all, calendar/requests class):
 *  - ArrRepository resolves from dataJvmModule (the legacy ArrModule @Provides
 *    is the reverse bridge, Hilt→Koin);
 *  - ExperimentalStore resolves from the C4 shared-datastore graph
 *    (datastoreCommonModule; SharedStoreModule bridges legacy injectors).
 * The Context + UserMessageBus ctor params were dropped at the move — their
 * only use was resolving ack/fallback strings, now carried unresolved through
 * the ArrQueueMessage screen-forward seam. The whole dep graph resolves on
 * BOTH platforms — the desktop startKoin (dataJvmModule +
 * datastoreCommonModule) can instantiate it directly once a desktop nav entry
 * exists.
 */
val arrqueueModule: Module = module {
    viewModel {
        ArrQueueViewModel(
            arrRepository = get(),
            experimentalStore = get(),
        )
    }
}
