package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore

/**
 * Lifecycle callbacks for the active player engine.
 * Implemented by each engine (ExoPlayer, MPV, LibVLC) differently.
 */
interface PlayerLifecycleCallbacks {
    fun onActivityPause() {}
    fun onActivityResume() {}
}

/**
 * Centralized lifecycle manager for the active player engine.
 *
 * Bridges the gap between the single-activity Compose architecture and the
 * lifecycle-aware patterns used by Findroid's BasePlayerActivity. The Activity
 * calls lifecycle methods here, and this class delegates directly to the
 * active engine — no StateFlow indirection for pause/resume.
 */
@Singleton
class PlayerLifecycleManager @Inject constructor(
    private val preferencesStore: UserPreferencesStore
) {

    // ── PiP state ──

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _shouldAutoEnterPip = MutableStateFlow(false)
    val shouldAutoEnterPip: StateFlow<Boolean> = _shouldAutoEnterPip.asStateFlow()

    /**
     * Set to `true` when PiP is dismissed (user swiped it away).
     * Uses StateFlow so the value survives lifecycle STOPPED→STARTED transitions.
     * The UI layer must call [clearPipDismissed] after handling the event.
     */
    private val _pipDismissed = MutableStateFlow(false)
    val pipDismissed: StateFlow<Boolean> = _pipDismissed.asStateFlow()

    // ── Active engine lifecycle callbacks ──
    // Set by the ViewModel when an engine is created/released.
    // Allows direct lifecycle calls without going through StateFlow hops.

    @Volatile
    var activeCallbacks: PlayerLifecycleCallbacks? = null

    // ── PiP methods ──

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun requestAutoEnterPip(shouldEnter: Boolean) {
        _shouldAutoEnterPip.value = shouldEnter
    }

    fun notifyPipDismissed() {
        _pipDismissed.value = true
    }

    fun clearPipDismissed() {
        _pipDismissed.value = false
    }

    // ── Activity lifecycle ──
    // Called by MainActivity. Delegates directly to the engine's callbacks:
    // - ExoPlayer: no-op (PlayerView handles lifecycle)
    // - MPV/LibVLC: pause audio, save state

    /** Called from Activity.onPause() when NOT in PiP mode */
    fun onActivityPause() {
        if (!preferencesStore.preferences.value.backgroundVideoAudioEnabled) {
            activeCallbacks?.onActivityPause()
        }
    }

    /** Called from Activity.onResume() */
    fun onActivityResume() {
        activeCallbacks?.onActivityResume()
    }
}
