package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

internal class AudioEffectsController {
    private val dialogueBoost = DialogueBoostHelper()
    private val nightMode = NightModeHelper()
    private val equalizerHelper = EqualizerHelper()

    fun applyDialogueBoost(exoPlayer: ExoPlayer?, enabled: Boolean) {
        val player = exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        dialogueBoost.attach(audioSessionId)
        dialogueBoost.setEnabled(enabled)
    }

    fun applyNightMode(exoPlayer: ExoPlayer?, enabled: Boolean) {
        val player = exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        nightMode.attach(audioSessionId)
        nightMode.setEnabled(enabled)
    }

    fun setNightModeTargetGain(gain: Int) {
        nightMode.setTargetGain(gain)
    }

    fun applyEqualizer(exoPlayer: ExoPlayer?, enabled: Boolean) {
        val player = exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizerHelper.attach(audioSessionId)
        equalizerHelper.setEnabled(enabled)
    }

    fun setEqualizerSettings(settings: com.raulshma.jellyplay.core.model.EqualizerSettings) {
        equalizerHelper.setSettings(settings)
    }

    fun release() {
        dialogueBoost.detach()
        nightMode.detach()
        equalizerHelper.detach()
    }
}
