package com.raulshma.jellyplay.feature.insights.di

import com.raulshma.jellyplay.feature.insights.heatmap.WatchProgressHeatmapViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the insights feature (docs/kmp-migration-plan.md
 * §Phase V3 conveyor). The HiltViewModel/@Inject annotations were stripped at
 * the move — Koin is the single constructor owner (one framework per type).
 * Ctor deps split two ways:
 *  - WatchHistoryRepository and PlaybackRepository are Koin-native
 *    (dataJvmModule in :shared:core:data — desktop resolves them too);
 *  - MediaRepository's interface lives in shared :core:data commonMain but
 *    its impl is still Hilt-owned (DataModule @Binds), so on Android it
 *    reaches Koin through the app composition root's Hilt interop module
 *    (dies at Phase X).
 *
 * Desktop: no MediaRepository definition exists yet, so resolution of this
 * ViewModel would throw NoDefinitionFound — registered inert-latent like the
 * other pre-Phase-X conveyor modules (the desktop shell has no insights nav
 * entry; grep confirms no route/screen reference).
 */
val insightsModule: Module = module {
    viewModel {
        WatchProgressHeatmapViewModel(
            watchHistoryRepository = get(),
            mediaRepository = get(),
            playbackRepository = get(),
        )
    }
}
