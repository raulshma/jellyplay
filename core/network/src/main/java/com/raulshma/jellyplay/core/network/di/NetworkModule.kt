package com.raulshma.jellyplay.core.network.di

import android.content.Context
import android.net.ConnectivityManager
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.JellyfinApiClientImpl
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.AdminApiClientImpl
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.AuthApiClientImpl
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClientImpl
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClientImpl
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClientImpl
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClientImpl
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClientImpl
import com.raulshma.jellyplay.core.network.seerr.ResilientSeerrApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClientImpl
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Protocol
import javax.inject.Named
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindJellyfinApiClient(
        impl: JellyfinApiClientImpl,
    ): JellyfinApiClient

    @Binds
    @Singleton
    abstract fun bindAuthApiClient(impl: AuthApiClientImpl): AuthApiClient

    @Binds
    @Singleton
    abstract fun bindLibraryApiClient(impl: LibraryApiClientImpl): LibraryApiClient

    @Binds
    @Singleton
    abstract fun bindPlaybackApiClient(impl: PlaybackApiClientImpl): PlaybackApiClient

    @Binds
    @Singleton
    abstract fun bindSyncPlayApiClient(impl: SyncPlayApiClientImpl): SyncPlayApiClient

    @Binds
    @Singleton
    abstract fun bindLiveTvApiClient(impl: LiveTvApiClientImpl): LiveTvApiClient

    @Binds
    @Singleton
    abstract fun bindAdminApiClient(impl: AdminApiClientImpl): AdminApiClient

    @Binds
    @Singleton
    abstract fun bindMetadataApiClient(impl: MetadataApiClientImpl): MetadataApiClient

    @Binds
    @Singleton
    abstract fun bindMediaInfoApiClient(impl: MediaInfoApiClientImpl): MediaInfoApiClient

    @Binds
    @Singleton
    abstract fun bindSeerrApiClient(
        impl: ResilientSeerrApiClient,
    ): SeerrApiClient

    @Binds
    @Singleton
    abstract fun bindTmdbApiClient(
        impl: TmdbApiClientImpl,
    ): TmdbApiClient

    companion object {
        @Provides
        @Singleton
        fun provideConnectivityManager(
            @ApplicationContext context: Context,
        ): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        @Provides
        @Singleton
        fun provideJellyfin(
            @ApplicationContext context: Context,
            okHttpClient: OkHttpClient,
        ): Jellyfin = createJellyfin {
            this.context = context
            clientInfo = ClientInfo(
                name = "JellyPlay",
                version = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            )
            apiClientFactory = OkHttpFactory(okHttpClient)
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(
            @ApplicationContext context: Context,
            userPreferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
            bandwidthInterceptor: BandwidthInterceptor,
        ): OkHttpClient {
            val cacheDir = File(context.cacheDir, "http_cache")
            cacheDir.mkdirs()
            val cacheMb = userPreferencesStore.preferences.value.maxCacheSizeMb
            val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 50L * 1024 * 1024
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cache(Cache(cacheDir, cacheSize))
                .connectionPool(ConnectionPool(16, 15, TimeUnit.MINUTES))
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .retryOnConnectionFailure(true)
                .addNetworkInterceptor(bandwidthInterceptor)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val path = chain.request().url.encodedPath
                    val query = chain.request().url.queryParameterNames
                    val cacheMaxAge = when {
                        path.startsWith("/Items/") && path.contains("/Images/") -> 604800
                        path.startsWith("/Genres/") -> 300
                        path == "/System/Info/Public" -> 600
                        path.startsWith("/Library/MediaFolders") -> 300
                        path == "/System/Info" -> 120
                        path.startsWith("/Sessions") -> 10
                        path.startsWith("/ScheduledTasks") -> 30
                        path == "/Shows/NextUp" || query.contains("resume") && path == "/Items" -> 120
                        path.startsWith("/Shows/") && path.contains("/Episodes") -> 300
                        path.startsWith("/Shows/") && path.contains("/Seasons") -> 300
                        path.contains("/Similar") -> 300
                        path == "/Items" && !query.contains("Resume") -> 60
                        else -> null
                    }
                    if (response.isSuccessful && cacheMaxAge != null) {
                        response.newBuilder()
                            .header("Cache-Control", "max-age=$cacheMaxAge")
                            .build()
                    } else {
                        response
                    }
                }
                .build()
        }

        @Provides
        @Singleton
        @Named("streaming")
        fun provideStreamingOkHttpClient(
            okHttpClient: OkHttpClient,
        ): OkHttpClient = okHttpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
