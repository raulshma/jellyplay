package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * Backs the Library Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [WidgetDataStore.libraryWidgetItems].
 *
 * `onDataSetChanged` runs on the main thread; items are read from the
 * store's eagerly-warmed [kotlinx.coroutines.flow.StateFlow] snapshots, so
 * no DataStore disk IO blocks them once warmed. On a cold process the
 * first read pays one bounded (≤1 s) warm-up — see [WidgetDataStore]'s
 * *Snapshot() docs. The
 * [LibraryRecommendationsWidget] calls
 * [AppWidgetManager.notifyAppWidgetViewDataChanged] whenever the data
 * changes, which re-binds the factory.
 */
class LibraryRecommendationsWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun widgetDataStore(): WidgetDataStore
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java,
        )
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return LibraryRecommendationsFactory(applicationContext, entryPoint.widgetDataStore(), appWidgetId)
    }

    private class LibraryRecommendationsFactory(
        private val context: Context,
        private val store: WidgetDataStore,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        private var items: List<LibraryWidgetItem> = emptyList()
        private var loadedVersion: Long = -1L
        // Poster cache populated in [onDataSetChanged] so [getViewAt] never
        // performs network I/O on the binder thread.
        private var posterCache: Map<String, Bitmap?> = emptyMap()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            // Always re-read the latest snapshot. The previous early-return
            // (`if (version == loadedVersion) return`) skipped loads whenever
            // the persisted version matched the factory's cached value, but
            // that left the widget blank if the factory was recreated (new
            // binder, process restart) and the worker happened to short-circuit
            // the version bump in [WidgetPersistHelper] because the content
            // was unchanged. Always reading is cheap (single DataStore read)
            // and makes the widget resilient to those edge cases.
            // Memory reads from the store's eagerly-warmed snapshots — no
            // DataStore disk IO on the main thread once warmed (cold-process
            // behavior: see WidgetDataStore's *Snapshot() docs).
            val (fresh, version) = store.libraryWidgetItemsSnapshot() to store.libraryWidgetVersion.value
            items = fresh
            loadedVersion = version
            // Pre-fetch posters concurrently so each `getViewAt` is a map lookup.
            // `posterUrl` is nullable; blank/null entries fall through to the placeholder.
            posterCache = if (items.isEmpty()) {
                emptyMap()
            } else {
                val nonNullUrls = items.map { it.posterUrl }.filterNotNull()
                runBlocking { WidgetImageLoader.preloadPosters(context, nonNullUrls) }
            }
        }

        override fun onDestroy() {
            items = emptyList()
            posterCache = emptyMap()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val item = items.getOrNull(position) ?: return loadingView()
            val view = RemoteViews(context.packageName, R.layout.library_recommendations_item)
            val title = buildTitle(item)
            view.setTextViewText(R.id.lr_item_title, title)
            view.setViewVisibility(R.id.lr_item_title, View.VISIBLE)
            view.setTextViewText(R.id.lr_item_subtitle, buildSubtitle(item))
            view.setViewVisibility(R.id.lr_item_subtitle, View.VISIBLE)

            // Apply responsive rules based on widget options
            var hideText = false

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val dims = computeWidgetDimensions(context, appWidgetId, 250)
                if (dims != null) {
                    val width = dims.width
                    val height = dims.height

                    if (height < 200 || width < 180) {
                        hideText = true
                    }
                }
            }

            if (hideText) {
                view.setViewVisibility(R.id.lr_item_text_container, View.GONE)
            } else {
                view.setViewVisibility(R.id.lr_item_text_container, View.VISIBLE)
            }

            val bitmap = posterCache[item.posterUrl]
            if (bitmap != null) {
                view.setImageViewBitmap(R.id.lr_item_poster, bitmap)
            } else {
                view.setImageViewResource(R.id.lr_item_poster, R.drawable.widget_backdrop_placeholder)
            }

            val fillIn = Intent().apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(WidgetDeepLinks.buildMediaDeepLink(item.itemId))
                putExtra(EXTRA_ITEM_ID, item.itemId)
            }
            view.setOnClickFillInIntent(R.id.lr_item_root, fillIn)
            return view
        }

        override fun getLoadingView(): RemoteViews = loadingView()

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.itemId?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true

        private fun loadingView(): RemoteViews {
            val view = RemoteViews(context.packageName, R.layout.library_recommendations_item)
            view.setTextViewText(R.id.lr_item_title, "")
            view.setTextViewText(R.id.lr_item_subtitle, "")
            view.setViewVisibility(R.id.lr_item_title, View.INVISIBLE)
            view.setViewVisibility(R.id.lr_item_subtitle, View.INVISIBLE)

            var hideText = false
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val dims = computeWidgetDimensions(context, appWidgetId, 250)
                if (dims != null) {
                    val width = dims.width
                    val height = dims.height

                    if (height < 200 || width < 180) {
                        hideText = true
                    }
                }
            }

            if (hideText) {
                view.setViewVisibility(R.id.lr_item_text_container, View.GONE)
            } else {
                view.setViewVisibility(R.id.lr_item_text_container, View.VISIBLE)
            }
            return view
        }

        private fun buildTitle(item: LibraryWidgetItem): String {
            val raw = item.name.ifBlank { "Untitled" }
            return raw
        }

        private fun buildSubtitle(item: LibraryWidgetItem): String {
            val parts = mutableListOf<String>()
            item.year?.let { parts.add(it.toString()) }
            item.communityRating?.let {
                if (it > 0f) parts.add("★ %.1f".format(it))
            }
            if (parts.isEmpty()) return " "
            return parts.joinToString("  ·  ")
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_lr_item_id"
    }
}
