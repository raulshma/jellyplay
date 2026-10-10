package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.PlayerInputMap

/**
 * The in-player input-binding quick toggle (issue #171), extracted from the
 * ViewModel per the [ControllerOwnershipTest] line-ceiling rule: reads the
 * live mapping, flips one binding's enabled flag, pushes the uiState write
 * (the detectors resolve through it — the gate moves immediately) and hands
 * the flip to [requestPersist]. Persistence goes through the store's
 * read-modify-write verb so a settings-editor write can never be clobbered
 * by a stale whole-map write — the RMW reads the stored blob inside the
 * edit; the mirror is only the synchronous UI gate.
 */
internal class InputBindingToggleController(
    private val getMap: () -> PlayerInputMap,
    private val updateMap: (PlayerInputMap) -> Unit,
    private val requestPersist: (bindingId: String, enabled: Boolean) -> Unit,
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
        requestPersist(bindingId, enabled)
    }
}
