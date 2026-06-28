package com.raulshma.jellyplay.feature.player.video.trickplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TrickplayManager(
    private val playbackRepository: PlaybackRepository,
    lowRamDevice: Boolean = false,
) {

    private val maxThumbnailCacheBytes = if (lowRamDevice) LOW_RAM_THUMBNAIL_BYTES else MAX_THUMBNAIL_CACHE_BYTES
    private val maxSpriteSheetCacheBytes = if (lowRamDevice) LOW_RAM_SPRITE_SHEET_BYTES else MAX_SPRITE_SHEET_CACHE_BYTES

    private fun obtainTileBitmap(width: Int, height: Int): Bitmap {
        // NOTE: a bitmap pool previously fed by thumbnailCache.entryRemoved
        // was removed to fix the recycle-vs-Compose-use race (H8) and the
        // non-thread-safe ArrayDeque access (H9). Thumbnails handed to the
        // UI are now dropped (never recycled) on eviction and left for GC.
        return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    }

    private val thumbnailCache = object : LruCache<Int, Bitmap>((maxThumbnailCacheBytes / 1024).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount / 1024
        override fun entryRemoved(evictedBySize: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            // Intentionally do NOT recycle. Thumbnails returned from
            // [getThumbnail] are held directly by TrickplayOverlay's
            // `remember(bitmap) { bitmap?.asImageBitmap() }`. Recycling an
            // evicted entry here used to race with Compose's draw pass and
            // threw IllegalStateException ("Cannot draw a recycled bitmap").
            // Let GC reclaim bitmaps once no caller (cache or UI) holds them.
        }
    }
    private val spriteSheetCache = object : LruCache<Int, Bitmap>((maxSpriteSheetCacheBytes / 1024).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount / 1024
        override fun entryRemoved(evictedBySize: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (!oldValue.isRecycled) oldValue.recycle()
        }
    }
    private val sheetMutexes = ConcurrentHashMap<Int, Mutex>()

    // `var` so [clear] can cancel the supervisor and its orphaned children
    // (fire-and-forget `scope.launch { persistDirectory…writeBytes }` and any
    // outstanding `preloadJob`). Without this the supervisor and pending IO
    // outlive `clear()` — the manager is held as a VM field so dies with the
    // VM, but per-item `clear()` on item switch leaked work from the previous
    // item. Recreated lazily, only when inactive (mirrors the engineScope
    // pattern that fixed H5).
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        prefetchInitial(trickplayInfo)
    }

    fun initializeWithCache(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {
        clear()
        this.itemId = itemId
        this.info = trickplayInfo
        this.localCacheDir = null
        this.persistDir = cacheDir.apply { mkdirs() }
        prefetchInitial(trickplayInfo)
    }

    fun initializeLocal(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {
        clear()
        this.itemId = itemId
        this.info = trickplayInfo
        this.localCacheDir = cacheDir
        prefetchInitial(trickplayInfo)
    }

    private fun prefetchInitial(trickplayInfo: TrickplayInfo) {
        val id = itemId ?: return
        scope.launch {
            loadSpriteSheet(id, 0, trickplayInfo) ?: return@launch
        }
    }

    suspend fun getThumbnail(positionMs: Long): Bitmap? {
        val currentInfo = info ?: return null
        val id = itemId ?: return null

        val thumbnailIndex = (positionMs / currentInfo.interval).toInt()
            .coerceIn(0, currentInfo.thumbnailCount - 1)

        thumbnailCache.get(thumbnailIndex)?.let { return it }

        return withContext(Dispatchers.Default) {
            try {
                val thumbnailsPerSheet = currentInfo.tileWidth * currentInfo.tileHeight
                val sheetIndex = thumbnailIndex / thumbnailsPerSheet

                val sheet = spriteSheetCache.get(sheetIndex)
                    ?: loadSpriteSheet(id, sheetIndex, currentInfo)
                    ?: return@withContext null

                val result = extractTile(sheet, thumbnailIndex, currentInfo)
                if (result != null) {
                    thumbnailCache.put(thumbnailIndex, result)
                }

                schedulePreload(id, thumbnailIndex, sheetIndex, currentInfo)

                result
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun extractTile(
        sheet: Bitmap,
        thumbnailIndex: Int,
        trickplayInfo: TrickplayInfo,
    ): Bitmap? {
        val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
        val sheetStartIndex = (thumbnailIndex / thumbnailsPerSheet) * thumbnailsPerSheet
        val tileIndex = thumbnailIndex - sheetStartIndex
        val tileCol = tileIndex % trickplayInfo.tileWidth
        val tileRow = tileIndex / trickplayInfo.tileWidth
        val offsetX = tileCol * trickplayInfo.width
        val offsetY = tileRow * trickplayInfo.height
        return try {
            val tile = obtainTileBitmap(trickplayInfo.width, trickplayInfo.height)
            val canvas = android.graphics.Canvas(tile)
            canvas.drawBitmap(sheet, android.graphics.Rect(offsetX, offsetY, offsetX + trickplayInfo.width, offsetY + trickplayInfo.height), android.graphics.Rect(0, 0, trickplayInfo.width, trickplayInfo.height), null)
            tile
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTileRange(
        sheet: Bitmap,
        sheetIndex: Int,
        centerTileIndex: Int,
        range: Int,
        trickplayInfo: TrickplayInfo,
    ) {
        val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
        val startIndex = sheetIndex * thumbnailsPerSheet
        val localCenter = centerTileIndex - startIndex
        val localMin = (localCenter - range).coerceAtLeast(0)
        val localMax = (localCenter + range).coerceAtMost(thumbnailsPerSheet - 1)
        val w = trickplayInfo.width
        val h = trickplayInfo.height

        for (localIdx in localMin..localMax) {
            val globalIndex = startIndex + localIdx
            if (thumbnailCache.get(globalIndex) != null) continue
            val col = localIdx % trickplayInfo.tileWidth
            val row = localIdx / trickplayInfo.tileWidth
            try {
                val tile = obtainTileBitmap(w, h)
                val canvas = android.graphics.Canvas(tile)
                canvas.drawBitmap(sheet, android.graphics.Rect(col * w, row * h, col * w + w, row * h + h), android.graphics.Rect(0, 0, w, h), null)
                thumbnailCache.put(globalIndex, tile)
            } catch (_: Exception) { }
        }
    }

    private fun schedulePreload(
        id: String,
        currentIndex: Int,
        currentSheetIndex: Int,
        trickplayInfo: TrickplayInfo,
    ) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val currentSheet = spriteSheetCache.get(currentSheetIndex)
            if (currentSheet != null) {
                extractTileRange(currentSheet, currentSheetIndex, currentIndex, PREFETCH_TILE_RANGE, trickplayInfo)
            }
            preloadAdjacentSheets(id, currentSheetIndex, trickplayInfo)
            preloadNeighborTiles(id, currentIndex, trickplayInfo)
        }
    }

    private suspend fun preloadAdjacentSheets(
        id: String,
        currentSheetIndex: Int,
        trickplayInfo: TrickplayInfo,
    ) {
        val totalSheets = (trickplayInfo.thumbnailCount + trickplayInfo.tileWidth * trickplayInfo.tileHeight - 1) /
            (trickplayInfo.tileWidth * trickplayInfo.tileHeight)

        for (offset in 1..ADJACENT_SHEET_PRELOAD_COUNT) {
            for (sheetIdx in listOf(currentSheetIndex + offset, currentSheetIndex - offset)) {
                if (sheetIdx < 0 || sheetIdx >= totalSheets) continue
                if (spriteSheetCache.get(sheetIdx) != null) continue

                withTimeoutOrNull(SHEET_PRELOAD_TIMEOUT_MS) {
                    loadSpriteSheet(id, sheetIdx, trickplayInfo)
                }
            }
        }
    }

    private suspend fun preloadNeighborTiles(
        id: String,
        currentIndex: Int,
        trickplayInfo: TrickplayInfo,
    ) {
        val minIndex = (currentIndex - PRELOAD_NEIGHBOR_COUNT).coerceAtLeast(0)
        val maxIndex = (currentIndex + PRELOAD_NEIGHBOR_COUNT).coerceAtMost(trickplayInfo.thumbnailCount - 1)
        val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight

        for (i in minIndex..maxIndex) {
            if (thumbnailCache.get(i) != null) continue

            val sheetIndex = i / thumbnailsPerSheet
            val sheet = spriteSheetCache.get(sheetIndex)
                ?: continue

            extractTile(sheet, i, trickplayInfo)?.let { tile ->
                thumbnailCache.put(i, tile)
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

                decodeSpriteSheetSafely(data, trickplayInfo)
            }.also { bitmap ->
                if (bitmap != null) {
                    spriteSheetCache.put(sheetIndex, bitmap)
                }
            }
        }
    }

    /**
     * Decodes a sprite-sheet JPEG with an explicit OOM guard. A malicious or
     * truncated payload can otherwise force [BitmapFactory.decodeByteArray]
     * to allocate a multi-GB bitmap; `OutOfMemoryError` extends `Error`, not
     * `Exception`, so the callers' `catch (_: Exception)` would not catch it
     * and the process would crash. We bounds-check first (rejecting anything
     * beyond 2x the expected sheet dimensions or an absolute pixel ceiling)
     * and catch [Throwable] as a last line of defence. No downsampling is
     * applied because [extractTile] assumes full-resolution sheets.
     */
    private fun decodeSpriteSheetSafely(data: ByteArray, trickplayInfo: TrickplayInfo): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val expectedWidth = trickplayInfo.width * trickplayInfo.tileWidth
        val expectedHeight = trickplayInfo.height * trickplayInfo.tileHeight
        val widthCap = expectedWidth.coerceAtLeast(1) * 2
        val heightCap = expectedHeight.coerceAtLeast(1) * 2
        if (bounds.outWidth > widthCap || bounds.outHeight > heightCap) return null
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_SPRITE_SHEET_PIXELS) return null

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = false
        }
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (t: Throwable) {
            null
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

        // Cancel the supervisor and any in-flight children (preload, persist
        // writes) so work from the previous item does not leak into the next
        // session. A fresh scope is created for the next [initialize] call.
        // Guarded because clear() is idempotent and may be invoked twice
        // (once per initialize, once on VM release).
        if (scope.isActive) {
            scope.cancel()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    companion object {
        private const val MAX_THUMBNAIL_CACHE_BYTES = 24 * 1024 * 1024L
        private const val MAX_SPRITE_SHEET_CACHE_BYTES = 96 * 1024 * 1024L
        private const val LOW_RAM_THUMBNAIL_BYTES = 12 * 1024 * 1024L
        private const val LOW_RAM_SPRITE_SHEET_BYTES = 48 * 1024 * 1024L
        private const val PRELOAD_NEIGHBOR_COUNT = 5
        private const val PREFETCH_TILE_RANGE = 10
        private const val ADJACENT_SHEET_PRELOAD_COUNT = 1
        private const val SHEET_PRELOAD_TIMEOUT_MS = 3000L
        // Absolute pixel backstop for sprite-sheet decoding, guarding against
        // pathological payloads even when reported tile dimensions are huge.
        // ~40 MP fits well within the RGB_565 sprite-sheet cache budget.
        private const val MAX_SPRITE_SHEET_PIXELS = 40_000_000L
    }
}
