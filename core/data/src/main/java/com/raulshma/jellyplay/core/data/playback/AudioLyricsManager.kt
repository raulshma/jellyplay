package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class AudioLyricsManager @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    private lateinit var scope: CoroutineScope

    private val _lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyrics: StateFlow<List<LyricsLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _lyricsSource = MutableStateFlow(LyricsSource.UNKNOWN)
    val lyricsSource: StateFlow<LyricsSource> = _lyricsSource.asStateFlow()

    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    fun fetchLyrics(
        itemId: String,
        artistName: String?,
        trackName: String?,
        durationSec: Double?,
    ) {
        scope.launch {
            _isFetchingLyrics.value = true
            mediaRepository.getLyricsWithFallback(itemId, artistName, trackName, durationSec)
                .onSuccess {
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
                .onFailure {
                    _lyrics.value = emptyList()
                    _lyricsSource.value = LyricsSource.UNKNOWN
                }
            _isFetchingLyrics.value = false
        }
    }

    fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        scope.launch {
            val result = mediaRepository.searchLyrics(query)
            callback(result)
        }
    }

    fun applyLyrics(lrcLibId: Long, currentItemId: String?) {
        val itemId = currentItemId ?: return
        scope.launch {
            mediaRepository.getLyricsById(lrcLibId, itemId)
                .onSuccess {
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
        }
    }

    fun updateCurrentLyricIndex(positionMs: Long) {
        if (_lyrics.value.isNotEmpty()) {
            _currentLyricIndex.value = findCurrentLyricLine(
                _lyrics.value, positionMs + 300L
            )
        }
    }

    fun reset() {
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        _lyricsSource.value = LyricsSource.UNKNOWN
        _isFetchingLyrics.value = false
    }

    private fun findCurrentLyricLine(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                lines[mid].timeMs <= positionMs -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return high.coerceAtLeast(-1)
    }
}
