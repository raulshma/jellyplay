package com.raulshma.jellyplay.widget.skeleton

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.widget.WidgetDimensions
import com.raulshma.jellyplay.widget.WidgetLayoutThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric lifecycle test for the shared grid `RemoteViewsFactory`
 * skeleton, over fake hooks — the three real factories only supply seams, so
 * this pins the choreography they all inherit:
 *
 *  - `onDataSetChanged` re-reads the snapshot, then batch-preloads posters
 *    (skipped entirely for an empty snapshot), and refreshes dimensions;
 *  - `getCount`/`getItemId`/`hasStableIds`/`getViewTypeCount` are snapshot
 *    -driven with the position fallback for out-of-range ids;
 *  - `getViewAt` binds each in-range row through the seams (bind + poster
 *    lookup + fill-in intent) and falls back to the loading view — a
 *    different layout in this test, so the fallback is observable via the
 *    layout id — for out-of-range positions;
 *  - `onDestroy` drops the state so a destroyed factory renders loading.
 *
 * The adapter seam wiring ([posterUrlOf]-keyed default poster pipeline) is
 * pinned by feeding a fake preload map and reading it back through the
 * default [WidgetGridFactory.posterFor].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetGridFactoryTest {

    /**
     * Records every seam call. The loading view renders the Seerr row layout
     * so tests can distinguish "loading" from a bound row by layout id
     * alone; `appWidgetId` is INVALID so the dimensions refresh deterministically
     * resolves to null (no options read for an unregistered id).
     */
    private class FakeFactory(
        context: Context,
        private val snapshot: List<String>,
        private val preloaded: Map<String, Bitmap?> = emptyMap(),
    ) : WidgetGridFactory<String>(
        context = context,
        appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
        itemLayoutRes = R.layout.library_recommendations_item,
        itemRootViewId = R.id.lr_item_root,
        titleViewId = R.id.lr_item_title,
        subtitleViewId = R.id.lr_item_subtitle,
        defaultHeightDp = WidgetLayoutThresholds.RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP,
    ) {
        val events = mutableListOf<String>()
        val boundItems = mutableListOf<String>()
        val fillInsFor = mutableListOf<String>()
        val postersAtBind = mutableListOf<Bitmap?>()
        val textVisibleAtBind = mutableListOf<Boolean>()

        /** Exposes the base's protected state from inside the subclass. */
        fun internalDims(): WidgetDimensions? = widgetDims

        fun internalPosterCache(): Map<String, Bitmap?> = posterCache

        override fun snapshotProvider(): List<String> {
            events += "snapshot"
            return snapshot
        }

        override fun posterUrlOf(item: String): String? = item

        override suspend fun preloadPosters(items: List<String>): Map<String, Bitmap?> {
            events += "preload(${items.size})"
            return preloaded
        }

        override fun bind(view: RemoteViews, item: String) {
            boundItems += item
            postersAtBind += posterFor(item)
            textVisibleAtBind += gridCellTextVisible(widgetDims)
        }

        override fun stableIdOf(item: String): Long = item.length.toLong()

        override fun fillInIntent(item: String): Intent {
            fillInsFor += item
            return Intent(Intent.ACTION_VIEW, Uri.parse("jellyfin://media/$item"))
        }

        override fun loadingView(): RemoteViews =
            RemoteViews(context.packageName, R.layout.seerr_recommendations_item)
                .clearRowTexts()
    }

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    @Test
    fun `onDataSetChanged reads the snapshot then preloads posters`() {
        val factory = FakeFactory(context, snapshot = listOf("aa", "bbb"))

        factory.onDataSetChanged()

        assertEquals(listOf("snapshot", "preload(2)"), factory.events)
    }

    @Test
    fun `count ids and view type are snapshot-driven with stable ids`() {
        val factory = FakeFactory(context, snapshot = listOf("aa", "bbb"))
        factory.onDataSetChanged()

        assertEquals(2, factory.count)
        // stableIdOf maps to the item length: "aa" → 2, "bbb" → 3.
        assertEquals(2L, factory.getItemId(0))
        assertEquals(3L, factory.getItemId(1))
        // Out-of-range reads fall back to the position.
        assertEquals(5L, factory.getItemId(5))
        assertTrue(factory.hasStableIds())
        assertEquals(1, factory.viewTypeCount)
    }

    @Test
    fun `getViewAt binds every in-range row and applies its fill-in intent`() {
        val poster = bitmap()
        val factory = FakeFactory(
            context,
            snapshot = listOf("aa", "bbb"),
            preloaded = mapOf("aa" to poster, "bbb" to null),
        )
        factory.onDataSetChanged()

        val first = factory.getViewAt(0)
        val second = factory.getViewAt(1)

        assertEquals(listOf("aa", "bbb"), factory.boundItems)
        assertEquals(listOf("aa", "bbb"), factory.fillInsFor)
        // The default poster pipeline is keyed by posterUrlOf.
        assertEquals(listOf(poster, null), factory.postersAtBind)
        // INVALID appWidgetId → no dimensions → the text rule keeps text.
        assertTrue(factory.textVisibleAtBind.all { it })
        assertEquals(R.layout.library_recommendations_item, first.layoutId)
        assertEquals(R.layout.library_recommendations_item, second.layoutId)
    }

    @Test
    fun `getViewAt falls back to the loading view out of range`() {
        val factory = FakeFactory(context, snapshot = listOf("aa"))
        factory.onDataSetChanged()

        assertEquals(R.layout.seerr_recommendations_item, factory.getViewAt(-1).layoutId)
        assertEquals(R.layout.seerr_recommendations_item, factory.getViewAt(1).layoutId)
        // No in-range position was requested, so no bind or fill-in ran.
        assertTrue(factory.boundItems.isEmpty())
        assertTrue(factory.fillInsFor.isEmpty())
    }

    @Test
    fun `getLoadingView serves the same loading view`() {
        val factory = FakeFactory(context, snapshot = emptyList())

        assertEquals(R.layout.seerr_recommendations_item, factory.getLoadingView().layoutId)
    }

    @Test
    fun `an empty snapshot skips the poster preload entirely`() {
        val factory = FakeFactory(context, snapshot = emptyList())

        factory.onDataSetChanged()

        assertEquals(listOf("snapshot"), factory.events)
        assertEquals(0, factory.count)
    }

    @Test
    fun `onDestroy drops the state so the factory renders loading`() {
        val factory = FakeFactory(context, snapshot = listOf("aa"))
        factory.onDataSetChanged()
        assertEquals(1, factory.count)

        factory.onDestroy()

        assertEquals(0, factory.count)
        assertEquals(R.layout.seerr_recommendations_item, factory.getViewAt(0).layoutId)
        assertNull(factory.internalDims())
        assertTrue(factory.internalPosterCache().isEmpty())
    }

    @Test
    fun `create is a no-op before any data arrives`() {
        val factory = FakeFactory(context, snapshot = listOf("aa"))

        factory.onCreate()

        assertEquals(0, factory.count)
        assertTrue(factory.events.isEmpty())
        assertTrue(factory.hasStableIds())
    }
}
