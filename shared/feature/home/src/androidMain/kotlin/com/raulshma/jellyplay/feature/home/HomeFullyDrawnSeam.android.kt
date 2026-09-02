package com.raulshma.jellyplay.feature.home

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android actual of the home fully-drawn seam: captures the host Activity
 * and forwards the report to its [android.app.Activity.reportFullyDrawn] —
 * the legacy `LocalActivity.current?.reportFullyDrawn()` behavior verbatim
 * (null-activity tolerant: a missing activity simply drops the report).
 */
@Composable
internal actual fun rememberReportHomeFullyDrawn(): () -> Unit {
    val activity = LocalActivity.current
    return remember(activity) { { activity?.reportFullyDrawn() } }
}
