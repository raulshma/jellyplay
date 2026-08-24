package com.raulshma.jellyplay.feature.newsletter.di

import com.raulshma.jellyplay.feature.newsletter.NewsletterViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the Newsletter feature (docs/kmp-migration-plan.md
 * §Phase V3 conveyor item after search, library, music, livetv, downloads,
 * syncplay, settings, admin, editor, requests and calendar). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Dep posture:
 *  - imageUrlProvider, notificationStore and authRepository were already
 *    Koin-native before this feature moved (AndroidDataModule +
 *    desktopDataModule / datastoreCommonModule / DataKoinModule), resolving
 *    on BOTH platforms;
 *  - mediaRepository resolves through the app-side hiltInteropModule lazy
 *    single (interface already lives in shared :core:data commonMain; impl
 *    stays Hilt-bound) and has NO desktop definition yet — the same
 *    documented-latent state as the search/library/music/livetv/syncplay
 *    registrations. Koin defers resolution, so the desktop startKoin stays
 *    safe until the data-layer defs land; the shell has no newsletter nav
 *    entry yet either.
 */
val newsletterModule: Module = module {
    viewModel {
        NewsletterViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            notificationStore = get(),
            authRepository = get(),
        )
    }
}
