package com.raulshma.jellyplay.core.network.di

import android.content.Context
import android.net.ConnectivityManager
import com.raulshma.jellyplay.core.network.DiscoveryMulticastGuard
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import com.raulshma.jellyplay.core.network.ServerDiscoveryService
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.DeviceCodecCapabilities
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin

/**
 * Phase C4: Koin (via [networkJvmModule] / [androidNetworkModule] in
 * :shared:core:network) is the construction owner for every network type.
 * This module keeps Hilt as the injector for legacy consumers only — each
 * @Provides is a thin bridge that fetches the Koin singleton, so both
 * frameworks see exactly one instance per type. The former @Binds pairs
 * became @Provides bridges for the same reason: a surviving @Binds would let
 * Hilt construct a SECOND instance of the impl, bypassing Koin.
 *
 * Types that were previously constructor-@Inject Hilt singletons but have
 * legacy Hilt consumers (the engine, the shared WebSocket client, the
 * realtime channels, discovery, bandwidth, LrcLib) also bridge here — their
 * classes live in the shared module now, and Koin's instances must be the
 * ones every consumer sees.
 *
 * The `@ApplicationScope`-qualified CoroutineScope and OkHttpConfigProvider
 * bindings remain Hilt-owned in their legacy shims and are NOT bridged.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    companion object {
        @Provides
        @Singleton
        fun provideJellyfinApiClient(): JellyfinApiClient = koin().get()

        @Provides
        @Singleton
        fun provideAuthApiClient(): AuthApiClient = koin().get()

        @Provides
        @Singleton
        fun provideLibraryApiClient(): LibraryApiClient = koin().get()

        @Provides
        @Singleton
        fun providePlaybackApiClient(): PlaybackApiClient = koin().get()

        @Provides
        @Singleton
        fun provideSyncPlayApiClient(): SyncPlayApiClient = koin().get()

        @Provides
        @Singleton
        fun provideLiveTvApiClient(): LiveTvApiClient = koin().get()

        @Provides
        @Singleton
        fun provideAdminApiClient(): AdminApiClient = koin().get()

        @Provides
        @Singleton
        fun provideUserApiClient(): UserApiClient = koin().get()

        @Provides
        @Singleton
        fun provideMetadataApiClient(): MetadataApiClient = koin().get()

        @Provides
        @Singleton
        fun provideMediaInfoApiClient(): MediaInfoApiClient = koin().get()

        @Provides
        @Singleton
        fun providePluginApiClient(): PluginApiClient = koin().get()

        @Provides
        @Singleton
        fun provideSeerrApiClient(): SeerrApiClient = koin().get()

        @Provides
        @Singleton
        fun provideTmdbApiClient(): TmdbApiClient = koin().get()

        @Provides
        @Singleton
        fun provideRadarrApiClient(): RadarrApiClient = koin().get()

        @Provides
        @Singleton
        fun provideSonarrApiClient(): SonarrApiClient = koin().get()

        @Provides
        @Singleton
        fun provideDeviceCodecCapabilities(): DeviceCodecCapabilities = koin().get()

        @Provides
        @Singleton
        fun provideDiscoveryMulticastGuard(): DiscoveryMulticastGuard = koin().get()

        @Provides
        @Singleton
        fun provideJellyfinApiEngine(): JellyfinApiEngine = koin().get()

        @Provides
        @Singleton
        fun provideJellyfinWebSocketClient(): JellyfinWebSocketClient = koin().get()

        @Provides
        @Singleton
        fun provideActivityLogRealtimeChannel(): ActivityLogRealtimeChannel = koin().get()

        @Provides
        @Singleton
        fun provideScheduledTasksRealtimeChannel(): ScheduledTasksRealtimeChannel = koin().get()

        @Provides
        @Singleton
        fun provideUserDataRealtimeChannel(): UserDataRealtimeChannel = koin().get()

        @Provides
        @Singleton
        fun provideServerDiscoveryService(): ServerDiscoveryService = koin().get()

        @Provides
        @Singleton
        fun provideBandwidthInterceptor(): BandwidthInterceptor = koin().get()

        @Provides
        @Singleton
        fun provideLrcLibApi(): LrcLibApi = koin().get()

        /**
         * The GitHub Releases impl takes the latest-release URL as a
         * constructor param (overridable by unit tests via MockWebServer);
         * the Koin definition in :shared:core:network supplies the production
         * constant, so this is a plain bridge.
         */
        @Provides
        @Singleton
        fun provideGitHubReleasesApi(): GitHubReleasesApi = koin().get()

        /** Shared lenient `Json`; constructed by [networkJvmModule]. */
        @Provides
        @Singleton
        fun provideJson(): kotlinx.serialization.json.Json = koin().get()

        /** Jellyfin SDK instance with the android device-id/options; constructed by [androidNetworkModule]. */
        @Provides
        @Singleton
        fun provideJellyfin(): Jellyfin = koin().get()

        /** Base `OkHttpClient` (cache + full interceptor stack); constructed by [androidNetworkModule]. */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = koin().get()

        @Provides
        @Singleton
        @Named("streaming")
        fun provideStreamingOkHttpClient(): OkHttpClient =
            koin().get(NetworkQualifiers.streamingHttpClient)

        @Provides
        @Singleton
        @Named("download")
        fun provideDownloadOkHttpClient(): OkHttpClient =
            koin().get(NetworkQualifiers.downloadHttpClient)

        // Android-only helper consumed by Hilt injectors elsewhere (e.g.
        // AdaptiveBitrateManager); stays Hilt-owned, no Koin counterpart.
        @Provides
        @Singleton
        fun provideConnectivityManager(
            @ApplicationContext context: Context,
        ): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}
