package com.raulshma.jellyplay.feature.subtitle.tester.di

import android.content.Context
import com.raulshma.jellyplay.feature.subtitle.tester.SubtitleTesterViewModel
import com.raulshma.jellyplay.feature.subtitle.tester.preview.PlaybackRequestFactory
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the subtitle tester (docs/kmp-migration-plan.md
 * §Phase V3, subtitle-tester conveyor — final feature). The whole feature is
 * Android-only: the ViewModel, screen, preview engine host and raw-asset
 * factory live in this module's androidMain (admin androidAdminModule
 * pattern), so — unlike every earlier conveyor feature — there is NO
 * commonMain Koin module and the desktop app registers nothing for this
 * feature at all. The shared settings-search row for Route.SubtitleTester
 * dead-clicks on desktop (same dormant state as every un-wired desktop
 * route).
 *
 * Ctor deps:
 *  - PlayerEngineFactory + FontProvider resolve through the app-side
 *    hiltInteropModule lazy singles (both stay Hilt @Singleton in
 *    :feature:player:video; lazy is load-bearing — eager resolution would
 *    pull media3 + font caches into startup);
 *  - SubtitleLanguageStore is Koin-native (datastoreCommonModule);
 *  - PlaybackRequestFactory is built here with the application context handed
 *    in by the app composition root — the ViewModel no longer touches
 *    Context itself.
 */
fun androidSubtitleTesterModule(context: Context): Module = module {
    single {
        PlaybackRequestFactory(
            context = context,
        )
    }
    viewModel {
        SubtitleTesterViewModel(
            engineFactory = get(),
            subtitleLanguageStore = get(),
            fontProvider = get(),
            playbackRequestFactory = get(),
        )
    }
}
