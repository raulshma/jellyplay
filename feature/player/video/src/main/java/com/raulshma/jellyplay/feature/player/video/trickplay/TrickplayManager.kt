package com.raulshma.jellyplay.feature.player.video.trickplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TrickplayManager(
    private val playbackRepository: PlaybackRepository,
) {

    private val thumbnailCache = object : LruCache<Int, Bitmap>((MAX_THUMBNAIL_CACHE_BYTES / 1024).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount / 1024
        override fun entryRemoved(evictedBySize: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            // Recycle the native bitmap memory when it is evicted from the cache.
            // Guard against double-recycle: if a caller still holds a reference the
            // system will silently ignore further operations on the recycled bitmap.
            if (!oldValue.isRecycled) oldValue.recycle()
        }
    }
    private val spriteSheetCache = object : LruCache<Int, Bitmap>((MAX_SPRITE_SHEET_CACHE_BYTES / 1024).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount / 1024
        override fun entryRemoved(evictedBySize: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            // Only recycle sprite sheets when the cache evicts them automatically;
            // explicit clear() handles the remaining entries via evictAll() which
            // also triggers this callback, so all paths are covered.
            if (!oldValue.isRecycled) oldValue.recycle()
        }
    }
    private val sheetMutexes = ConcurrentHashMap<Int, Mutex>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var info: TrickplayInfo? = null
    private var itemId: String? = null
    private var preloadJob: kotlinx.coroutines.Job? = null
    private var localCacheDir: File? = null
    private var persistDir: File? = null

    fun initialize(itemId: String, trickplayInfo: TrickplayInfo) {
        clear()
        this.itemId = itemId
        this.info = trickplayInfo
        this.localCacheDir = null
        this.persistDir = null
    }

    fun initializeWithCache(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {
        clear()
        this.itemId = itemId
        this.info = trickplayInfo
        this.localCacheDir = null
        this.persistDir = cacheDir.apply { mkdirs() }
    }

    fun initializeLocal(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {
        clear()
        this.itemId = itemId
        this.info = trickplayInfo
        this.localCacheDir = cacheDir
    }

    suspend fun getThumbnail(positionMs: Long): Bitmap? {
        val currentInfo = info ?: return null
        val id = itemId ?: return null

        val thumbnailIndex = (positionMs / currentInfo.interval).toInt()
            .coerceIn(0, currentInfo.thumbnailCount - 1)

        thumbnailCache.get(thumbnailIndex)?.let { return it }

        return withContext(Dispatchers.Default) {
            val thumbnailsPerSheet = currentInfo.tileWidth * currentInfo.tileHeight
            val sheetIndex = thumbnailIndex / thumbnailsPerSheet
            val tileIndex = thumbnailIndex % thumbnailsPerSheet
            val tileCol = tileIndex % currentInfo.tileWidth
            val tileRow = tileIndex / currentInfo.tileWidth

            val sheet = spriteSheetCache.get(sheetIndex)
                ?: loadSpriteSheet(id, sheetIndex, currentInfo)
                ?: return@withContext null

            val offsetX = tileCol * currentInfo.width
            val offsetY = tileRow * currentInfo.height

            try {
                val thumbnail = Bitmap.createBitmap(
                    sheet, offsetX, offsetY, currentInfo.width, currentInfo.height,
                )
                thumbnailCache.put(thumbnailIndex, thumbnail)
                preloadNeighbors(id, thumbnailIndex, currentInfo)
                thumbnail
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun loadSpriteSheet(
        id: String,
        sheetIndex: Int,
        trickplayInfo: TrickplayInfo,
    ): Bitmap? {
        val mutex = sheetMutexes.getOrPut(sheetIndex) { Mutex() }
        return mutex.withLock {
            spriteSheetCache.get(sheetIndex)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val localDir = localCacheDir
                val localFile = if (localDir != null) File(localDir, "trickplay_${sheetIndex}.jpg") else null
                val persistDirectory = persistDir
                val data = if (localFile != null && localFile.exists()) {
                    localFile.readBytes()
                } else {
                    val fetched = playbackRepository.getTrickplayTileImage(id, trickplayInfo.width, sheetIndex)
                        ?: return@withContext null
                    if (persistDirectory != null) {
                        scope.launch {
                            try {
                                persistDirectory.mkdirs()
                                File(persistDirectory, "trickplay_${sheetIndex}.jpg").writeBytes(fetched)
                            } catch (_: Exception) { }
                        }
                    }
                    fetched
                }

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = false
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
            }.also { bitmap ->
                if (bitmap != null) {
                    spriteSheetCache.put(sheetIndex, bitmap)
                }
            }
        }
    }

    private fun preloadNeighbors(
        id: String,
        currentIndex: Int,
        trickplayInfo: TrickplayInfo,
    ) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val preloadRange = PRELOAD_NEIGHBOR_COUNT
            val minIndex = (currentIndex - preloadRange).coerceAtLeast(0)
            val maxIndex = (currentIndex + preloadRange).coerceAtMost(trickplayInfo.thumbnailCount - 1)
            val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight

            for (i in minIndex..maxIndex) {
                if (i == currentIndex) continue
                if (thumbnailCache.get(i) != null) continue

                val sheetIndex = i / thumbnailsPerSheet
                val tileIndex = i % thumbnailsPerSheet
                val tileCol = tileIndex % trickplayInfo.tileWidth
                val tileRow = tileIndex / trickplayInfo.tileWidth

                val sheet = spriteSheetCache.get(sheetIndex)
                    ?: loadSpriteSheet(id, sheetIndex, trickplayInfo)
                    ?: continue

                val offsetX = tileCol * trickplayInfo.width
                val offsetY = tileRow * trickplayInfo.height

                withContext(Dispatchers.Default) {
                    try {
                        val thumbnail = Bitmap.createBitmap(
                            sheet, offsetX, offsetY, trickplayInfo.width, trickplayInfo.height,
                        )
                        thumbnailCache.put(i, thumbnail)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    fun clear() {
        preloadJob?.cancel()
        preloadJob = null

        thumbnailCache.evictAll()
        spriteSheetCache.evictAll()

        info = null
        itemId = null
        localCacheDir = null
        persistDir = null
        sheetMutexes.clear()
        scope.coroutineContext.cancelChildren()
    }

    companion object {
        private const val MAX_THUMBNAIL_CACHE_BYTES = 16 * 1024 * 1024L
        private const val MAX_SPRITE_SHEET_CACHE_BYTES = 64 * 1024 * 1024L
        private const val PRELOAD_NEIGHBOR_COUNT = 1
    }
}
