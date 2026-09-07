package com.raulshma.jellyplay.widget.skeleton

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.widget.WidgetLayoutThresholds
import com.raulshma.jellyplay.widget.openAppPendingIntent
import com.raulshma.jellyplay.widget.widgetDimensionsFromOptions

/**
 * The recommendation grids' PendingIntent request codes — the per-widget
 * namespaces (7_400_0xx Library, 7_500_0xx Seerr) must stay disjoint because
 * the shared PendingIntent table is keyed by request code.
 */
internal data class GridWidgetRequestCodes(
    val header: Int,
    val refresh: Int,
    val item: Int,
)

/**
 * Everything that differs between the two recommendation grids'
 * `updateAppWidget`: layout, view ids, the refresh broadcast (action +
 * receiving provider), the remote-adapter service, and the request-code
 * namespace. The wiring order and intents are the skeleton's.
 */
internal data class GridWidgetUi(
    val layoutRes: Int,
    val headerViewId: Int,
    /** The header's text container — the header's open-app tap target. */
    val headerTextContainerViewId: Int,
    val subtitleViewId: Int,
    val refreshViewId: Int,
    val gridViewId: Int,
    val emptyViewId: Int,
    val refreshAction: String,
    val refreshBroadcastTarget: Class<out BroadcastReceiver>,
    val serviceClass: Class<out RemoteViewsService>,
    val requestCodes: GridWidgetRequestCodes,
)

/**
 * The `updateAppWidget` template the Library and Seerr recommendation grids
 * used to repeat byte-for-byte (differing only in the [GridWidgetUi]
 * parameterization): subtitle text, the 130/180dp header ladder, the
 * Seerr-only empty-state text via [bindExtraContent], open-app/refresh/
 * template PendingIntents, and the remote-adapter + empty-view wiring.
 * Call order matches the originals exactly.
 *
 * @param subtitleText     the current source label for the header subtitle
 *                         (evaluated by the caller, which may read the store
 *                         synchronously).
 * @param bindExtraContent extra view binding between the header ladder and
 *                         the PendingIntents — the Seerr grid's empty-state
 *                         texts; the Library grid passes none.
 */
internal fun updateRecommendationGridWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    subtitleText: String,
    ui: GridWidgetUi,
    bindExtraContent: (RemoteViews) -> Unit = {},
) {
    val views = RemoteViews(context.packageName, ui.layoutRes)
    views.setTextViewText(ui.subtitleViewId, subtitleText)

    // Apply responsive rules
    val dims = widgetDimensionsFromOptions(
        context,
        appWidgetManager.getAppWidgetOptions(appWidgetId),
        WidgetLayoutThresholds.RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP,
    )
    if (dims != null) {
        val ladder = recommendationGridHeaderVisibility(dims.height)
        views.setViewVisibility(ui.headerViewId, ladder.showHeader.toViewVisibility())
        views.setViewVisibility(ui.subtitleViewId, ladder.showSubtitle.toViewVisibility())
        views.setViewVisibility(ui.refreshViewId, ladder.showRefresh.toViewVisibility())
    }

    bindExtraContent(views)

    val openApp = openAppPendingIntent(context, ui.requestCodes.header)
    views.setOnClickPendingIntent(ui.headerTextContainerViewId, openApp)
    views.setOnClickPendingIntent(ui.emptyViewId, openApp)

    val refreshIntent = Intent(context, ui.refreshBroadcastTarget).apply {
        action = ui.refreshAction
    }
    val refreshPending = PendingIntent.getBroadcast(
        context,
        ui.requestCodes.refresh,
        refreshIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(ui.refreshViewId, refreshPending)

    val templateIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        addCategory(Intent.CATEGORY_DEFAULT)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val templatePending = PendingIntent.getActivity(
        context,
        ui.requestCodes.item,
        templateIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    views.setPendingIntentTemplate(ui.gridViewId, templatePending)

    val serviceIntent = Intent(context, ui.serviceClass).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    views.setRemoteAdapter(ui.gridViewId, serviceIntent)
    views.setEmptyView(ui.gridViewId, ui.emptyViewId)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/** Maps a ladder decision to the View constant the RemoteViews call needs. */
internal fun Boolean.toViewVisibility(): Int =
    if (this) View.VISIBLE else View.GONE
