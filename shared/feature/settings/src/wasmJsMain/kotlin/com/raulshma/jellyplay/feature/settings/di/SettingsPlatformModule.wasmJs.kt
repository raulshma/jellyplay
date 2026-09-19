package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.feature.settings.AboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.AppLocaleSetter
import com.raulshma.jellyplay.feature.settings.AppMetaProvider
import com.raulshma.jellyplay.feature.settings.AudioCacheClearer
import com.raulshma.jellyplay.feature.settings.AutoDownloadSync
import com.raulshma.jellyplay.feature.settings.LogCollector
import com.raulshma.jellyplay.feature.settings.NotificationSync
import com.raulshma.jellyplay.feature.settings.ServerAdminActions
import com.raulshma.jellyplay.feature.settings.SettingsBackupIo
import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageMount
import com.raulshma.jellyplay.feature.settings.StorageMountKind
import com.raulshma.jellyplay.feature.settings.StorageMountsProvider
import com.raulshma.jellyplay.feature.settings.StorageSizeEstimate
import com.raulshma.jellyplay.feature.settings.WatchNextRefresher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The wasmJs actual of the settings platform fragment (see the common
 * [platformSettingsModule] KDoc). Honest no-ops across the board: the browser
 * shell has no settings storage layout, no scheduler backends and no admin
 * surface — the behavior seams degrade (the visibility halves hide their rows
 * via `SettingsCapabilities` / the null picker), so no web user sees a control
 * whose poke lands nowhere. Web wiring itself stays with the orchestrator's
 * shared-wiring pass (shortcuts precedent) — these bindings only exist so the
 * graph is resolvable if settingsModule is ever included on web.
 */

/** No browser filesystem: backup export/import stay unreachable behind the null picker. */
private object WasmSettingsBackupIo : SettingsBackupIo {
    override suspend fun writeExportPayload(uri: String, payload: String): Boolean = false
    override suspend fun readImportPayload(uri: String): String? = null
    override suspend fun estimateCacheSizeBytes(): Long = 0L
}

/** No admin surface on web — see [ServerAdminActions]' KDoc for the gate wiring. */
private object WasmServerAdminActions : ServerAdminActions {
    override val isSupported: Boolean = false
    override suspend fun getSystemInfo(): Result<SystemInfo> = Result.failure(IllegalStateException("Not available on the web"))
    override suspend fun getSessions(): Result<List<SessionInfo>> = Result.success(emptyList())
    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String): Result<Unit> =
        Result.failure(IllegalStateException("Not available on the web"))
}

internal actual fun platformSettingsModule(): Module = module {
    single<ServerAdminActions> { WasmServerAdminActions }
    single<SettingsBackupIo> { WasmSettingsBackupIo }
    single<AppLocaleSetter> { AppLocaleSetter { /* no per-app locale override in a browser tab */ } }
    single<StorageAreas> {
        object : StorageAreas {
            override suspend fun sizeEstimateBytes(downloadStorageLocation: String) =
                StorageSizeEstimate(cacheBytes = 0L, externalCacheBytes = 0L, downloadsBytes = 0L, imageCacheBytes = 0L)
            override suspend fun clearCache() { /* no storage layout on web */ }
            override suspend fun clearImageCache() { /* no storage layout on web */ }
        }
    }
    single<StorageMountsProvider> {
        // One "browser" internal volume with unknown free space — the storage
        // screen renders the picker, the buckets stay empty.
        StorageMountsProvider {
            listOf(StorageMount(prefValue = "INTERNAL", kind = StorageMountKind.INTERNAL, availableBytes = 0L, rootPath = "browser"))
        }
    }
    single<AppMetaProvider> {
        object : AppMetaProvider {
            override val versionName: String? = null
            override val isDebugBuild: Boolean = false
            override val minSdk: Int = 0
            override val targetSdk: Int = 0
        }
    }
    single<LogCollector> { LogCollector { _, _, _ -> null } }
    single<AboutLibrariesJsonSource> { AboutLibrariesJsonSource { null } }
    single<AutoDownloadSync> { AutoDownloadSync { /* no download scheduler on web */ } }
    single<WatchNextRefresher> { WatchNextRefresher { /* no Watch Next row on web */ } }
    single<AudioCacheClearer> { AudioCacheClearer { /* no audio cache on web */ } }
    single<NotificationSync> { NotificationSync { /* no notification worker on web */ } }
}
