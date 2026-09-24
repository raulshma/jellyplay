package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.playback.DefaultPlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.config.ClientCertificateManager
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackTls
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The playback family of the dataJvmModule split — the playback half of the
 * former mixed download-actions section: the PlaybackRepository facade over
 * the narrow API family seams and the PlaybackIdentity session-read module
 * (see [dataJvmModule] for the construction-owner rules). Binding bodies
 * moved verbatim from the pre-split single-module layout.
 */
internal val dataPlaybackFamilyModule: Module = module {
    // ── playback family (PlaybackRepository + PlaybackIdentity) ──────────
    single {
        PlaybackRepositoryImpl(
            // Narrow family seams, not the JellyfinApiClient union: the impl
            // only touches playback/library/auth/metadata, and the family
            // singles below compose the same impls JellyfinApiClientImpl does.
            playbackApiClient = get<PlaybackApiClient>(),
            libraryApiClient = get<LibraryApiClient>(),
            authApiClient = get<AuthApiClient>(),
            metadataApiClient = get<MetadataApiClient>(),
            outbox = get(),
            offlineModeManager = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
            mediaCacheInvalidation = get(),
            mediaRepository = lazy { get<MediaRepository>() },
        )
    }
    single<PlaybackRepository> { get<PlaybackRepositoryImpl>() }

    // The playback session-identity reads (token + base URL) — the narrow
    // module the former PlaybackRepository.getServerUrl/getAccessToken members
    // were retired into. Bound to the same AuthApiClient the impl reads. The
    // client-TLS reader maps the app-level certificate manager's
    // normalized file paths into the player-contract view (the mpv engines'
    // tls-* options); no enabled certificate yields null (no TLS on the
    // request) while the OkHttp side fail-closes on its own.
    single<PlaybackIdentity> {
        DefaultPlaybackIdentity(
            apiClient = get(),
            clientTlsReader = {
                get<ClientCertificateManager>().playbackTlsPaths()?.let { paths ->
                    PlaybackTls(
                        clientCertificatePath = paths.certificatePath,
                        clientKeyPath = paths.keyPath,
                        caPath = paths.caPath,
                    )
                }
            },
        )
    }
}
