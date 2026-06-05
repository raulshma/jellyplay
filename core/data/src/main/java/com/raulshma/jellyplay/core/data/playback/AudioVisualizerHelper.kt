package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Visualizer
import android.util.Log
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioVisualizerHelper {

    private var visualizer: Visualizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val _fftData = MutableStateFlow(ByteArray(0))
    val fftData: StateFlow<ByteArray> = _fftData.asStateFlow()

    private val _waveformData = MutableStateFlow(ByteArray(0))
    val waveformData: StateFlow<ByteArray> = _waveformData.asStateFlow()

    private var lastFft: ByteArray? = null
    private var lastWaveform: ByteArray? = null

    var isEnabled: Boolean = false
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && visualizer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        visualizer = try {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (waveform != null && !waveform.contentEquals(lastWaveform)) {
                                lastWaveform = waveform
                                _waveformData.value = waveform
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (fft != null && !fft.contentEquals(lastFft)) {
                                lastFft = fft
                                _fftData.value = fft
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 4,
                    true,
                    true,
                )
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Visualizer", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        visualizer?.enabled = enabled
        if (!enabled) {
            _fftData.value = ByteArray(0)
            _waveformData.value = ByteArray(0)
        }
    }

    fun detach() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        _fftData.value = ByteArray(0)
        _waveformData.value = ByteArray(0)
    }

    companion object {
        private const val TAG = "AudioVisualizerHelper"
    }
}
