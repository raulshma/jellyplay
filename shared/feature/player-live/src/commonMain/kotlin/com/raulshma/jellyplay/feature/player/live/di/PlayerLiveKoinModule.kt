package com.raulshma.jellyplay.feature.player.live.di

import com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the live player feature (docs/kmp-migration-
 * plan.md §Phase V3, player-live conveyor). The HiltViewModel/@Inject
 * annotations were stripped at the move — Koin is the single constructor
 * owner (one framework per type). Ctor deps split three ways:
 *  - LiveTvRepository/PlaybackRepository/ImageUrlProvider are Koin-native
 *    (dataJvmModule, both platforms — the MediaRepository cluster flip
 *    already landed); the three stores are Koin-native in the shared
 *    datastore graph;
 *  - the three platform seams (LiveEngineFactory, LivePlayerAudio,
 *    TranscodeReasonsRenderer) are Android-only definitions in
 *    `androidPlayerLiveModule` (subtitle-tester's context-param pattern) —
 *    the desktop registration of this module is documented-latent: the VM
 *    is only constructed by the Android screen, which lives in androidMain;
 *  - LastChannelStore is a plain single over AppRuntimeStateStore.
 *
 * The record/cancel feedback no longer goes through the Android-only
 * UserMessageBus: the VM emits LivePlayerMessage values on a messages Flow
 * that LivePlayerScreen renders via the app bus (livetv conveyor's
 * LiveTvUserMessage seam shape).
 *
 * Wave 19C (live PiP): the VM's `pip` seam is a fourth platform slot —
 * Android binds it in `androidPlayerLiveModule` (adapter over the legacy
 * core:data singleton the host PlayerActivity reads). Like the audio seam it
 * has no jvm definition, so the desktop registration here stays
 * documented-latent for one more unresolvable dep — the live screen never
 * composes there (Route.LiveTvChannelPlayer stays guarded in DesktopAppRoot).
 */
val playerLiveModule: Module = module {
    viewModel {
        LiveTvPlayerViewModel(
            liveTvRepository = get(),
            playbackRepository = get(),
            appRuntimeStateStore = get(),
            playbackStore = get(),
            aggregateStore = get(),
            lastChannelStore = get(),
            engineFactory = get(),
            imageUrlProvider = get(),
            audio = get(),
            transcodeReasonsRenderer = get(),
            pip = get(),
        )
    }
    single {
        LastChannelStore(
            prefs = get(),
        )
    }
}
