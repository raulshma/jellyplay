package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.PlayerInputMap

/**
 * The in-player input-binding quick toggle (issue #171), extracted from the
 * ViewModel per the [ControllerOwnershipTest] line-ceiling rule: reads the
 * live mapping, flips one binding's enabled flag, pushes both the uiState
 * write (the detectors resolve through it — the gate moves immediately) and
 * the whole-map persist through constructor lambdas.
 */
internal class InputBindingToggleController(
    private val getMap: () -> PlayerInputMap,
    private val updateMap: (PlayerInputMap) -> Unit,
    private val persist: (PlayerInputMap) -> Unit,
) {

    /**
     * Flips [bindingId]'s enabled flag. Unknown ids are no-ops (a stale
     * sheet row racing a mapping reset) — `withBindingEnabled` returns the
     * same instance and nothing is written or persisted.
     */
    fun setEnabled(bindingId: String, enabled: Boolean) {
        val current = getMap()
        val newMap = current.withBindingEnabled(bindingId, enabled)
        if (newMap === current) return
        updateMap(newMap)
        persist(newMap)
    }
}
