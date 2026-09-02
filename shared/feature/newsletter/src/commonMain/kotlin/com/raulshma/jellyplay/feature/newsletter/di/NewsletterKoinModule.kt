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
 *  - mediaRepository was the last Hilt-interop dep historically; since the
 *    wave-8 Hilt extinction Koin owns MediaRepositoryImpl natively
 *    (dataJvmModule), so this VM is fully live-resolvable on desktop too
 *    (the shell nav entry remains the only gate).
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
