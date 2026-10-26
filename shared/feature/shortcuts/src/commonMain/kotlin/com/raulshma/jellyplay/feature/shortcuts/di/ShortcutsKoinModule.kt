package com.raulshma.jellyplay.feature.shortcuts.di

import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
import com.raulshma.jellyplay.feature.shortcuts.DefaultShortcutCatalog
import com.raulshma.jellyplay.feature.shortcuts.ShortcutsViewModel
import com.raulshma.jellyplay.feature.shortcuts.customize.ShortcutsCustomizeViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin construction owner for the Shortcuts feature (docs/kmp-migration-plan.md
 *  feature conveyor). The HiltViewModel/@Inject annotations were
 * stripped at the move — Koin is the single constructor owner (one framework
 * per type). The dep graph resolves on desktop too — nav v1 renders
 * shortcutsSection in the rail (the calendar/requests fully-live
 * registration shape).
 *
 * [DefaultShortcutCatalog] is the single registration site for pickable
 * shortcut targets; it is exposed both concretely (the Shortcuts screen and
 * the editor read the categorized [DefaultShortcutCatalog.items]) and via the
 * core:ui [ShortcutCatalog] interface (the home dock and the shell overflow
 * menu resolve through it, no feature→feature edge).
 */
val shortcutsModule: Module = module {
    single { DefaultShortcutCatalog() } bind ShortcutCatalog::class
    viewModel {
        ShortcutsViewModel(
            authRepository = get(),
            catalog = get(),
        )
    }
    viewModel {
        ShortcutsCustomizeViewModel(
            editor = get(),
            shortcutsStore = get(),
            catalog = get(),
            authRepository = get(),
        )
    }
}
