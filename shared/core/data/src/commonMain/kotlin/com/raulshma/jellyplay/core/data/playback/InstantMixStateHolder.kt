package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Mix-start seam outcome, normalized so commonMain stays free of the
 * jvmShared [AudioQueueFacade] types: each ViewModel adapts its facade call to
 * this shape at the call site (the guard/navigation-drift veto stays inside the
 * adapter, where the live item id lives).
 */
sealed interface InstantMixOutcome {
    /** Mix started; [firstTrackId] carries the queue head for one-shot navigation. */
    data class Started(val firstTrackId: String?) : InstantMixOutcome

    /** The server returned no mix for the seed. */
    data object EmptyMix : InstantMixOutcome

    /** A navigation-drift guard vetoed playback — silent by design. */
    data object Suppressed : InstantMixOutcome

    /** The mix fetch/queue build threw. */
    data class Failed(val cause: Throwable) : InstantMixOutcome
}

/** Consumer-renderable mix failure, already message-shaped. */
sealed interface InstantMixError {
    data object EmptyMix : InstantMixError

    /**
     * [message] mirrors the underlying cause's message and is null when the
     * cause carried none — consumers own the fallback text.
     */
    data class Failed(val message: String?) : InstantMixError
}

/** The holder's single observable snapshot; individual flows stay private. */
data class InstantMixState(
    val isStarting: Boolean = false,
    val firstTrackId: String? = null,
    val error: InstantMixError? = null,
)

/**
 * Deep module for the instant-mix choreography shared by the album, artist,
 * and media-detail screens (SeerrRequestStateHolder shape): ONE [state]
 * snapshot plus the [start] command that owns the whole isStartingMix flag,
 * first-track one-shot, and outcome → error mapping. Consumers used to
 * hand-copy this as six parallel composeStates + a `when` per screen — every
 * new mix behaviour forced all three re-writes; here it is one fold.
 *
 * [startMix] is the mix-starting dependency as a constructor seam (the
 * AudioQueueFacade call each ViewModel already made), so commonMain stays
 * pure; error TEXT stays consumer-side — callers map [InstantMixError] to
 * their localized message type (a null [InstantMixError.Failed.message]
 * means the cause carried none). [clearError] lets message-based consumers
 * re-arm after surfacing an error (StateFlow equality would otherwise swallow
 * a repeated identical failure).
 */
class InstantMixStateHolder(
    private val scope: CoroutineScope,
    private val startMix: suspend (seedItemId: String, fallbackName: String?) -> InstantMixOutcome,
) {
    private val _state = MutableStateFlow(InstantMixState())
    val state: StateFlow<InstantMixState> = _state.asStateFlow()

    /**
     * Starts a mix seeded off [seedItemId]: raises the isStarting flag,
     * clears any prior error, runs the [startMix] seam, then folds the
     * outcome — Started sets the first-track one-shot, EmptyMix/Failed land
     * on [InstantMixState.error], Suppressed stays silent. The flag is
     * always lowered once the seam returns or throws, including on failure
     * outcomes (the `finally` owns it; the fold never touches it).
     */
    fun start(seedItemId: String, fallbackName: String?) {
        scope.launch {
            _state.update { it.copy(isStarting = true, error = null) }
            try {
                when (val outcome = startMix(seedItemId, fallbackName)) {
                    is InstantMixOutcome.Started ->
                        _state.update { it.copy(firstTrackId = outcome.firstTrackId) }
                    InstantMixOutcome.EmptyMix ->
                        _state.update { it.copy(error = InstantMixError.EmptyMix) }
                    InstantMixOutcome.Suppressed -> Unit
                    is InstantMixOutcome.Failed ->
                        _state.update {
                            it.copy(error = InstantMixError.Failed(outcome.cause.message))
                        }
                }
            } finally {
                _state.update { it.copy(isStarting = false) }
            }
        }
    }

    /** Consume the first-track navigation one-shot (screen navigated). */
    fun consumeStartedEvent() {
        _state.update { it.copy(firstTrackId = null) }
    }

    /** Clears the surfaced error so an identical repeat failure re-fires. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
