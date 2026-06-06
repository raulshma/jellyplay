package com.raulshma.jellyplay.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Backs the Library Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [UserPreferencesStore.libraryWidgetItems].
 *
 * Factory operations are synchronous, so the Hilt entry point is used
 * to grab a [UserPreferencesStore] handle and the latest list is loaded
 * with [runBlocking] on each [onDataSetChanged] call. The
 * [LibraryRecommendationsWidget] calls
 * [AppWidgetManager.notifyAppWidgetViewDataChanged] whenever the data
 * changes, which re-binds the factory.
 */
class LibraryRecommendationsWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun userPreferencesStore(): UserPreferencesStore
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java,
        )
        return LibraryRecommendationsFactory(applicationContext, entryPoint.userPreferencesStore())
    }

    private class LibraryRecommendationsFactory(
        private val context: Context,
        private val store: UserPreferencesStore,
    ) : RemoteViewsFactory {

        private var items: List<LibraryWidgetItem> = emptyList()
        private var loadedVersion: Long = -1L

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val flow = store.libraryWidgetItems
            val version = runBlocking { store.libraryWidgetVersion.first() }
            val fresh = runBlocking { flow.first() }
            if (version != loadedVersion) {
                items = fresh
                loadedVersion = version
            }
        }

        override fun onDestroy() {
            items = emptyList()
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
            val bitmap = runBlocking {
                WidgetImageLoader.loadPoster(context, item.posterUrl)
            }
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
