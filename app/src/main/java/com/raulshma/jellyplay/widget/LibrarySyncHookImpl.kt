package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler

/**
 * Wires the post-library-scan side effects that live in different modules:
 * the auto-download foreground drain (core/data) and the widget refresh (app).
 *
 * Each call is wrapped in its own try/catch so a failure in one (e.g. widget
 * cooldown rejected, WorkManager not yet initialised) never breaks the other or
 * the home refresh that triggered this hook. Both targets are themselves
 * no-op-safe when their pref is off or no widget is bound.
 */
class LibrarySyncHookImpl (
    private val autoDownloadScheduler: AutoDownloadScheduler,
    private val widgetWorkScheduler: WidgetWorkScheduler,
) : LibrarySyncHook {

    override suspend fun onLibraryScanComplete() {
        runCatching { autoDownloadScheduler.enqueueNow() }
        runCatching {
            widgetWorkScheduler.refreshLibraryNow()
            widgetWorkScheduler.refreshSeerrNow()
        }
    }
}
