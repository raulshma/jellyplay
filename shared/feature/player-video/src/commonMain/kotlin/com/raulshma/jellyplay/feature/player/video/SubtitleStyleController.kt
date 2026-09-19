package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounce window for the engine apply of a subtitle-delay change. A delay
 * change forces ExoPlayer/LibVLC to reload the media item to re-parse cues
 * through the offset wrapper (a rebuffer), so a burst of fine-tune nudges is
 * coalesced into a single reload. The in-memory value (and thus the overlay
 * readout) updates immediately; only the expensive engine push waits.
 */
internal const val SUBTITLE_DELAY_APPLY_DEBOUNCE_MS = 500L

/**
 * Owns the subtitle-style state choreography extracted from
 * [VideoPlayerViewModel]: the resolved in-memory [SubtitleStyle] (styling AND
 * the per-item subtitle-sync delay), the per-item dialogue-boost strength,
 * and the debounced engine re-sync a delay change triggers.
 *
 * Step 1 of the recorded two-step design (the `SubtitlePreviewController`
 * shape): the state stays in the VM's uiState mirrors — every write flows
 * through the narrow constructor lambdas below, NOT a raw uiState handle
 * (the god-count ratchet stays at its baseline; this class never references
 * [VideoPlayerUiState]). Step 2 (moving the slice into an owned flow) lands
 * later, if ever — the mirrors are what the engine-config builder and the
 * screen read today.
 *
 * Load-bearing invariants (pinned by [SubtitleStyleControllerTest]):
 *  - the in-memory [SubtitleStyle.offsetMs] is the per-item RESOLVED delay,
 *    never a global default: a global style write must persist the stored
 *    global offset, not the in-memory one, or one item's correction leaks
 *    into every other item ([setStyle] restores it before persisting);
 *  - a delay change persists ONLY to the per-item store ([setDelay]) — it
 *    must never route through the global style write;
 *  - the engine re-sync after a delay change is debounced and idempotent:
 *    a burst of nudges coalesces into one apply ([SUBTITLE_DELAY_APPLY_DEBOUNCE_MS]).
 */
internal class SubtitleStyleController(
    private val scope: CoroutineScope,
    /** Reads the current in-memory style (the VM's subtitleStyle mirror). */
    private val getStyle: () -> SubtitleStyle,
    /** Writes the in-memory style (the VM's subtitleStyle mirror). */
    private val setStyleMirror: (SubtitleStyle) -> Unit,
    /** Writes the dialogue-boost mirror pair (strength + its enabled flag). */
    private val setDialogueBoostMirror: (strength: EffectStrength, enabled: Boolean) -> Unit,
    /** Reads the dialogue-boost enabled mirror (the toggle's input). */
    private val isDialogueBoostEnabled: () -> Boolean,
    /** The session's current item id (the per-item delay store key). */
    private val getCurrentItemId: () -> String?,
    /** The persisted GLOBAL "Subtitle sync offset" default ([setStyle] restores it). */
    private val getGlobalOffsetMs: () -> Long,
    /** Persists the global style ([SubtitleLanguageStore.setSubtitleStyle]). */
    private val saveGlobalStyle: suspend (SubtitleStyle) -> Unit,
    /** Persists a per-item delay correction ([SubtitleLanguageStore.setSubtitleDelayForItem]). */
    private val saveItemDelay: suspend (itemId: String, delayMs: Long) -> Unit,
    /** Persists the per-item/series dialogue-boost rule ([ItemPlaybackPreferenceWriter]). */
    private val saveDialogueBoost: (EffectStrength) -> Unit,
    /** Immediate engine-config rebuild ([VideoPlayerViewModel.updateConfigWithUiState]). */
    private val syncEngineConfig: () -> Unit,
    /** Drag-settling engine-config rebuild ([VideoPlayerViewModel.updateConfigWithUiStateDebounced]). */
    private val syncEngineConfigDebounced: () -> Unit,
) {

    // Coalesces a burst of subtitle-delay fine-tune changes into one engine
    // apply (one media reload on ExoPlayer/LibVLC). Cancelled/replaced on each
    // setDelay call so only the last value wins.
    private var delayApplyJob: Job? = null

    /**
     * Applies a user style edit (font/colour/edge/font-family…): mirrors it,
     * rebuilds the engine config immediately, then persists it GLOBALLY with
     * the persisted global offset restored — see the class KDoc's first
     * invariant.
     */
    fun setStyle(style: SubtitleStyle) {
        setStyleMirror(style)
        syncEngineConfig()
        scope.launch { persistStylePreservingGlobalOffset(style) }
    }

    private suspend fun persistStylePreservingGlobalOffset(style: SubtitleStyle) {
        val globalOffsetMs = getGlobalOffsetMs()
        saveGlobalStyle(style.copy(offsetMs = globalOffsetMs))
    }

    /**
     * Applies a subtitle-delay change: mirrors the new resolved value (the
     * overlay readout updates instantly) and persists ONLY to the per-item
     * store — routing this through [setStyle] would clobber the per-item
     * correction into the global "Subtitle sync offset" default, which is how
     * a correction for one item previously leaked into every other item. The
     * engine apply is debounced: a delay change forces ExoPlayer/LibVLC to
     * reload the media item to re-parse cues through the offset wrapper (a
     * rebuffer); mpv and libVLC apply the delay live (sub-delay / setSpuDelay)
     * with no reload, but ExoPlayer benefits from coalescing a burst of
     * fine-tune nudges into a single reload.
     */
    fun setDelay(delayMs: Long) {
        val current = getStyle()
        if (current.offsetMs == delayMs) return
        setStyleMirror(current.copy(offsetMs = delayMs))
        getCurrentItemId()?.let { itemId ->
            scope.launch { saveItemDelay(itemId, delayMs) }
        }
        delayApplyJob?.cancel()
        delayApplyJob = scope.launch {
            delay(SUBTITLE_DELAY_APPLY_DEBOUNCE_MS)
            syncEngineConfig()
        }
    }

    /**
     * Toggles dialogue boost. Dialogue Boost is persisted per-item/series:
     * toggling on pins MODERATE for this item/series, toggling off clears it
     * (resolves to NONE). It does not touch the global setting, so it never
     * bleeds across unrelated items.
     */
    fun toggleDialogueBoost() {
        val target = if (isDialogueBoostEnabled()) EffectStrength.NONE else EffectStrength.MODERATE
        setDialogueBoost(target)
    }

    /**
     * Sets the dialogue-boost strength: mirrors the pair, rebuilds the engine
     * config, then persists per SERIES-then-ITEM scope through the preference
     * writer (NONE means the explicit clear, and the resolver refresh always
     * fires — see [ItemPlaybackPreferenceWriter.setDialogueBoostStrength]).
     */
    fun setDialogueBoost(strength: EffectStrength) {
        setDialogueBoostMirror(strength, strength != EffectStrength.NONE)
        syncEngineConfig()
        saveDialogueBoost(strength)
    }

    /**
     * Per-item hydration after a load: resolve the effective subtitle-sync
     * delay for the item — a stored per-item correction wins, otherwise the
     * global "Subtitle sync offset" default applies ([resolveSubtitleDelayMs]
     * via the slice). Always applied (even when no per-item entry exists) so
     * the previous item's in-memory delay can't bleed into this one; pushed
     * through the DRAG debounce (the load path already rebuilt the config
     * around the same frame — no need for the 500 ms delay-reload debounce).
     */
    fun onItemHydrated(slice: SubtitleSlice, itemId: String) {
        val itemDelay = resolveSubtitleDelayMs(slice, itemId)
        val current = getStyle()
        if (current.offsetMs != itemDelay) {
            setStyleMirror(current.copy(offsetMs = itemDelay))
            syncEngineConfigDebounced()
        }
    }

    /**
     * Seeds the slice for a freshly bound engine (load, mode/quality/stream
     * reload, engine switch, error retry): the style re-resolves from prefs
     * WITH its per-item delay — an engine swap must never reset a per-item
     * correction to the global default — and dialogue boost resets to OFF
     * until the per-item resolver re-applies a stored rule (it does not
     * inherit the previous item's strength).
     */
    fun onEngineBound(slice: SubtitleSlice, itemId: String?, isHdr: Boolean) {
        setStyleMirror(resolveSubtitleStyleWithDelay(slice, itemId, isHdr))
        setDialogueBoostMirror(EffectStrength.NONE, false)
    }
}
