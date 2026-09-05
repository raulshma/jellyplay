package com.raulshma.jellyplay.feature.livetv.channeldetail

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.components.RecordAction
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChannelDetailViewModel(
    private val mediaRepository: LiveTvRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(ChannelDetailUiState())
    val uiState: StateFlow<ChannelDetailUiState> = _uiState.flow

    /**
     * One-shot record/cancel feedback, screen-forward seam replacing the legacy
     * UserMessageBus ctor dep (the bus + UiText live in the Android-only
     * :core:ui shim and are not visible from commonMain). Same one-shot
     * semantics as the bus: buffered, single collector, never replayed —
     * [ChannelDetailScreen] resolves the resource text and forwards through
     * the LiveTvMessenger actual.
     */
    private val messageChannel = Channel<LiveTvUserMessage>(Channel.BUFFERED)
    val messages: Flow<LiveTvUserMessage> = messageChannel.receiveAsFlow()

    fun loadChannel(channelId: String, channelName: String) {
        _uiState.update { it.copy(channelId = channelId, channelName = channelName, isLoading = true, error = null) }
        launch {
            // 1. Channel meta (name/number/logo + currentProgram). limit matches
            //    ChannelsViewModel so channels beyond rank 50 are still found.
            mediaRepository.getLiveTvChannels(limit = 100, addCurrentProgram = true)
                .onSuccess { channels ->
                    val channel = channels.firstOrNull { it.id == channelId }
                    if (channel != null) {
                        _uiState.update {
                            it.copy(
                                channelName = channel.name.ifBlank { channelName },
                                channelNumber = channel.number,
                                channelLogoUrl = if (channel.imageTag != null) {
                                    imageUrlProvider.getImageUrl(channelId)
                                } else "",
                                channelBlurHash = channel.primaryBlurHash,
                                currentProgram = channel.currentProgram,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load channel") }
                    return@launch
                }

            // 2. Today's programs: now → end of day (local midnight).
            refreshPrograms(channelId, isInitialLoad = true)
        }
    }

    /**
     * Re-fetches today's program window for [channelId] and merges the result
     * into [_uiState]. Used by [loadChannel] (initial) and after every record /
     * cancel action so the timer-state on each [LiveTvProgram] (and the current
     * airing program driving the hero) reflects the latest server state.
     *
     * @param isInitialLoad when true, flips [ChannelDetailUiState.isLoading]
     *   off on completion and surfaces failures as [ChannelDetailUiState.error]
     *   (initial-load semantics). Subsequent refreshes silently update the list.
     */
    private suspend fun refreshPrograms(channelId: String, isInitialLoad: Boolean = false) {
        val now = OffsetDateTime.now()
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
        val startIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val endIso = endOfDay.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val nowInstant = Instant.now()

        mediaRepository.getLiveTvPrograms(channelId, startIso, endIso)
            .onSuccess { all ->
                val upcoming = all
                    .filter { p -> p.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isAfter(nowInstant) ?: true }
                    .sortedBy { p -> p.startDate ?: "" }
                _uiState.update { it.copy(programs = upcoming, isLoading = false) }
                // If the channel-meta currentProgram was null, resolve from the list.
                if (_uiState.value.currentProgram == null) {
                    val airing = upcoming.firstOrNull { p ->
                        val s = p.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        val e = p.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        (s == null || !s.isAfter(nowInstant)) && (e == null || e.isAfter(nowInstant))
                    }
                    if (airing != null) _uiState.update { it.copy(currentProgram = airing) }
                } else {
                    // Keep the hero in sync with the refreshed timer-state for the
                    // currently-airing program (Record ↔ Cancel button flips).
                    val airingId = _uiState.value.currentProgram?.id
                    val refreshedCurrent = airingId?.let { id -> upcoming.firstOrNull { it.id == id } }
                    if (refreshedCurrent != null) {
                        _uiState.update { it.copy(currentProgram = refreshedCurrent) }
                    }
                }
            }
            .onFailure { e ->
                if (isInitialLoad) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load programs") }
                }
            }
    }

    // ── Recording actions ──
    // The shared [RecordActions] choreography, adapted to this tab's feedback
    // surface: one-shot messages on [messageChannel] (success/canceled, Raw
    // failure with the legacy fallback literals) and a re-fetch of today's
    // program window after every successful action so the timer-state on each
    // [LiveTvProgram] (and the Record ↔ Cancel button on the hero) follows the
    // server.

    private val recordActions = RecordActions(mediaRepository, scope) { outcome ->
        when (outcome) {
            is RecordOutcome.Success -> {
                messageChannel.trySend(outcome.request.action.successMessage())
                launch { refreshPrograms(_uiState.value.channelId) }
            }
            is RecordOutcome.Error ->
                messageChannel.trySend(LiveTvUserMessage.Raw(outcome.message ?: outcome.request.action.failureFallback()))
            is RecordOutcome.Requesting, RecordOutcome.Idle -> Unit
        }
    }

    fun recordProgram(program: LiveTvProgram) {
        recordActions.recordOnce(program)
    }

    fun recordSeries(program: LiveTvProgram) {
        recordActions.recordSeries(program)
    }

    fun cancelTimer(program: LiveTvProgram) {
        recordActions.cancelTimer(program)
    }

    fun cancelSeries(program: LiveTvProgram) {
        recordActions.cancelSeries(program)
    }

    /**
     * Backdrop URL precedence: program image tag → program [LiveTvProgram.imageUrl]
     * → channel primary image. Note these are Primary/poster images (the only
     * image type programs/channels carry), not dedicated Backdrop images, so
     * the composable pairs the URL with [ChannelDetailUiState.channelBlurHash]
     * for a placeholder while it loads.
     */
    fun getProgramBackdropUrl(program: LiveTvProgram): String {
        val directUrl = program.imageUrl
        return when {
            program.imageTag != null -> imageUrlProvider.getImageUrl(program.id)
            directUrl != null -> directUrl
            else -> _uiState.value.channelLogoUrl
        }
    }
}

/** Timer creations announce success; cancels announce cancellation. */
private fun RecordAction.successMessage(): LiveTvUserMessage =
    if (this == RecordAction.RECORD_ONCE || this == RecordAction.RECORD_SERIES) {
        LiveTvUserMessage.RecordSuccess
    } else {
        LiveTvUserMessage.RecordCanceled
    }

/** The failure fallback literals, kept byte-identical from the legacy bus call sites. */
private fun RecordAction.failureFallback(): String =
    if (this == RecordAction.RECORD_ONCE || this == RecordAction.RECORD_SERIES) {
        "Failed to set recording"
    } else {
        "Failed to cancel recording"
    }
