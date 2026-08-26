package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Resolves the per-item / per-series playback-language preference to apply for
 * the currently-playing item, with item scope winning over series scope.
 *
 * `TrackSelectionHelper.updateTracksFromEngine()` runs on a synchronous hot
 * path and cannot itself suspend to query the DAO. This helper keeps a
 * cached resolution in a [StateFlow] that the hot path reads without
 * suspending, while a [refresh]/[clear] (launched onto [scope]) performs the
 * actual DAO reads. The cache is refreshed whenever a new item/series becomes
 * current or whenever a preference is saved/deleted by the user.
 */
internal class ItemPlaybackPreferenceResolver(
    private val repository: ItemPlaybackPreferenceRepository,
    private val getCurrentItemId: () -> String?,
    private val getCurrentSeriesId: () -> String?,
    private val scope: CoroutineScope,
) {
    private val _resolved = MutableStateFlow<ItemPlaybackPreference?>(null)

    /** The most recently resolved preference for the current item/series. */
    val resolved: StateFlow<ItemPlaybackPreference?> = _resolved.asStateFlow()

    /**
     * Re-reads the preference for the current item/series and updates
     * [resolved]. Item scope takes precedence; series scope is consulted only
     * when no item-scope rule (or no item id) exists.
     */
    fun refresh() {
        val itemId = getCurrentItemId()
        val seriesId = getCurrentSeriesId()
        scope.launch {
            _resolved.value = resolveNow(itemId, seriesId)
        }
    }

    /** Clears the cache — call when playback ends / the helper is reset. */
    fun clear() {
        _resolved.value = null
    }

    private suspend fun resolveNow(itemId: String?, seriesId: String?): ItemPlaybackPreference? {
        if (itemId != null) {
            repository.get(PlaybackPrefScope.ITEM, itemId)?.let { return it }
        }
        if (seriesId != null) {
            repository.get(PlaybackPrefScope.SERIES, seriesId)?.let { return it }
        }
        return null
    }
}
