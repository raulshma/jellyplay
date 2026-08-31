package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle

/**
 * Responsive dimensions derived from a widget's current app-widget options.
 *
 * Every home-screen widget in this package computes its effective `width`/`height`
 * from `AppWidgetManager.getAppWidgetOptions` the same way: pick min/max by
 * orientation, then clamp `<= 0` to sane defaults. The orientation selection and
 * the `width` default (280) are shared; only the `height` default differs per
 * widget family, so it is passed in.
 *
 * Extracted purely for de-duplication — the threshold logic that *consumes*
 * these dimensions stays at each call site because it differs per widget.
 */
data class WidgetDimensions(val width: Int, val height: Int)

/**
 * Resolve the effective pixel dimensions for [appWidgetId], or null if the
 * widget options bundle is unavailable.
 *
 * @param context     used only to read the current orientation.
 * @param appWidgetId the widget whose options should be read.
 * @param defaultHeight fallback height when the reported value is `<= 0`
 *                      (e.g. 220 for continue-watching, 250 for recommendation
 *                      grids, 110 for the now-playing widget).
 */
fun computeWidgetDimensions(
    context: Context,
    appWidgetId: Int,
    defaultHeight: Int,
): WidgetDimensions? {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId) ?: return null
    return widgetDimensionsFromOptions(context, options, defaultHeight)
}

/**
 * Per-refresh dimension resolution for the list/grid `RemoteViewsFactory`
 * implementations: factories cache this in `onDataSetChanged` (the provider
 * also calls `notifyAppWidgetViewDataChanged` from `onAppWidgetOptionsChanged`,
 * so a resize re-runs the refresh) instead of paying the options IPC per
 * `getViewAt` bind.
 */
fun refreshWidgetDimensions(
    context: Context,
    appWidgetId: Int,
    defaultHeight: Int,
): WidgetDimensions? =
    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        computeWidgetDimensions(context, appWidgetId, defaultHeight)
    } else {
        null
    }

/**
 * Shared responsive rule of the recommendation grids: below these cell sizes
 * the title/subtitle container is hidden entirely.
 */
fun WidgetDimensions.isTooSmallForText(): Boolean = height < 200 || width < 180

/**
 * Same dimension computation as [computeWidgetDimensions] but for callers that
 * already hold the options [Bundle] and an [AppWidgetManager] instance (e.g. an
 * `AppWidgetProvider.onUpdate` that receives the manager directly).
 */
fun widgetDimensionsFromOptions(
    context: Context,
    options: Bundle?,
    defaultHeight: Int,
): WidgetDimensions? {
    if (options == null) return null
    val config = context.resources.configuration
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

    var width = if (isLandscape) maxWidth else minWidth
    var height = if (isLandscape) minHeight else maxHeight

    if (width <= 0) width = 280
    if (height <= 0) height = defaultHeight

    return WidgetDimensions(width, height)
}
