package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable

/**
 * TTFD (time-to-fully-drawn) reporting seam for the home screen (conveyor
 * transform: `LocalActivity` + `Activity.reportFullyDrawn()` is
 * Android-only). Returns a no-arg reporter the screen calls once its first
 * content frame has been composed and a frame has been drawn; the Android
 * actual forwards to the host Activity's `reportFullyDrawn()` (null-activity
 * tolerant: a missing activity drops the report), the JVM
 * actual is a no-op — desktop has no TTFD metric.
 */
@Composable
internal expect fun rememberReportHomeFullyDrawn(): () -> Unit
