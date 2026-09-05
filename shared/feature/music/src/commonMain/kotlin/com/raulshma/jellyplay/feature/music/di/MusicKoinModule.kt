package com.raulshma.jellyplay.feature.music.di

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.feature.music.albumdetail.AlbumDetailViewModel
import com.raulshma.jellyplay.feature.music.albums.AlbumsViewModel
import com.raulshma.jellyplay.feature.music.artistdetail.ArtistDetailViewModel
import com.raulshma.jellyplay.feature.music.artists.ArtistsViewModel
import com.raulshma.jellyplay.feature.music.browse.MusicBrowseViewModel
import com.raulshma.jellyplay.feature.music.genres.GenreDetailViewModel
import com.raulshma.jellyplay.feature.music.genres.GenresViewModel
import com.raulshma.jellyplay.feature.music.moodplaylist.MoodPlaylistsViewModel
import com.raulshma.jellyplay.feature.music.musichome.MusicHomeViewModel
import com.raulshma.jellyplay.feature.music.playlists.PlaylistDetailViewModel
import com.raulshma.jellyplay.feature.music.playlists.PlaylistsViewModel
import com.raulshma.jellyplay.feature.music.smartplaylist.SmartPlaylistsViewModel
import com.raulshma.jellyplay.feature.music.tracks.TracksViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the music feature (docs/kmp-migration-plan.md
 * §Phase V3, third conveyor item after search and library). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Ctor deps split three
 * ways:
 *  - MediaRepository / DownloadRepository / DownloadIntake / AudioQueueFacade
 *    are still Hilt-owned in the legacy data shim and reach Koin through the
 *    app composition root's Hilt interop module (dies at Phase X);
 *  - ImageUrlProvider / MoodPlaylistRepository / SmartPlaylistRepository
 *    (shared data), HomeDiscoveryStore (shared datastore) and
 *    OfflineModeManager resolve from the C4 shared-module graph;
 *  - MusicMessageBus is app-provided on Android (bridge to the legacy
 *    UserMessageBus) and a buffering relay on desktop
 *    (desktopMusicMessageBusModule — the desktop shell's snackbar host
 *    collects it since wave 21B).
 *
 * GenreDetailViewModel's SavedStateHandle is pulled from the definition
 * parameters: on Android, Koin synthesizes it from the CreationExtras of the
 * LocalViewModelStoreOwner current at the call site (nav3 1.1.5 installs no
 * per-entry ViewModelStoreOwner, so that owner is the Activity — the exact
 * same extras source the Hilt factory consumed at HEAD).
 */
val musicModule: Module = module {
    viewModel {
        MusicHomeViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
            downloadRepository = get(),
            homeDiscoveryStore = get(),
            offlineModeManager = get(),
            userMessageBus = get(),
        )
    }
    viewModel {
        AlbumsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        ArtistsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        MusicBrowseViewModel(
            mediaRepository = get(),
            playlistRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        GenresViewModel(
            mediaRepository = get(),
        )
    }
    viewModel { params ->
        GenreDetailViewModel(
            savedStateHandle = params.get<SavedStateHandle>(),
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
        )
    }
    viewModel {
        MoodPlaylistsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
            moodPlaylistRepository = get(),
        )
    }
    viewModel {
        SmartPlaylistsViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
            smartPlaylistRepository = get(),
        )
    }
    viewModel {
        PlaylistsViewModel(
            playlistRepository = get(),
        )
    }
    viewModel {
        PlaylistDetailViewModel(
            mediaRepository = get(),
            playlistRepository = get(),
            audioQueueFacade = get(),
        )
    }
    viewModel {
        TracksViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
        )
    }
    viewModel {
        AlbumDetailViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
            downloadRepository = get(),
            downloadIntake = get(),
        )
    }
    viewModel {
        ArtistDetailViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
            audioQueueFacade = get(),
        )
    }
}
