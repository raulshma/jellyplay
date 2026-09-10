package com.raulshma.jellyplay.feature.livetv.programs

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.LIVE_TV_STALENESS_INTERVAL_MS
import com.raulshma.jellyplay.feature.livetv.LiveTvLoad
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordDialogState
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A titled horizontal row of programs (mirrors jellyfin-web `getProgramSections`). */
@Immutable
data class ProgramRow(
    /** Stable per-section id ("on-now", "shows", …) — the LazyColumn key. */
    val id: String,
    val title: String,
    val programs: List<LiveTvProgram>,
)

@Immutable
data class ProgramsUiState(
    val rows: List<ProgramRow> = emptyList(),
    val isLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val recordDialog: RecordDialogState = RecordDialogState.Idle,
)

/**
 * Drives the Programs tab — six category rows fetched in parallel from
 * `GET /LiveTv/Programs/Recommended`, exactly matching jellyfin-web's
 * `livetvsuggested.js` reload(): On Now (isAiring), then Shows/Movies/Sports/
 * Kids/News (hasAired=false + the category flag). Implements the web app's
 * 5-minute full-render throttle: a re-entry within
 * [LIVE_TV_STALENESS_INTERVAL_MS] only refreshes the "On Now" row.
 */
class ProgramsViewModel(
    private val mediaRepository: LiveTvRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val timeSource: TimeSource,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(ProgramsUiState())
    val uiState get() = _uiState.flow

    /**
     * The shared record choreography ([RecordActions]); this tab's adaptation
     * maps the outcome onto the dialog in [ProgramsUiState] and re-runs [load]
     * after a success so timer badges reflect the server state.
     */
    private val recordActions = RecordActions(mediaRepository, scope) { outcome ->
        _uiState.update {
            it.copy(
                recordDialog = when (outcome) {
                    is RecordOutcome.Requesting -> RecordDialogState.Requesting
                    is RecordOutcome.Success -> RecordDialogState.Success()
                    is RecordOutcome.Error -> RecordDialogState.Error(outcome.message ?: "Failed")
                    RecordOutcome.Idle -> it.recordDialog
                },
            )
        }
        if (outcome is RecordOutcome.Success) load()
    }

    @Volatile private var lastFullRender: Long = 0L

    init { load() }

    fun load() {
        launch {
            val now = timeSource.nowEpochMillis()
            val fullRender = now - lastFullRender > LIVE_TV_STALENESS_INTERVAL_MS
            LiveTvLoad.load(
                start = {
                    // The load flavour picks the flag: a full render raises
                    // isLoading, a throttled re-entry raises refreshing — both
                    // clear the error.
                    if (fullRender) {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    } else {
                        _uiState.update { it.copy(refreshing = true, error = null) }
                    }
                },
                fetch = {
                    // Sanctioned cancellation-aware wrapper (the old body's
                    // catch (e: Exception) also swallowed
                    // CancellationException): cancellation now propagates
                    // instead of settling into the error arm.
                    runCatchingRethrowingCancellation {
                        coroutineScope {
                            val onNow = async {
                                mediaRepository.getRecommendedPrograms(
                                    ProgramFilters(isAiring = true),
                                    limit = ON_NOW_LIMIT,
                                )
                            }
                            val rows = if (fullRender) {
                                val shows = async {
                                    mediaRepository.getRecommendedPrograms(
                                        ProgramFilters(hasAired = false, isSeries = true, isMovie = false, isNews = false, isKids = false, isSports = false), limit = ROW_LIMIT)
                                }
                                val movies = async {
                                    mediaRepository.getRecommendedPrograms(
                                        ProgramFilters(hasAired = false, isMovie = true), limit = ROW_LIMIT)
                                }
                                val sports = async {
                                    mediaRepository.getRecommendedPrograms(
                                        ProgramFilters(hasAired = false, isSports = true), limit = ROW_LIMIT)
                                }
                                val kids = async {
                                    mediaRepository.getRecommendedPrograms(
                                        ProgramFilters(hasAired = false, isKids = true), limit = ROW_LIMIT)
                                }
                                val news = async {
                                    mediaRepository.getRecommendedPrograms(
                                        ProgramFilters(hasAired = false, isNews = true), limit = ROW_LIMIT)
                                }
                                buildRows(
                                    onNow = onNow.await().getOrDefault(emptyList()),
                                    shows = shows.await().getOrDefault(emptyList()),
                                    movies = movies.await().getOrDefault(emptyList()),
                                    sports = sports.await().getOrDefault(emptyList()),
                                    kids = kids.await().getOrDefault(emptyList()),
                                    news = news.await().getOrDefault(emptyList()),
                                )
                            } else {
                                // Throttled path: only refresh On Now, keep the rest.
                                val existing = _uiState.value.rows
                                val refreshedOnNow = onNow.await().getOrDefault(emptyList())
                                if (existing.isEmpty()) {
                                    listOf(ProgramRow("on-now", "On Now", refreshedOnNow))
                                } else {
                                    existing.toMutableList().also { it[0] = ProgramRow("on-now", "On Now", refreshedOnNow) }
                                }
                            }
                            lastFullRender = now
                            rows
                        }
                    }
                },
                onSuccess = { rows ->
                    _uiState.update { it.copy(rows = rows, isLoading = false, refreshing = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false, refreshing = false) }
                },
            )
        }
    }

    private fun buildRows(
        onNow: List<LiveTvProgram>,
        shows: List<LiveTvProgram>,
        movies: List<LiveTvProgram>,
        sports: List<LiveTvProgram>,
        kids: List<LiveTvProgram>,
        news: List<LiveTvProgram>,
    ): List<ProgramRow> = buildList {
        if (onNow.isNotEmpty()) add(ProgramRow("on-now", "On Now", onNow))
        if (shows.isNotEmpty()) add(ProgramRow("shows", "Shows", shows))
        if (movies.isNotEmpty()) add(ProgramRow("movies", "Movies", movies))
        if (sports.isNotEmpty()) add(ProgramRow("sports", "Sports", sports))
        if (kids.isNotEmpty()) add(ProgramRow("kids", "Kids", kids))
        if (news.isNotEmpty()) add(ProgramRow("news", "News", news))
    }

    fun getImageUrl(itemId: String, imageTag: String?): String =
        imageUrlProvider.getImageUrlOrNull(itemId, imageTag)

    // ── Recording flow (shared RecordDialog, choreography in RecordActions) ──
    fun requestRecord(program: LiveTvProgram) {
        _uiState.update { it.copy(recordDialog = RecordDialogState.AwaitingChoice(program)) }
    }

    fun recordOnce(program: LiveTvProgram) = recordActions.recordOnce(program)

    fun recordSeries(program: LiveTvProgram) = recordActions.recordSeries(program)

    fun cancelTimer(program: LiveTvProgram) {
        if (!recordActions.cancelTimer(program)) dismissRecordDialog()
    }

    fun cancelSeries(program: LiveTvProgram) {
        if (!recordActions.cancelSeries(program)) dismissRecordDialog()
    }

    fun dismissRecordDialog() {
        _uiState.update { it.copy(recordDialog = RecordDialogState.Idle) }
    }

    companion object {
        private const val ON_NOW_LIMIT = 24
        private const val ROW_LIMIT = 12
    }
}
