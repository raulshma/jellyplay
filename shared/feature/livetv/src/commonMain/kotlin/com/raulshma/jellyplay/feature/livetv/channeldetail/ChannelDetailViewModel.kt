package com.raulshma.jellyplay.feature.livetv.channeldetail

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
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
    // Each schedules/cancels a timer then re-fetches today's program window so
    // the Record ↔ Cancel button state follows the server's timer-state, and
    // emits a one-shot message via [messageChannel] (matches the Programs tab).

    fun recordProgram(program: LiveTvProgram) {
        launch {
            mediaRepository.createTimer(program.id)
                .onSuccess {
                    messageChannel.trySend(LiveTvUserMessage.RecordSuccess)
                    refreshPrograms(_uiState.value.channelId)
                }
                .onFailure { e ->
                    messageChannel.trySend(LiveTvUserMessage.Raw(e.message ?: "Failed to set recording"))
                }
        }
    }

    fun recordSeries(program: LiveTvProgram) {
        launch {
            mediaRepository.createSeriesTimer(program.id)
                .onSuccess {
                    messageChannel.trySend(LiveTvUserMessage.RecordSuccess)
                    refreshPrograms(_uiState.value.channelId)
                }
                .onFailure { e ->
                    messageChannel.trySend(LiveTvUserMessage.Raw(e.message ?: "Failed to set recording"))
                }
        }
    }

    fun cancelTimer(program: LiveTvProgram) {
        val timerId = program.timerId ?: return
        launch {
            mediaRepository.cancelTimer(timerId)
                .onSuccess {
                    messageChannel.trySend(LiveTvUserMessage.RecordCanceled)
                    refreshPrograms(_uiState.value.channelId)
                }
                .onFailure { e ->
                    messageChannel.trySend(LiveTvUserMessage.Raw(e.message ?: "Failed to cancel recording"))
                }
        }
    }

    fun cancelSeries(program: LiveTvProgram) {
        val seriesTimerId = program.seriesTimerId ?: return
        launch {
            mediaRepository.cancelSeriesTimer(seriesTimerId)
                .onSuccess {
                    messageChannel.trySend(LiveTvUserMessage.RecordCanceled)
                    refreshPrograms(_uiState.value.channelId)
                }
                .onFailure { e ->
                    messageChannel.trySend(LiveTvUserMessage.Raw(e.message ?: "Failed to cancel recording"))
                }
        }
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
