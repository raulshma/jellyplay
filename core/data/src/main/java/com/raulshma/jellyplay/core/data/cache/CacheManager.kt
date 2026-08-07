package com.raulshma.jellyplay.core.data.cache

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Name of the audio byte cache subdirectory under [Context.cacheDir]. */
internal const val AUDIO_CACHE_DIR_NAME = "audio_cache"

/**
 * Manages the application's HTTP/image caches and honours the
 * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.autoDeleteCache]
 * preference: when enabled, caches are cleared when the app goes to the
 * background (process `ON_STOP`).
 *
 * The clear operation only deletes the contents of [Context.cacheDir] and
 * [Context.externalCacheDir] — both of which are private to the app and
 * safe to evict at any time (the HTTP stack and image loaders will simply
 * re-fetch on demand). Downloaded media lives under a separate directory
 * and is never touched here.
 *
 * The [AUDIO_CACHE_DIR_NAME] subdirectory is **excluded** from the sweep
 * because it holds in-flight data referenced by ExoPlayer; sweeping it
 * mid-playback would corrupt reads.
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : DefaultLifecycleObserver {

    /**
     * Subdirectories of [Context.cacheDir] that must survive the auto-delete
     * sweep. The audio byte cache holds in-flight data referenced by ExoPlayer.
     */
    private val protectedSubdirs = setOf(AUDIO_CACHE_DIR_NAME)

    /** Test hook: overrides the cache directory resolved from [Context]. */
    internal var cacheDirOverride: File? = null
    private fun cacheDir(): File = cacheDirOverride ?: context.cacheDir

    init {
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        val prefs = networkOfflineStore.networkOffline.value
        if (prefs.autoDeleteCache) {
            // Fire-and-forget on the application scope so the lifecycle
            // callback is not blocked while the cache directory walk runs.
            appScope.launch(Dispatchers.IO) { clearCacheInternal() }
        }
    }

    /**
     * Synchronously clears the app's cache directories. Safe to call from a
     * background thread. Returns the number of bytes reclaimed.
     */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        clearCacheInternal()
    }

    /**
     * Returns the combined size (in bytes) of the internal + external cache
     * directories. Computed on a background thread. Excludes [protectedSubdirs].
     */
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        getDirSizeExcludingProtected(cacheDir()) +
            (context.externalCacheDir?.let { getDirSizeExcludingProtected(it) } ?: 0L)
    }

    private fun clearCacheInternal(): Long {
        var reclaimed = 0L
        reclaimed += deleteContents(cacheDir())
        context.externalCacheDir?.let { reclaimed += deleteContents(it) }
        return reclaimed
    }

    private fun deleteContents(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var total = 0L
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && child.name in protectedSubdirs) {
                return@forEach // preserve audio_cache
            }
            total += if (child.isDirectory) {
                val sub = getDirSize(child)
                child.deleteRecursively()
                sub
            } else {
                val len = child.length()
                child.delete()
                len
            }
        }
        return total
    }

    private fun getDirSizeExcludingProtected(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var size = 0L
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && child.name in protectedSubdirs) return@forEach
            if (child.isDirectory) size += getDirSize(child)
            else if (child.isFile) size += child.length()
        }
        return size
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }
}
