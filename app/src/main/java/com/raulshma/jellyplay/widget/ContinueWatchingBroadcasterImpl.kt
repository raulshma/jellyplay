package com.raulshma.jellyplay.widget

import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster

/**
 * Sends an explicit-component broadcast to [ContinueWatchingWidget] so it
 * re-reads its RemoteViewsService data.
 *
 * Explicit-component (rather than implicit) because implicit broadcasts to
 * manifest-registered receivers are blocked on Android O+, and the widget's
 * intent-filter only carries APPWIDGET_UPDATE, so we target the receiver class
 * directly to guarantee delivery in-process.
 *
 * Action string + receiver class name are owned here, not by the home ViewModel.
 */
class ContinueWatchingBroadcasterImpl (
    private val context: Context,
) : ContinueWatchingBroadcaster {

    override fun refreshContinueWatching() {
        val intent = Intent(ContinueWatchingWidget.ACTION_REFRESH).apply {
            setClassName(context.packageName, ContinueWatchingWidget::class.java.name)
        }
        context.sendBroadcast(intent)
    }
}
