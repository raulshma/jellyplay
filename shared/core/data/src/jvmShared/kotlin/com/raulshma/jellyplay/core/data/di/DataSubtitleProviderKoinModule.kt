package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepositoryImpl
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The subtitle-provider family of the dataJvmModule split — the
 * subtitle half of the former mixed download-actions section (see
 * [dataJvmModule] for the construction-owner rules). Binding bodies moved
 * verbatim from the pre-split single-module layout.
 */
internal val dataSubtitleProviderModule: Module = module {
    // ── subtitle-provider family ─────────────────────────────────────────
    single {
        SubtitleProviderRepositoryImpl(
            preferencesStore = get(),
            externalProviders = get<Map<SubtitleProviderKind, SubtitleProvider>>(),
            playbackRepository = get(),
        )
    }
    single<SubtitleProviderRepository> { get<SubtitleProviderRepositoryImpl>() }
}
