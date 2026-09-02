package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.network.DiscoveryMulticastGuard
import com.raulshma.jellyplay.core.network.NoopDiscoveryMulticastGuard
import com.raulshma.jellyplay.core.network.api.DesktopDeviceCodecCapabilities
import com.raulshma.jellyplay.core.network.api.DeviceCodecCapabilities
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the Koin-owned network stack (Phase C4). The base
 * client, its interceptor stack, and the derived streaming/download clients
 * are identical to Android (via [baseOkHttpClient]) except the cache lives
 * under `configDir/http-cache`. The Jellyfin SDK options mirror the Android
 * provider minus the android-only pieces (no `androidDevice`, no
 * packageManager version lookup) — jellyfin-core is a plain JVM lib.
 */

// Desktop has no packageManager; the JellyPlay desktop build has no separate
// version source yet, so the client version mirrors the Android fallback.
private const val DESKTOP_APP_VERSION = "1.0"
private const val DESKTOP_DEVICE_NAME = "JellyPlay Desktop"

fun desktopNetworkModule(configDir: Path): Module = module {
    single {
        baseOkHttpClient(
            cacheDir = (configDir / "http-cache").toFile(),
            okHttpConfigProvider = get(),
            bandwidthInterceptor = get(),
            serverAddressRouter = get(),
        )
    }
    single(qualifier = NetworkQualifiers.streamingHttpClient) {
        get<OkHttpClient>().toStreamingOkHttpClient()
    }
    single(qualifier = NetworkQualifiers.downloadHttpClient) {
        get<OkHttpClient>().toDownloadOkHttpClient()
    }
    single {
        val serverIdentityStore = get<ServerIdentityStore>()
        // Same DataStore-backed device id as Android: one stable identity per
        // install, shared by the REST/session API and the WebSocket channel.
        val deviceId = serverIdentityStore.identity.value.deviceId
            ?: runBlocking { serverIdentityStore.ensureDeviceId() }
        createJellyfin {
            clientInfo = ClientInfo(
                name = "JellyPlay",
                version = DESKTOP_APP_VERSION,
            )
            deviceInfo = DeviceInfo(
                id = deviceId,
                name = DESKTOP_DEVICE_NAME,
            )
            apiClientFactory = OkHttpFactory(get())
        }
    }
    single<DeviceCodecCapabilities> { DesktopDeviceCodecCapabilities() }
    single<DiscoveryMulticastGuard> { NoopDiscoveryMulticastGuard() }
}
