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
import org.jellyfin.sdk.Jellyfin
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
        ): OkHttpClient {
            val cacheDir = File(context.cacheDir, "http_cache")
            cacheDir.mkdirs()
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cache(Cache(cacheDir, 50L * 1024 * 1024))
                .connectionPool(ConnectionPool(5, 10, TimeUnit.MINUTES))
                .build()
        }
    }
}
