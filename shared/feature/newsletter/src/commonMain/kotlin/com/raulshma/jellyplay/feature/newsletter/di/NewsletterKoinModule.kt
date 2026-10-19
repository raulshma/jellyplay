package com.raulshma.jellyplay.feature.newsletter.di

import com.raulshma.jellyplay.feature.newsletter.NewsletterViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the Newsletter feature. Koin is the single
 * constructor owner (one framework per type). Dep posture:
 *  - imageUrlProvider, notificationStore and authRepository resolve from the
 *    shared Koin graph (AndroidDataModule + desktopDataModule /
 *    datastoreCommonModule / DataKoinModule) on BOTH platforms;
 *  - newsletterApiClient resolves through dataJvmModule, which binds the
 *    NewsletterApiClient view of MediaRepositoryImpl, so this VM is fully
 *    live-resolvable on desktop too (the shell nav entry remains the only
 *    gate).
 */
val newsletterModule: Module = module {
    viewModel {
        NewsletterViewModel(
            newsletterApiClient = get(),
            imageUrlProvider = get(),
            notificationStore = get(),
            authRepository = get(),
        )
    }
}
