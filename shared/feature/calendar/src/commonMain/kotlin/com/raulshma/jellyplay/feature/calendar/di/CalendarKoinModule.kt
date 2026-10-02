package com.raulshma.jellyplay.feature.calendar.di

import com.raulshma.jellyplay.feature.calendar.UpcomingCalendarViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the calendar feature (docs/kmp-migration-plan.md
 *  conveyor). The HiltViewModel/@Inject annotations were stripped at
 * the move — Koin is the single constructor owner (one framework per type).
 * Unlike every earlier conveyor module, ALL three ctor deps are already
 * Koin-native in the shared graph — no platform interop edges at all:
 *  - ArrRepository + SeerrRepository resolve from dataJvmModule
 *    (:shared:core:data) on both platforms;
 *  - ExperimentalFeatureGate resolves from datastoreCommonModule
 *    (:shared:core:datastore) — the shared eagerly-shared Direct-*arr gate,
 *    replacing this VM's hand-rolled `stateIn(Eagerly)` copy.
 * The desktop registration is therefore fully live (first conveyor
 * module with zero platform-shaped defs): nav v1 renders calendarSection in
 * the rail.
 */
val calendarModule: Module = module {
    viewModel {
        UpcomingCalendarViewModel(
            arrRepository = get(),
            seerrRepository = get(),
            experimentalGate = get(),
        )
    }
}
