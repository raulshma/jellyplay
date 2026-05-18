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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class TrickplayManager(
    private val playbackRepository: PlaybackRepository,
) {

    private val thumbnailCache = LruCache<Int, Bitmap>(MAX_THUMBNAIL_CACHE_SIZE)
    private val spriteSheetCache = LruCache<Int, Bitmap>(MAX_SPRITE_SHEET_CACHE_SIZE)
    private val sheetMutexes = ConcurrentHashMap<Int, Mutex>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var info: TrickplayInfo? = null
    private var itemId: String? = null
    private var preloadJob: kotlinx.coroutines.Job? = null

    fun initialize(itemId: String, trickplayInfo: TrickplayInfo) {
        clear()
        this.itemId = itemId
        this.info = trickplayInfo
    }

    suspend fun getThumbnail(positionMs: Long): Bitmap? {
        val currentInfo = info ?: return null
        val id = itemId ?: return null

        val thumbnailIndex = (positionMs / currentInfo.interval).toInt()
            .coerceIn(0, currentInfo.thumbnailCount - 1)

        thumbnailCache.get(thumbnailIndex)?.let { return it }

        val thumbnailsPerSheet = currentInfo.tileWidth * currentInfo.tileHeight
        val sheetIndex = thumbnailIndex / thumbnailsPerSheet
        val tileIndex = thumbnailIndex % thumbnailsPerSheet
        val tileCol = tileIndex % currentInfo.tileWidth
        val tileRow = tileIndex / currentInfo.tileWidth

        val sheet = spriteSheetCache.get(sheetIndex)
            ?: loadSpriteSheet(id, sheetIndex, currentInfo)
            ?: return null

        val offsetX = tileCol * currentInfo.width
        val offsetY = tileRow * currentInfo.height

        try {
            val thumbnail = Bitmap.createBitmap(
                sheet, offsetX, offsetY, currentInfo.width, currentInfo.height,
            )
            thumbnailCache.put(thumbnailIndex, thumbnail)
            preloadNeighbors(id, thumbnailIndex, currentInfo)
            return thumbnail
        } catch (_: Exception) {
            return null
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

            val data = playbackRepository.getTrickplayTileImage(id, trickplayInfo.width, sheetIndex)
                ?: return@withLock null

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inMutable = false
            }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
            if (bitmap != null) {
                spriteSheetCache.put(sheetIndex, bitmap)
            }
            bitmap
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

                try {
                    val thumbnail = Bitmap.createBitmap(
                        sheet, offsetX, offsetY, trickplayInfo.width, trickplayInfo.height,
                    )
                    thumbnailCache.put(i, thumbnail)
                } catch (_: Exception) { }
            }
        }
    }

    fun clear() {
        preloadJob?.cancel()
        preloadJob = null

        val it = thumbnailCache.snapshot().values.iterator()
        while (it.hasNext()) {
            it.next().recycle()
        }
        thumbnailCache.evictAll()

        val it2 = spriteSheetCache.snapshot().values.iterator()
        while (it2.hasNext()) {
            it2.next().recycle()
        }
        spriteSheetCache.evictAll()

        info = null
        itemId = null
        sheetMutexes.clear()
    }

    companion object {
        private const val MAX_THUMBNAIL_CACHE_SIZE = 100
        private const val MAX_SPRITE_SHEET_CACHE_SIZE = 10
        private const val PRELOAD_NEIGHBOR_COUNT = 3
    }
}
