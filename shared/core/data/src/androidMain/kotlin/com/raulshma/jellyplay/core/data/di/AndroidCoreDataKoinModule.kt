package com.raulshma.jellyplay.core.data.di

import android.content.Context
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * core-side Hilt extinction: Koin owns every remaining legacy
 * core:data singleton. The classes in the sibling family modules are the
 * Android-only remainder (media3 audio playback stack, cast strategies,
 * WorkManager schedulers and reconnect listeners, receivers' helper graph)
 * — their ctor shapes are byte-identical to the old Hilt graph, with the two
 * qualifier edges mapped onto the shared modules' Koin qualifiers:
 *  - the old `@ApplicationScope CoroutineScope` →
 *    [com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope]
 *    (datastoreCommonModule single);
 *  - the old `@Named("streaming")` / `@Named("download")` OkHttpClient →
 *    [com.raulshma.jellyplay.core.network.di.NetworkQualifiers.streamingHttpClient] /
 *    [com.raulshma.jellyplay.core.network.di.NetworkQualifiers.downloadHttpClient]
 *    (androidNetworkModule singles).
 *
 * Interface bindings mirror every former DataModule/SubtitleModule `@Binds`
 * (DownloadIntake, AudioQueueManager, TvWatchNextScheduler,
 * UserDataSyncScheduler, PlaybackSyncScheduler, StreamingSubtitleStore) plus
 * the AudioEffectsManager/AudioQueueFacade aliases the app interop layer used
 * to supply.
 *
 * Family split: the former single module is now sibling family modules in
 * this package (the file's old comment-group boundaries), and this aggregate
 * keeps the `androidCoreDataModule` name alive via `includes` so consumers
 * are unchanged. Every binding body moved verbatim.
 *
 * A transitional Hilt bridge module briefly fed the still-Hilt :app
 * injectors from these singles; it died with the app Hilt extinction
 * — :app now resolves these directly.
 */
fun androidCoreDataModule(context: Context): Module = module {
    includes(
        androidPlaybackFocusModule(context),
        androidPlaybackStackModule(context),
        androidRemoteCastModule(context),
        androidIntakeStorageModule(context),
        androidWorkSchedulersModule(context),
    )
}
