package com.raulshma.jellyplay.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository

/**
 * Backs the Continue Watching widget's `ListView` with a
 * [RemoteViewsFactory] that pulls the latest snapshot from
 * [UserPreferencesStore.continueWatching].
 *
 * RemoteViewsFactory operations are synchronous, so the Hilt entry point
 * is used to grab a [UserPreferencesStore] handle and the latest list is
 * loaded with [runBlocking] on each [onDataSetChanged] call. The
 * [ContinueWatchingWidget] calls
 * [AppWidgetManager.notifyAppWidgetViewDataChanged] whenever the data
 * changes, which re-binds the factory.
 */
class ContinueWatchingWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun preferencesStore(): UserPreferencesStore
        fun playbackRepository(): PlaybackRepository
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java,
        )
        val store = entryPoint.preferencesStore()
        val playbackRepo = entryPoint.playbackRepository()
        return ContinueWatchingFactory(applicationContext, store, playbackRepo)
    }

    private class ContinueWatchingFactory(
        private val context: Context,
        private val store: UserPreferencesStore,
        private val playbackRepository: PlaybackRepository,
    ) : RemoteViewsFactory {

        private var items: List<MediaItem> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            items = runBlocking { store.continueWatching.first() }
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val item = items.getOrNull(position) ?: return loadingView()
            val view = RemoteViews(context.packageName, R.layout.continue_watching_item)
            view.setTextViewText(R.id.cw_item_title, item.name)
            view.setTextViewText(R.id.cw_item_subtitle, buildSubtitle(item))
            val progress = computeProgress(item)
            if (progress != null) {
                view.setProgressBar(R.id.cw_item_progress, 100, progress, false)
                view.setViewVisibility(R.id.cw_item_progress, View.VISIBLE)
                view.setViewVisibility(R.id.cw_item_remaining, View.VISIBLE)
                view.setTextViewText(
                    R.id.cw_item_remaining,
                    buildRemainingText(item, progress),
                )
            } else {
                view.setProgressBar(R.id.cw_item_progress, 100, 0, false)
                view.setViewVisibility(R.id.cw_item_progress, View.GONE)
                view.setViewVisibility(R.id.cw_item_remaining, View.GONE)
            }

            // Load poster image
            val imageId = item.seriesId ?: item.id
            val posterUrl = playbackRepository.getImageUrl(imageId, maxWidth = 300)
            android.util.Log.d("ContinueWatchingWidget", "Poster URL for ${item.name} (ID: ${item.id}, ImageID: $imageId): $posterUrl")
            val posterBitmap = if (!posterUrl.isNullOrBlank()) {
                runBlocking {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(posterUrl)
                            .size(300, 450)
                            .allowHardware(false)
                            .build()
                        val result = context.imageLoader.execute(request)
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val density = context.resources.displayMetrics.density
                            val cornerRadiusPx = 8f * density
                            getRoundedCornerBitmap(bitmap, cornerRadiusPx)
                        } else {
                            android.util.Log.w("ContinueWatchingWidget", "Result image is null for ${item.name}")
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ContinueWatchingWidget", "Exception loading poster for ${item.name}", e)
                        null
                    }
                }
            } else null

            if (posterBitmap != null) {
                view.setImageViewBitmap(R.id.cw_item_poster, posterBitmap)
            } else {
                view.setImageViewResource(R.id.cw_item_poster, R.drawable.ic_banner)
            }

            val deepLinkUri = Uri.parse("${DeepLinkHandler.SCHEME_CUSTOM}://media/${item.id}")
            val fillIn = Intent().apply {
                action = Intent.ACTION_VIEW
                data = deepLinkUri
                putExtra(EXTRA_ITEM_ID, item.id)
            }
            view.setOnClickFillInIntent(R.id.cw_item_root, fillIn)
            return view
        }

        override fun getLoadingView(): RemoteViews = loadingView()

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true

        private fun loadingView(): RemoteViews {
            val view = RemoteViews(context.packageName, R.layout.continue_watching_item)
            view.setTextViewText(R.id.cw_item_title, "")
            view.setTextViewText(R.id.cw_item_subtitle, "")
            return view
        }

        private fun buildSubtitle(item: MediaItem): String {
            val parts = mutableListOf<String>()
            item.seriesName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            item.seasonNumber?.let { season ->
                item.episodeNumber?.let { ep ->
                    parts.add("S${season}E${ep.toString().padStart(2, '0')}")
                } ?: parts.add("S$season")
            }
            return parts.joinToString(" · ")
        }

        private fun buildRemainingText(item: MediaItem, progress: Int): String {
            val totalTicks = item.runTimeTicks ?: 0L
            val posTicks = item.playbackPositionTicks ?: 0L
            val leftTicks = totalTicks - posTicks
            if (leftTicks > 0L) {
                val minsLeft = (leftTicks / 10_000_000L / 60L).toInt()
                if (minsLeft > 0) {
                    return context.getString(R.string.widget_minutes_left, minsLeft)
                }
            }
            return context.getString(R.string.widget_progress_percent, progress)
        }

        private fun computeProgress(item: MediaItem): Int? {
            val total = item.runTimeTicks ?: return null
            val pos = item.playbackPositionTicks ?: return null
            if (total <= 0L) return null
            val pct = (pos.toDouble() / total.toDouble() * 100.0).toInt()
            return pct.coerceIn(0, 100)
        }

        private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = 0xff424242.toInt()
            }
            val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = android.graphics.RectF(rect)
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawRoundRect(rectF, pixels, pixels, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_cw_item_id"
    }
}
