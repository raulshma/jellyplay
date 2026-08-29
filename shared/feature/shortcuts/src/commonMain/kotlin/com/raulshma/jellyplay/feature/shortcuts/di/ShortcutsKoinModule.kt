package com.raulshma.jellyplay.feature.shortcuts.di

import com.raulshma.jellyplay.feature.shortcuts.ShortcutsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the Shortcuts feature (docs/kmp-migration-plan.md
 * §Phase V3 feature conveyor). The HiltViewModel/@Inject annotations were
 * stripped at the move — Koin is the single constructor owner (one framework
 * per type). The single ctor dep (AuthRepository) was already Koin-native on
 * BOTH platforms before this feature moved: it resolves from dataJvmModule.
 * The whole dep graph resolves on desktop too — nav v1 renders
 * shortcutsSection in the rail (the calendar/requests fully-live
 * registration shape).
 */
val shortcutsModule: Module = module {
    viewModel {
        ShortcutsViewModel(
            authRepository = get(),
        )
    }
}
