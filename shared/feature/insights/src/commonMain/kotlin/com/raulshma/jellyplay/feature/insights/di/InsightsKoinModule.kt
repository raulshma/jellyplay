package com.raulshma.jellyplay.feature.insights.di

import com.raulshma.jellyplay.feature.insights.heatmap.WatchProgressHeatmapViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the insights feature (docs/kmp-migration-plan.md
 * §Phase V3 conveyor). The HiltViewModel/@Inject annotations were stripped at
 * the move — Koin is the single constructor owner (one framework per type).
 * All three ctor deps are Koin-native on BOTH platforms — WatchHistoryRepository,
 * PlaybackRepository and MediaRepository (since the Phase X cluster flip)
 * resolve from dataJvmModule in :shared:core:data. Desktop is live: nav v1
 * renders insightsSection in the rail (and the share seam's wave-20C desktop
 * actual writes a tmpdir PNG, so the share button works there).
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
