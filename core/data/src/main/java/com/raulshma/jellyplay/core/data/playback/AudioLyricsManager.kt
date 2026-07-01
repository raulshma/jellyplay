package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioLyricsManager @Inject constructor(
    private val lyricsRepository: LyricsRepository,
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

    /**
     * Lead/lag applied to the playback position when computing the active
     * lyric line. A positive value makes lyrics advance earlier (useful when
     * an LRCLIB match lags behind the audio). Defaults to [DEFAULT_OFFSET_MS]
     * to preserve the historic fixed lead; adjustable per-item via
     * [setLyricsOffset].
     */
    private val _lyricsOffsetMs = MutableStateFlow(DEFAULT_OFFSET_MS)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    /**
     * Per-item offset memory (session-scoped). The current item id is
     * captured in [fetchLyrics]/[applyLyrics] so the offset is restored when
     * the user returns to a previously-adjusted track.
     */
    private val perItemOffsets = mutableMapOf<String, Long>()
    private var currentItemId: String? = null

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    fun fetchLyrics(
        itemId: String,
        artistName: String?,
        trackName: String?,
        durationSec: Double?,
    ) {
        currentItemId = itemId
        restoreOffsetForItem(itemId)
        scope.launch {
            _isFetchingLyrics.value = true
            lyricsRepository.getLyricsWithFallback(itemId, artistName, trackName, durationSec)
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
            val result = lyricsRepository.searchLyrics(query)
            callback(result)
        }
    }

    fun applyLyrics(lrcLibId: Long, currentItemId: String?) {
        val itemId = currentItemId ?: return
        this.currentItemId = itemId
        restoreOffsetForItem(itemId)
        scope.launch {
            lyricsRepository.getLyricsById(lrcLibId, itemId)
                .onSuccess {
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
        }
    }

    /**
     * Sets the lyrics offset for the current item, clamped to
     * [MIN_OFFSET_MS]..[MAX_OFFSET_MS]. The value is remembered per-item for
     * the session so switching tracks and back restores the adjustment.
     */
    fun setLyricsOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        _lyricsOffsetMs.value = clamped
        currentItemId?.let { perItemOffsets[it] = clamped }
    }

    private fun restoreOffsetForItem(itemId: String) {
        _lyricsOffsetMs.value = perItemOffsets[itemId] ?: DEFAULT_OFFSET_MS
    }

    fun updateCurrentLyricIndex(positionMs: Long) {
        if (_lyrics.value.isNotEmpty()) {
            _currentLyricIndex.value = findCurrentLyricLine(
                _lyrics.value, positionMs + _lyricsOffsetMs.value
            )
        }
    }

    fun reset() {
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        _lyricsSource.value = LyricsSource.UNKNOWN
        _isFetchingLyrics.value = false
        _lyricsOffsetMs.value = DEFAULT_OFFSET_MS
        currentItemId = null
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

    companion object {
        /** Historic fixed lead baked into `updateCurrentLyricIndex`. */
        const val DEFAULT_OFFSET_MS = 300L
        const val MIN_OFFSET_MS = -500L
        const val MAX_OFFSET_MS = 500L
    }
}
