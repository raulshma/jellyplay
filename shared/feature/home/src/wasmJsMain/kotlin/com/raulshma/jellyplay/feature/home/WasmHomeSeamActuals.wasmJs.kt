package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable

/**
 * Web actual of the TTFD seam: a no-op reporter — the desktop actual's
 * shape. The browser has no `reportFullyDrawn()` metric host.
 */
@Composable
internal actual fun rememberReportHomeFullyDrawn(): () -> Unit = {}

/**
 * Web actual of the process-lifecycle seam: no registration, always null —
 * the desktop actual's shape. A browser tab has no app-foreground/background
 * process lifecycle (the web shell drives its own visibility handling).
 */
internal actual fun registerHomeProcessLifecycle(
    onStart: () -> Unit,
    onStop: () -> Unit,
): (() -> Unit)? = null
