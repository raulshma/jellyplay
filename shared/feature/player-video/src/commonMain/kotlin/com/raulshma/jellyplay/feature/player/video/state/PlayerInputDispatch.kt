package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.DpadControl
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.WheelAxis
import com.raulshma.jellyplay.core.ui.tv.input.DpadKeyEvent

/** What one resolved wheel row means to the wheel tier (the former inline `when`). */
enum class WheelRowDisposition { Volume, Seek, Inert }

/**
 * One persisted input map's dispatch half (the GLOSSARY "Input dispatch
 * module"): map resolution ([PlayerInputPolicy]) + the resolve-then-execute
 * discrete-arm ladder + the per-surface registered arm sets, behind ONE
 * instance. The VOD gesture modifier and the TV key sink consume the same
 * instance (the surface configs are built from it and remembered, so the
 * arms compile once per instance change instead of per recomposition);
 * live TV registers its zap-family arms through it, which is how the
 * persisted map finally reaches live keys.
 *
 * Plain class, reader lambdas, zero Compose (the PlayerInputPolicy
 * convention): [getMap] and [executeAction] are invoked at EVENT time, so an
 * already-built dispatcher resolves the CURRENT map through the CURRENT
 * executor — a rebound binding applies to an instance built before the
 * rebound. Resolution defaults to the map alone; a surface with no executor
 * (live's zap family has no [PlayerAction] to name) arms its fixed default
 * behind the enable gate ([isControlEnabled]).
 *
 * Public because the LIVE player's screen builds its dispatcher over this
 * module (the PlayerWindowOps public-for-live precedent — feature-to-feature
 * edge player-live → player-video).
 */
class PlayerInputDispatcher(
    private val getMap: () -> PlayerInputMap,
    private val executeAction: (PlayerAction) -> Boolean,
) {
    /** Map resolution: pattern → bound action, null = disabled/unbound/NONE. */
    fun resolveAction(pattern: InputPattern): PlayerAction? =
        PlayerInputPolicy.resolveAction(getMap(), pattern)

    /** The shared discrete-action executor route (the rebound-arm funnel). */
    fun execute(action: PlayerAction): Boolean = executeAction(action)

    /** Wheel-row resolution (the exact-then-plain candidate ladder). */
    fun resolveWheelAction(axis: WheelAxis, isShiftPressed: Boolean): PlayerAction? =
        PlayerInputPolicy.resolveAction(getMap(), PlayerInputPolicy.wheelCandidates(axis, isShiftPressed))

    /**
     * Whether [control]'s row is enabled-and-bound (a disabled/NONE row is an
     * OFF: the arm attaches no semantics and consumes nothing). The enable
     * gate the fixed-default surfaces (live's zap family) resolve through.
     */
    fun isControlEnabled(control: DpadControl): Boolean =
        resolveAction(InputPattern.DPad(control)) != null

    /**
     * The ONE resolve-then-execute discrete arm every D-pad/tap tier shared
     * as a local helper (the former `discreteArm`/`touchArm` copies): the
     * DEFAULT action runs the surface's legacy arm verbatim, a rebound action
     * routes to [executeAction], an unbound/disabled row falls through
     * ([fallThroughOnUnbound] arms return false so the key drops; the touch
     * tiers no-op). Seek-family arms stay at their own sites — they carry
     * accumulate/commit event semantics, not this ladder.
     */
    fun discreteArm(
        dpadKey: DpadKeyEvent,
        control: DpadControl,
        defaultAction: PlayerAction,
        fallThroughOnUnbound: Boolean = true,
        legacy: () -> Boolean,
    ): Boolean {
        if (!dpadKey.isKeyDown) return false
        return when (val action = resolveAction(InputPattern.DPad(control))) {
            defaultAction -> legacy()
            null -> fallThroughOnUnbound
            else -> executeAction(action)
        }
    }

    /** The touch tier's arm: identical ladder, unbound rows no-op. */
    fun touchArm(pattern: InputPattern, defaultAction: PlayerAction, legacy: () -> Unit) {
        when (val action = resolveAction(pattern)) {
            defaultAction -> legacy()
            null -> {}
            else -> executeAction(action)
        }
    }

    /** The wheel tier's row disposition (the former inline volume/seek `when`). */
    fun wheelDisposition(axis: WheelAxis, isShiftPressed: Boolean): WheelRowDisposition =
        when (resolveWheelAction(axis, isShiftPressed)) {
            PlayerAction.SWIPE_VOLUME,
            PlayerAction.VOLUME_UP,
            PlayerAction.VOLUME_DOWN,
            -> WheelRowDisposition.Volume
            PlayerAction.SWIPE_SEEK,
            PlayerAction.SEEK_FORWARD,
            PlayerAction.SEEK_BACK,
            -> WheelRowDisposition.Seek
            else -> WheelRowDisposition.Inert
        }
}
