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

class TrickplayManager(
    private val playbackRepository: PlaybackRepository,
) {

    private val thumbnailCache = LruCache<Int, Bitmap>(MAX_THUMBNAIL_CACHE_SIZE)
    private val spriteSheetCache = LruCache<Int, Bitmap>(MAX_SPRITE_SHEET_CACHE_SIZE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var info: TrickplayInfo? = null
    private var itemId: String? = null

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
            val thumbnail = Bitmap.createBitmap(sheet, offsetX, offsetY, currentInfo.width, currentInfo.height)
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
        val data = playbackRepository.getTrickplayTileImage(id, trickplayInfo.width, sheetIndex)
            ?: return null
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        if (bitmap != null) {
            spriteSheetCache.put(sheetIndex, bitmap)
        }
        return bitmap
    }

    private fun preloadNeighbors(
        id: String,
        currentIndex: Int,
        trickplayInfo: TrickplayInfo,
    ) {
        val preloadRange = PRELOAD_NEIGHBOR_COUNT
        val minIndex = (currentIndex - preloadRange).coerceAtLeast(0)
        val maxIndex = (currentIndex + preloadRange).coerceAtMost(trickplayInfo.thumbnailCount - 1)

        for (i in minIndex..maxIndex) {
            if (i == currentIndex) continue
            if (thumbnailCache.get(i) != null) continue
            scope.launch {
                getThumbnail(i * trickplayInfo.interval.toLong())
            }
        }
    }

    fun clear() {
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
    }

    companion object {
        private const val MAX_THUMBNAIL_CACHE_SIZE = 100
        private const val MAX_SPRITE_SHEET_CACHE_SIZE = 10
        private const val PRELOAD_NEIGHBOR_COUNT = 3
    }
}
