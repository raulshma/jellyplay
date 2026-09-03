package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Pins the shared widget dimension resolution: orientation decides whether
 * the min or max option feeds width/height, non-positive reported values
 * clamp to the family defaults (280 wide, caller-supplied height), the
 * options bundle may be absent, and the recommendation grids' text-hiding
 * thresholds hold at exactly height<200 / width<180.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetMetricsTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun setOrientation(orientation: Int) {
        context.resources.configuration.orientation = orientation
    }

    private fun options(
        minWidth: Int = 0,
        minHeight: Int = 0,
        maxWidth: Int = 0,
        maxHeight: Int = 0,
    ): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight)
    }

    @Test
    fun `portrait uses min width and max height`() {
        setOrientation(Configuration.ORIENTATION_PORTRAIT)
        val dims = widgetDimensionsFromOptions(
            context,
            options(minWidth = 300, minHeight = 100, maxWidth = 400, maxHeight = 250),
            defaultHeight = 220,
        )!!

        assertEquals(300, dims.width)
        assertEquals(250, dims.height)
    }

    @Test
    fun `landscape uses max width and min height`() {
        setOrientation(Configuration.ORIENTATION_LANDSCAPE)
        val dims = widgetDimensionsFromOptions(
            context,
            options(minWidth = 300, minHeight = 100, maxWidth = 400, maxHeight = 250),
            defaultHeight = 220,
        )!!

        assertEquals(400, dims.width)
        assertEquals(100, dims.height)
    }

    @Test
    fun `non-positive values clamp to the shared defaults`() {
        setOrientation(Configuration.ORIENTATION_PORTRAIT)
        val dims = widgetDimensionsFromOptions(
            context,
            options(minWidth = 0, maxHeight = -5),
            defaultHeight = 110,
        )!!

        assertEquals(280, dims.width)
        assertEquals(110, dims.height)
    }

    @Test
    fun `null options bundle yields null dimensions`() {
        setOrientation(Configuration.ORIENTATION_PORTRAIT)
        assertNull(widgetDimensionsFromOptions(context, null, defaultHeight = 220))
    }

    @Test
    fun `refreshWidgetDimensions guards the invalid widget id`() {
        assertNull(
            refreshWidgetDimensions(
                context,
                AppWidgetManager.INVALID_APPWIDGET_ID,
                defaultHeight = 220,
            ),
        )
    }

    @Test
    fun `refreshWidgetDimensions falls through to the clamped defaults for an unconfigured id`() {
        // An id with no installed provider still returns an (empty) options
        // bundle from the shadow, so the non-positive clamp path must
        // produce the family defaults rather than null.
        setOrientation(Configuration.ORIENTATION_PORTRAIT)

        assertEquals(
            WidgetDimensions(280, 220),
            refreshWidgetDimensions(context, appWidgetId = 123, defaultHeight = 220),
        )
    }

    @Test
    fun `text hides strictly below 200 height or 180 width`() {
        // Strict inequalities: a cell of exactly (180, 200) still shows text.
        assertTrue(WidgetDimensions(180, 200).copy(width = 179).isTooSmallForText())
        assertTrue(WidgetDimensions(180, 200).copy(height = 199).isTooSmallForText())
        assertFalse(WidgetDimensions(180, 200).isTooSmallForText())
        assertFalse(WidgetDimensions(300, 250).isTooSmallForText())
    }
}
