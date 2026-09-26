package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.WidgetConfig
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import org.koin.mp.KoinPlatform

/**
 * The widget package's ONE Koin service-locator seam (the Hilt-removal
 * idiom): every widget-side dependency resolves through here instead of
 * each file carrying its own `private fun koinXxx() = KoinPlatform.getKoin()!!.get()`
 * copy or `by lazy { KoinPlatform.getKoin()!!.get() }` property. Callers
 * keep their own race policy — most wrap the read in try/catch so the
 * process-start race (Koin not up yet during a broadcast) degrades to the
 * caller's empty/fallback state, and the null-degrading resolvers that
 * predate this object ([com.raulshma.jellyplay.widget.skeleton.resolveWidgetDataStore],
 * [AppWidgetWorkerFactory]) stay as they are: a `!!`-throwing accessor
 * cannot express their no-op-on-unstarted-Koin contract.
 */
internal object WidgetKoin {

    /** Typed convenience for the most-repeated widget resolutions. */
    val widgetDataStore: WidgetDataStore get() = get()
    val seerrPreferencesStore: SeerrPreferencesStore get() = get()
    val widgetWorkScheduler: WidgetWorkScheduler get() = get()
    val audioPlaybackManager: AudioPlaybackManager get() = get()
    val nowPlayingWidgetUpdater: NowPlayingWidgetUpdater get() = get()

    /** The raw application-container resolution every site used to restate. */
    inline fun <reified T : Any> get(): T = KoinPlatform.getKoin()!!.get()
}

/**
 * The two recommendation grids' shared subtitle read — the former
 * Library/Seerr `readSourceLabel` twins, deduped beside
 * [WidgetPosterIdentity]. Reads the widget config through the sync snapshot
 * accessor (`onUpdate`/`onAppWidgetOptionsChanged` run on the main thread,
 * so a blocking DataStore read is not acceptable — and the store's sync
 * read is the memory-backed one) and degrades to [fallback] when the
 * process-start race makes the store unresolvable.
 */
internal fun readSourceLabel(
    appWidgetId: Int,
    sourceOf: (WidgetConfig) -> String,
    fallback: String,
): String = runCatching {
    sourceOf(WidgetKoin.widgetDataStore.getWidgetConfigForIdSync(appWidgetId))
}.getOrDefault(fallback)

/** The Library grid's subtitle: its configured source's display name. */
internal fun readLibrarySourceLabel(appWidgetId: Int): String = readSourceLabel(
    appWidgetId,
    sourceOf = { it.librarySource.displayName },
    fallback = LibraryRecommendationsSource.SIMILAR_TO_RECENT.displayName,
)

/** The Seerr grid's subtitle: its configured source's display name. */
internal fun readSeerrSourceLabel(appWidgetId: Int): String = readSourceLabel(
    appWidgetId,
    sourceOf = { it.seerrSource.displayName },
    fallback = SeerrWidgetSource.TRENDING.displayName,
)
