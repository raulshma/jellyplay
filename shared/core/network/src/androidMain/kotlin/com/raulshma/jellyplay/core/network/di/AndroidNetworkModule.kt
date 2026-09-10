package com.raulshma.jellyplay.core.network.di

import android.content.Context
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.network.DiscoveryMulticastGuard
import com.raulshma.jellyplay.core.network.api.AndroidDeviceCodecCapabilities
import com.raulshma.jellyplay.core.network.api.DeviceCodecCapabilities
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick of the Koin-owned network stack (Phase C4).
 * Constructions replicate the legacy Hilt `NetworkModule` providers
 * byte-for-byte — cache under `context.cacheDir`, the full interceptor stack
 * via [baseOkHttpClient], the Jellyfin SDK options (androidDevice, device-id
 * DataStore, app version from packageManager) — so only the construction
 * owner changes, not the wiring.
 *
 * [OkHttpConfigProvider] is resolved from Koin (the app composition root
 * provides the impl definition; its class still lives in `core:data` until
 * that module migrates).
 */
fun androidNetworkModule(context: Context): Module = module {
    single {
        baseOkHttpClient(
            cacheDir = File(context.cacheDir, "http_cache"),
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
        // Use the app's persistent DataStore UUID as the SDK device id so the
        // REST/session API, the WebSocket connection (which passes the same
        // id in MainViewModel), and the server all agree on one identity.
        // Without this the SDK defaults to Settings.Secure.ANDROID_ID, which
        // never equals ensureDeviceId() and made the app's own session show up
        // in the Play On / Cast device list. Resolution ladder (memory value
        // → bounded blocking → random-UUID last resort) lives in
        // [resolveDeviceId], shared with the desktop pick.
        val androidDefault = androidDevice(context)
        val deviceId = resolveDeviceId(
            serverIdentityStore,
            get<CoroutineScope>(DatastoreQualifiers.applicationScope),
        )
        createJellyfin {
            this.context = context
            clientInfo = ClientInfo(
                name = "JellyPlay",
                version = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "1.0",
            )
            deviceInfo = DeviceInfo(
                id = deviceId,
                name = androidDefault.name,
            )
            apiClientFactory = OkHttpFactory(get())
        }
    }
    single<DeviceCodecCapabilities> { AndroidDeviceCodecCapabilities() }
    single<DiscoveryMulticastGuard> { AndroidMulticastLockGuard(context.applicationContext) }
}
