package com.raulshma.jellyplay.feature.player.live.engine

import com.raulshma.jellyplay.feature.player.video.engine.PlaybackVolumePolicy
import com.raulshma.jellyplay.feature.player.video.engine.VolumeCommandTemplates

/**
 * The live engine's ONE mute applier — the host of the shared
 * [VolumeCommandTemplates] mute template over the engine's native volume
 * handle. Replaces the ViewModel's former volume-zero hack: mute is REAL
 * state here, so it survives engine/track volume resets instead of silently
 * un-muting while the uiState still says muted.
 *
 * Live's divergence from the VOD engines is declared data on the template
 * call, carrying over the chip semantics the ViewModel used to apply by
 * hand:
 *  - no unmute floor ([unmuteRestoreFloor] = 0f) — the EXACT pre-mute level
 *    is restored (0.03 stays 0.03; VOD floors at
 *    [PlaybackVolumePolicy.UNMUTE_FLOOR]);
 *  - no system music-stream sync (live has no such seam — the template's
 *    sync arm stays a no-op);
 *  - an empty memory unmutes to LEAVE_UNCHANGED — never a fixed default
 *    (the chip's null arm: mute set externally / handle swapped);
 *  - no persistence: the memory lives and dies with the engine instance, so
 *    a fresh engine after release starts unmuted with an empty memory (the
 *    former stop()-clears-the-chip rule, absorbed by the engine lifecycle).
 *
 * The declared fix: because mute is state ON the engine, a channel zap /
 * transcode-fallback reload ([reassertAfterLoad]) re-asserts silence instead
 * of resurfacing at the old volume.
 *
 * Internal: consumed by [ExoLiveEngine] (the host) and this module's
 * jvmTest — not a stable API surface.
 */
internal class LiveMuteController(
    /** The native handle read (the pre-mute snapshot source); null = no handle. */
    private val readVolume: () -> Float?,
    /** The native handle write. */
    private val writeVolume: (Float) -> Unit,
) : VolumeCommandTemplates.NativeVolumeSurface {

    /** The engine's real mute state; the reload re-assert and uiState mirror read this. */
    var isMuted: Boolean = false
        private set

    /** The pre-mute remember/restore chip (the former VM-applied [LiveMuteMemory]). */
    private var muteMemory = LiveMuteMemory()

    /**
     * The mute command, riding the shared template. Unmute clears the chip
     * after the template read it, so a stale level never outlives its mute
     * (the chip's onUnmute rule).
     */
    fun setMuted(muted: Boolean) {
        VolumeCommandTemplates.setMuted(this, muted)
        if (!muted) muteMemory = muteMemory.onUnmute()
    }

    /**
     * A channel zap / fallback reload reuses the engine (and its volume
     * handle), so a muted engine re-asserts silence after the load instead
     * of resurfacing at the pre-mute level.
     */
    fun reassertAfterLoad() {
        if (isMuted) writeVolume(0f)
    }

    override val rememberedUnmuteLevel: Float
        get() = muteMemory.restorationVolume() ?: 0f

    /** Live restores the EXACT pre-mute level — no audible floor (VOD's default floors at 0.05). */
    override val unmuteRestoreFloor: Float get() = 0f

    override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
        if (!muted && muteMemory.restorationVolume() == null) {
            // The chip's null arm: no pre-mute level to restore — leave the
            // current volume untouched, never slam a fixed default.
            PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED
        } else {
            PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL
        }

    override fun applyNativeMuteFlag(muted: Boolean) {
        isMuted = muted
    }

    /** The pre-mute snapshot: remember the native level BEFORE the mute write. */
    override fun snapshotVolumeForMute() {
        readVolume()?.let { muteMemory = muteMemory.onMute(it) }
    }

    override fun applyNativeVolume(normalized: Float) {
        writeVolume(normalized)
    }

    /** Live has no system music stream — the template's sync arm stays a no-op. */
    override fun syncSystemStream(normalized: Float) {}

    /** Level-template seam (no live volume commands today); reads the native handle. */
    override fun readNativeVolume(): Float? = readVolume()

    /** Level-template seam (no live volume commands today); stores like the mute snapshot. */
    override fun rememberUnmuteVolume(level: Float) {
        muteMemory = LiveMuteMemory(level)
    }
}
