package com.raulshma.jellyplay.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import android.util.LruCache
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * (image id, url) entry the Continue-Watching paths share: [imageId] is the
 * series id when the row is an episode and keys the factory's poster cache,
 * [url] is the resolved image URL at the widget's cell width.
 */
data class ContinueWatchingPosterEntry(
    val imageId: String,
    val url: String,
)

/**
 * Centralised Coil → Bitmap pipeline used by every widget factory.
 *
 * The pipeline is intentionally small:
 *   1. Decode at most [WIDGET_IMAGE_TARGET] (px on the long edge) so the
 *      widget never holds > 250 KB per cell — well inside Android's
 *      per-process bitmap budget.
 *   2. Force `allowHardware(false)` because `RemoteViews.setImageViewBitmap`
 *      does not accept hardware bitmaps on older Android versions.
 *   3. Apply a rounded-corner mask so the cell matches the dark Material
 *      surfaces used elsewhere in the widget.
 */
object WidgetImageLoader {

    private const val WIDGET_IMAGE_TARGET = 480

    // Cap any single poster load so a slow Jellyfin server can't pin the
    // widget's binder thread indefinitely. Returning `null` makes the factory
    // fall back to its placeholder drawable — preferable to a frozen cell.
    private const val WIDGET_LOAD_TIMEOUT_MS = 2_000L

    // Overall deadline for a whole batch fetch. RemoteViewsFactory callbacks
    // run on the widget host's binder thread, so without a hard ceiling N slow
    // URLs (each up to WIDGET_LOAD_TIMEOUT_MS) can stall the launcher and ANR
    // it. Bounded batches return a partial map; unresolved URLs fall back to
    // the placeholder just like an individual timeout.
    private const val WIDGET_PRELOAD_DEADLINE_MS = 2_000L

    // Cap how many distinct posters a single batch fetch attempts. A widget
    // cell count is small; loading more just widens the blocking window on
    // the binder thread for content the user has to scroll to see anyway.
    private const val WIDGET_PRELOAD_MAX_URLS = 12

    /**
     * Process-scoped decoded-poster cache keyed by image URL. RemoteViewsService
     * factories are recreated frequently (new binder, process restart, config
     * change) and previously re-fetched every poster on each re-bind even when
     * the URL was unchanged. This LruCache makes repeat binds a map lookup.
     *
     * Sized by bitmap byte count (~250KB per decoded+rounded poster at
     * [WIDGET_IMAGE_TARGET]); ~6MB comfortably holds a full widget grid and is
     * well inside the widget process's bitmap budget.
     */
    private val posterMemoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Background prewarm scope (CONC-6): fire-and-forget poster fetches kicked
    // from the widget snapshot-push paths, so a later factory
    // `onDataSetChanged` resolves from [posterMemoryCache] instead of paying
    // its bounded blocking preload against a cold server. Process-scoped like
    // the cache itself; SupervisorJob so one failed URL never cancels the
    // rest of a prewarm batch.
    private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun loadPoster(context: Context, url: String?, cornerRadiusDp: Float = 10f): Bitmap? {
        if (url.isNullOrBlank()) return null
        // Serve from the process cache first so a factory re-bind (or a second
        // widget on the same launcher) does not re-decode/re-fetch.
        posterMemoryCache.get(url)?.let { return it }
        val bitmap = withTimeoutOrNull(WIDGET_LOAD_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(WIDGET_IMAGE_TARGET, WIDGET_IMAGE_TARGET)
                        .allowHardware(false)
                        // The widget needs its own private bitmap instance: the
                        // default `toBitmap()` unwrap returns the *shared* Bitmap
                        // held in Coil's memory cache (and handed out to Compose
                        // `AsyncImage` consumers). Recycling that shared bitmap
                        // in [applyRoundedCorners] then crashed any Compose
                        // painter still drawing the same URL with
                        // "Canvas: trying to use a recycled bitmap". Disabling
                        // the memory cache for the widget request guarantees the
                        // returned bitmap is a fresh decode owned only by us, so
                        // recycling it after rounding is safe.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                    val image = context.imageLoader.execute(request).image
                    image?.toBitmap()?.let { applyRoundedCorners(context, it, cornerRadiusDp) }
                }.getOrNull()
            }
        }
        if (bitmap != null) posterMemoryCache.put(url, bitmap)
        return bitmap
    }

    /**
     * Awaited batch poster fetch so `RemoteViewsFactory.getViewAt` can read
     * from the resulting cache instead of doing per-cell network I/O on the
     * binder thread. Distinct from [prewarmPosters]: this one suspends until
     * the batch resolves (or its deadline hits) and returns the bitmaps;
     * prewarm is fire-and-forget cache fill.
     *
     * Two safety rails keep this from ANR-ing the launcher when called from a
     * factory's `onDataSetChanged`:
     *   1. The batch is capped at [WIDGET_PRELOAD_MAX_URLS] distinct URLs.
     *   2. The whole batch is bounded by [WIDGET_PRELOAD_DEADLINE_MS]; any URL
     *      not resolved in time simply maps to `null` (placeholder fallback).
     *
     * Already-cached posters (from a prior fetch or [prewarmPosters] in this
     * process) are served instantly from [posterMemoryCache] and don't count
     * against the deadline.
     */
    suspend fun fetchPosters(
        context: Context,
        urls: Collection<String>,
        cornerRadiusDp: Float = 10f,
    ): Map<String, Bitmap?> {
        val distinct = urls.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val capped = distinct.take(WIDGET_PRELOAD_MAX_URLS)
        // withTimeoutOrNull returns null on timeout — treat that as "resolve
        // whatever landed". Because the async loads write into posterMemoryCache
        // as they complete, a timeout still leaves any finished entries cached
        // for the next bind; here we just report the ones that resolved in time
        // (reading them back out of the cache — the timed-out block itself is
        // cancelled before awaitAll can return), so one slow server never
        // blanks every cell for the whole bind.
        return withTimeoutOrNull(WIDGET_PRELOAD_DEADLINE_MS) {
            coroutineScope {
                capped.map { url -> async { url to loadPoster(context, url, cornerRadiusDp) } }
                    .awaitAll()
                    .toMap()
            }
        } ?: capped.associateWith { url -> posterMemoryCache.get(url) }
    }

    /**
     * Fire-and-forget [posterMemoryCache] prewarm (CONC-6) for the widget
     * snapshot-push paths: decodes the same URLs, at the same target size and
     * default corner radius, into the same cache the factories read — so a
     * later factory bind resolves from memory. Purely additive warm-up: the
     * factories' own bounded `onDataSetChanged` [fetchPosters] remains the
     * fallback whenever the prewarm hasn't landed (or was evicted). Unlike
     * [fetchPosters] there is no batch deadline — nobody waits on this —
     * but each URL is still individually bounded by
     * [WIDGET_LOAD_TIMEOUT_MS] via [loadPoster], and the same
     * [WIDGET_PRELOAD_MAX_URLS] cap applies. Never triggers widget updates
     * itself; cache hits are free, so repeat calls are cheap. Accepts null
     * urls (snapshot item types carry nullable poster urls) and skips them.
     */
    fun prewarmPosters(context: Context, urls: Collection<String?>) {
        val distinct = urls.filter { !it.isNullOrBlank() }.distinct().take(WIDGET_PRELOAD_MAX_URLS)
        if (distinct.isEmpty()) return
        prewarmScope.launch {
            distinct.map { url -> async { loadPoster(context, url) } }.awaitAll()
        }
    }

    /**
     * Poster-cache key for a Continue-Watching row: the series id when the
     * row is an episode, else the item id. Single source for the entry's
     * [ContinueWatchingPosterEntry.imageId] AND the factory's getViewAt
     * lookup, so the key rule cannot drift between the two.
     */
    fun continueWatchingPosterImageId(item: MediaItem): String = item.seriesId ?: item.id

    /**
     * Canonical Continue-Watching poster rule (CONC-6): the CW factory's
     * onDataSetChanged preload and the broadcaster's snapshot prewarm must
     * derive the SAME (image id, url) per row — image id comes from
     * [continueWatchingPosterImageId], maxWidth pins the widget's
     * single-column cell size. Returns the entry so the factory's
     * posterCache key and the prewarmed url share one definition and can't
     * drift.
     */
    fun continueWatchingPosterEntry(
        item: MediaItem,
        playbackRepository: PlaybackRepository,
    ): ContinueWatchingPosterEntry {
        val imageId = continueWatchingPosterImageId(item)
        return ContinueWatchingPosterEntry(imageId, playbackRepository.getImageUrl(imageId, maxWidth = 300))
    }

    /**
     * Fire-and-forget prewarm (CONC-6) for the whole Continue-Watching
     * snapshot: derives each row's url via [continueWatchingPosterEntry] —
     * the same rule the CW factory's preload uses — so the prewarmed urls
     * and the factory's binds cannot drift. It serves every widget instance
     * at once (per-widget item counts are unknown to the caller), so it maps
     * the whole snapshot and lets [prewarmPosters] apply its batch cap.
     */
    fun prewarmContinueWatchingPosters(
        context: Context,
        items: List<MediaItem>,
        playbackRepository: PlaybackRepository,
    ) {
        prewarmPosters(context, items.map { continueWatchingPosterEntry(it, playbackRepository).url })
    }

    private fun applyRoundedCorners(context: Context, bitmap: Bitmap, cornerRadiusDp: Float = 10f): Bitmap {
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = cornerRadiusDp * density
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val paint = Paint().apply {
                isAntiAlias = true
                color = 0xff424242.toInt()
            }
            val rect = Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = RectF(rect)
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
        } finally {
            // The input bitmap is no longer referenced once it has been drawn
            // into `output`; recycle it promptly to avoid N pairs of bitmaps
            // briefly coexisting during concurrent `fetchPosters` fetches.
            bitmap.recycle()
        }
        return output
    }
}
