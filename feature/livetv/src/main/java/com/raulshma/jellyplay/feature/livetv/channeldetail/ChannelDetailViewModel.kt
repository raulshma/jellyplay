package com.raulshma.jellyplay.feature.livetv.channeldetail

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(ChannelDetailUiState())
    val uiState: StateFlow<ChannelDetailUiState> = _uiState.flow

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
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load programs") }
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
