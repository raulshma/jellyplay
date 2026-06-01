package com.raulshma.jellyplay.core.network.di

import android.content.Context
import android.net.ConnectivityManager
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.JellyfinApiClientImpl
import com.raulshma.jellyplay.core.network.seerr.ResilientSeerrApiClient
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
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
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

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
    abstract fun bindSeerrApiClient(
        impl: ResilientSeerrApiClient,
    ): SeerrApiClient

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
        ): Jellyfin = createJellyfin {
            this.context = context
            clientInfo = ClientInfo(
                name = "JellyPlay",
                version = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            )
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(
            @ApplicationContext context: Context,
            userPreferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
        ): OkHttpClient {
            val cacheDir = File(context.cacheDir, "http_cache")
            cacheDir.mkdirs()
            val cacheMb = kotlinx.coroutines.runBlocking {
                userPreferencesStore.preferences.first().maxCacheSizeMb
            }
            val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 50L * 1024 * 1024
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cache(Cache(cacheDir, cacheSize))
                .connectionPool(ConnectionPool(5, 10, TimeUnit.MINUTES))
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .retryOnConnectionFailure(true)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val path = chain.request().url.encodedPath
                    val cacheMaxAge = when {
                        path.contains("/Images/") -> 604800
                        path.contains("/Genres") -> 300
                        path.contains("/System/Info") -> 600
                        path.contains("/Library/MediaFolders") -> 300
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
    }
}
