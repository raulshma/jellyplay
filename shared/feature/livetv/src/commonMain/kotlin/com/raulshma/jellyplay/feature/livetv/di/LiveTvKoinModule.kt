package com.raulshma.jellyplay.feature.livetv.di

import com.raulshma.jellyplay.feature.livetv.LiveTvOverviewViewModel
import com.raulshma.jellyplay.feature.livetv.channeldetail.ChannelDetailViewModel
import com.raulshma.jellyplay.feature.livetv.channels.ChannelsViewModel
import com.raulshma.jellyplay.feature.livetv.epg.EpgViewModel
import com.raulshma.jellyplay.feature.livetv.programs.ProgramsViewModel
import com.raulshma.jellyplay.feature.livetv.recordings.RecordingsViewModel
import com.raulshma.jellyplay.feature.livetv.schedule.ScheduleViewModel
import com.raulshma.jellyplay.feature.livetv.series.SeriesViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the Live TV feature (docs/kmp-migration-plan.md
 * §Phase V3, fourth conveyor item after search, library and music). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Ctor deps split three
 * ways:
 *  - MediaRepository is still Hilt-owned in the legacy data shim and reaches
 *    Koin through the app composition root's Hilt interop module (dies at
 *    Phase X);
 *  - ImageUrlProvider (shared data) and AppRuntimeStateStore (shared
 *    datastore) resolve from the C4 shared-module graph;
 *  - VideoMiniPlayerState resolves from dataJvmModule (V3 livetv conveyor:
 *    the holder moved into :shared:core:data — Koin-owned, bridged back to
 *    the legacy Hilt injectors by the legacy DataModule).
 *
 * ChannelDetailViewModel's record/cancel feedback no longer goes through the
 * Android-only UserMessageBus: it emits LiveTvUserMessage values on a
 * messages Flow that ChannelDetailScreen renders via the LiveTvMessenger
 * actual (bus→flow seam, same shape as the library conveyor's UserMessenger).
 */
val liveTvModule: Module = module {
    viewModel {
        LiveTvOverviewViewModel(
            mediaRepository = get(),
        )
    }
    viewModel {
        ChannelDetailViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        ChannelsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            appRuntimeStateStore = get(),
            videoMiniPlayerState = get(),
        )
    }
    viewModel {
        EpgViewModel(
            mediaRepository = get(),
        )
    }
    viewModel {
        ProgramsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        RecordingsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        ScheduleViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        SeriesViewModel(
            mediaRepository = get(),
        )
    }
}
