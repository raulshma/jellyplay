package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.whatsnew.WhatsNewRepository
import com.raulshma.jellyplay.core.data.whatsnew.WhatsNewRepositoryImpl
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What's-New family module (the family-split pattern): the release-notes
 * feed repository. The GitHub releases API resolves from :shared:core:network's
 * `networkJvmModule` (same source as the update check); the cache +
 * seen-version keys live in
 * [com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore];
 * the repository rides the shared application scope for its one-shot cache
 * fold-in.
 */
val dataWhatsNewModule: Module = module {
    single {
        WhatsNewRepositoryImpl(
            gitHubReleasesApi = get(),
            experimentalStore = get(),
            externalScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<WhatsNewRepository> { get<WhatsNewRepositoryImpl>() }
}
