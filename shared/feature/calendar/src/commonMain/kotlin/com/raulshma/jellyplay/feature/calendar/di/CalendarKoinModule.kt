package com.raulshma.jellyplay.feature.calendar.di

import com.raulshma.jellyplay.feature.calendar.UpcomingCalendarViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the calendar feature. Koin is the single
 * constructor owner (one framework per type). All three ctor deps resolve
 * from the shared graph — no platform interop edges:
 *  - ArrRepository + SeerrRepository resolve from dataJvmModule
 *    (:shared:core:data) on both platforms;
 *  - ExperimentalFeatureGate resolves from datastoreCommonModule
 *    (:shared:core:datastore) — the shared eagerly-created Direct-*arr gate.
 *
 * The desktop registration is fully live: nav v1 renders calendarSection in
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
