package com.raulshma.jellyplay.feature.livetv

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Per-tab badge counts for the Live TV host bar. A value of `0` means "no
 * badge"; a positive value renders a count pill, `-1` a plain dot.
 */
@Immutable
data class LiveTvBadges(
    val recordings: Int = 0,
    val activeRecordings: Int = 0,
    val upcoming: Int = 0,
    val series: Int = 0,
)

/**
 * Host-scoped VM that backs the tab badges. Fetches the small counts needed to
 * render Live TV's tab indicators (recordings, in-progress recordings,
 * upcoming timers, series timers) in parallel — mirroring jellyfin-web's home
 * Live TV pre-check. Cheap queries (each `enableTotalRecordCount` off where
 * possible), so re-running on refresh is fine.
 *
 * Each child tab owns its own `koinViewModel()` for its full dataset; this VM
 * only carries the lightweight counts the tab bar needs regardless of which
 * page is visible.
 */
class LiveTvOverviewViewModel(
    private val mediaRepository: LiveTvRepository,
) : JellyPlayViewModel() {

    private val _badges = stateFlow(LiveTvBadges())
    val badges get() = _badges.flow

    init { refresh() }

    fun refresh() {
        launch {
            coroutineScope {
                val recordings = async { mediaRepository.getRecordings(limit = 1).getOrDefault(emptyList()).size }
                val active = async { mediaRepository.getRecordings(isInProgress = true).getOrDefault(emptyList()).size }
                val upcoming = async { mediaRepository.getTimers(isActive = false, isScheduled = true).getOrDefault(emptyList()).size }
                val series = async { mediaRepository.getSeriesTimers().getOrDefault(emptyList()).size }
                val result = LiveTvBadges(
                    recordings = recordings.await(),
                    activeRecordings = active.await(),
                    upcoming = upcoming.await(),
                    series = series.await(),
                )
                _badges.set(result)
            }
        }
    }
}
