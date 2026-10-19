package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.IdEnricher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Platform seam behind [NotificationSettingsViewModel]'s reschedule poke:
 * `NotificationScheduler` (WorkManager enqueue/cancel over the notification
 * store) lives in the Hilt-owned Android-only :core:notification, which this
 * module cannot reach. The Android composition root provides the wrapping impl
 * at the Koin edge (downloads-conveyor interop pattern); desktop has no
 * notification worker and binds a no-op. Suspend + no-args, matching the
 * `NotificationScheduler.scheduleOrUpdate()` signature exactly.
 */
fun interface NotificationSync {
    suspend fun scheduleOrUpdate()
}

class NotificationSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
    private val notificationSync: NotificationSync,
    private val imageUrlProvider: ImageUrlProvider,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Notification-screen slice — recomposes this screen only on notification-field writes. */
    val preferences: StateFlow<NotificationPreferences> = projections.notificationPreferences

    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    var libraryError by composeState<String?>(null)
        private set

    /**
     * Applies a transform to the notification preferences and reschedules the
     * notification worker against the updated config.
     */
    fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        editor.edit {
            notification.updateNotificationPreferences(transform)
            notificationSync.scheduleOrUpdate()
        }
    }

    // ── The per-series notification management sheet ─────────────────────

    /**
     * One "Series notifications" list row: the opted-in series' id plus the
     * hydrated metadata — the NextUpExcludedSeries shape. [item] stays null
     * while the detail fetch has not succeeded (in flight, offline, or the
     * series vanished from the server); the row still renders and still
     * removes by id.
     */
    @Immutable
    data class SeriesNotificationRow(
        val id: String,
        val item: MediaItem? = null,
    )

    /** The management sheet's snapshot: the rows plus the hydration-in-flight flag. */
    @Immutable
    data class SeriesNotificationsState(
        val series: List<SeriesNotificationRow> = emptyList(),
        val loading: Boolean = false,
    )

    private val _seriesNotifications = MutableStateFlow(SeriesNotificationsState())
    val seriesNotifications: StateFlow<SeriesNotificationsState> = _seriesNotifications.asStateFlow()

    /** The row poster's URL (primary image at the provider's default width). */
    fun seriesPosterUrl(itemId: String): String = imageUrlProvider.getImageUrl(itemId)

    /** Metadata resolved so far, id → item (removed ids prune it). */
    private val resolvedSeriesItems = mutableMapOf<String, MediaItem>()

    /** The id set of the latest store emission — the rows the publish renders. */
    private var publishedSeriesIds: Set<String> = emptySet()

    private var seriesHydrateJob: Job? = null

    /**
     * The hydration pass — the NextUpExcludedViewModel template:
     * one detail fetch at a time (the cached detail read), the failed-id
     * latch so a flaky fetch never retry-storms, and a cancel of the previous
     * pass so a rapid remove cannot leave a stray fetch writing into state.
     */
    private val seriesHydrater = IdEnricher<String, MediaItem>(
        scope = scope,
        concurrency = 1,
        retry = IdEnricher.Retry.NEVER,
        fetch = { id -> mediaRepository.getMediaDetail(id).getOrNull()?.item },
        merge = { id, item ->
            resolvedSeriesItems[id] = item
            publishSeries(publishedSeriesIds, loading = false)
        },
    )

    // Must stay below the state fields above: viewModelScope runs on
    // Dispatchers.Main.immediate, so the store's first emission can deliver
    // synchronously during construction — an earlier init would reach
    // reconcileSeries while resolvedSeriesItems/seriesHydrater are still null.
    init {
        loadLibraryFolders()
        observeSeriesNotifications()
    }

    /** Mirrors the store's per-series configs into the sheet's hydrated rows. */
    private fun observeSeriesNotifications() {
        launch {
            preferences
                .map { it.seriesConfigs.keys }
                .distinctUntilChanged()
                .collect { ids -> reconcileSeries(ids) }
        }
    }

    private fun reconcileSeries(ids: Set<String>) {
        resolvedSeriesItems.keys.retainAll(ids)
        seriesHydrater.pruneFailures(ids)
        publishedSeriesIds = ids
        publishSeries(ids, loading = false)
        val missing = ids.filter { it !in resolvedSeriesItems && !seriesHydrater.hasFailed(it) }
        if (missing.isEmpty()) return
        publishSeries(ids, loading = true)
        seriesHydrateJob?.cancel()
        seriesHydrateJob = seriesHydrater.enrich(
            ids = missing,
            onSettled = { publishSeries(publishedSeriesIds, loading = false) },
        )
    }

    private fun publishSeries(ids: Set<String>, loading: Boolean) {
        _seriesNotifications.value = SeriesNotificationsState(
            series = ids.map { SeriesNotificationRow(id = it, item = resolvedSeriesItems[it]) },
            loading = loading,
        )
    }

    /** Removes one series' opt-in (the row's bell-off action) — the store RMW command. */
    fun removeSeriesNotifications(seriesId: String) {
        editor.edit { notification.setSeriesNotificationEnabled(seriesId, enabled = false) }
    }

    /** Removes every series opt-in (the sheet's "Restore all"). */
    fun removeAllSeriesNotifications() {
        editor.edit { notification.clearSeriesNotificationConfigs() }
    }

    private fun loadLibraryFolders() {
        launch {
            libraryError = null
            mediaRepository.getLibraryFolders()
                .onSuccess { folders ->
                    _libraryFolders.value = folders.filter { it.collectionType != "music" }
                }
                .onFailure { error -> libraryError = error.message ?: error::class.simpleName }
        }
    }
}
