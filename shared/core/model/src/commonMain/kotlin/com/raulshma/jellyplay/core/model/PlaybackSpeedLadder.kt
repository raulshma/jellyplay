package com.raulshma.jellyplay.core.model

import kotlin.math.roundToInt

/**
 * The ONE home of the playback-speed ladder: which speeds exist, the
 * continuous slider's geometry over them, and their "1x"/"1.5x" label
 * formatting. Every player surface reads this object — both speed-picker
 * sheets' chip rungs, both speed sliders (touch + D-pad), the video
 * [PlayerAction.CYCLE_SPEED] key ladder and the settings default-speed
 * pickers — so extending the ladder edits this file alone (commit
 * e404b42be's 3x/5x rung addition spread over nine files; this object is
 * the collapse of that spread).
 *
 * [range] and [fullRungs] are TWO deliberate concepts, not one: the
 * continuous slider spans the whole [range] while the chip sheets offer
 * only [fullRungs] — 4.0x stays slider-only to keep the phone chip row
 * legible (commit e404b42be's message), so a rung list derived from the
 * range (or a range clamped to the rungs) would both be wrong. The slider
 * is quantized to [stepSize]; [stepCount] is the Compose slider `steps`
 * value — the snap points BETWEEN the endpoints — derived from
 * [range]/[stepSize] rather than restated beside them.
 *
 * [audioSettingsRungs] is the audio settings screen's default-speed picker
 * subset — a deliberate product choice, not a stale copy (commit e404b42be
 * added 3.0x/5.0x there without unifying): 0.25x and 1.75x stay off that
 * picker. The audio PLAYER's sheet serves [fullRungs] like the video one.
 */
object PlaybackSpeedLadder {
    /** Continuous slider span: [stepSize]-quantized from 0.25x to 5.0x. */
    val range: ClosedFloatingPointRange<Float> = 0.25f..5.0f

    /** Slider notch width — also the D-pad step on the same slider. */
    const val stepSize: Float = 0.05f

    /**
     * Compose slider `steps`: the snap points between the [range] endpoints,
     * derived from [range]/[stepSize] — the hand `94` was the same
     * `((5.0 - 0.25) / 0.05) - 1` carried as a comment. [roundToInt] absorbs
     * the float-division fuzz of the non-representable 0.05f.
     */
    val stepCount: Int =
        ((range.endInclusive - range.start) / stepSize).roundToInt() - 1

    /**
     * The full chip ladder — the rungs both player speed sheets offer (and
     * the video sheet's [PlayerAction.CYCLE_SPEED] ladder: next rung up,
     * wrapping to 1x, so a rebound key steps the same speeds the sheet
     * shows). Also the video default-speed settings picker's options.
     */
    val fullRungs: List<Float> =
        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 5.0f)

    /**
     * The audio settings screen's deliberate default-speed subset —
     * [fullRungs] minus 0.25x and 1.75x (see the object KDoc).
     */
    val audioSettingsRungs: List<Float> =
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f)

    /**
     * The multiplier row label ("1x", "1.5x"): the `if (speed == 1.0f) "1x"
     * else "${speed}x"` the settings rows hand-rolled, moved verbatim —
     * 1.0f spells "1x", every other speed renders its float as-is ("5.0x").
     */
    fun format(speed: Float): String = if (speed == 1.0f) "1x" else "${speed}x"
}
