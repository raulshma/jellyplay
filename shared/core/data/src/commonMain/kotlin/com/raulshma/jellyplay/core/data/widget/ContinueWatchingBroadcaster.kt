package com.raulshma.jellyplay.core.data.widget

/**
 * Notifies the Continue Watching home-screen widget that its data changed.
 *
 * Hides the explicit-component broadcast (action string + receiver class name)
 * from the home ViewModel so it no longer needs an Android [android.content.Context].
 * Mirrors the `TvWatchNextScheduler` DI-clean pattern: the interface lives in
 * `core/data`, the Android-aware implementation lives in `app`.
 *
 * The implementation is a no-op when no Continue Watching widget is bound.
 */
interface ContinueWatchingBroadcaster {
    fun refreshContinueWatching()
}
