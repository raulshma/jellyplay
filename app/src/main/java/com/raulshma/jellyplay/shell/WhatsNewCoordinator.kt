package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.data.whatsnew.WhatsNewRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.whatsnew.WhatsNewDecision
import com.raulshma.jellyplay.whatsnew.WhatsNewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the post-update What's New prompt behind a small seam (the twin of
 * [UpdateCoordinator] for the release-notes feed): one [state] flow the shell
 * renders, plus the dismiss command the sheet issues. The seen-version stamp,
 * the show-once policy, and the offline-first content lookup are private to
 * this module; the pure policy lives in [WhatsNewDecision].
 *
 * Launch flow ([onSessionRestored], after session restore):
 *  1. Decide the phase from seen-version + onboarding state.
 *  2. `SHOW_IF_CONTENT`: serve from the cached feed first (works offline,
 *     instantly — there is no compiled-in snapshot, the GitHub release body
 *     is the single source); only when the cache doesn't know this version
 *     yet does the launch await one [WhatsNewRepository.refresh].
 *  3. A failed refresh never stamps: an offline first launch retries on the
 *     NEXT launch instead of silently losing the prompt. A successful fetch
 *     that still lacks the version stamps, so an unknown version never nags.
 *  4. Every other phase still fires a best-effort refresh so the Settings
 *     archive starts warm.
 *
 * The sheet is rendered by the shell only while the update sheet is Idle —
 * an available self-update outranks the What's New presentation; this
 * coordinator never needs to know that.
 *
 * Content is the release body (same artifact the update sheet shows): the
 * `## What's New` table becomes the guided cards, and a body without the
 * table still presents as markdown, so "content exists" means the release
 * carries either.
 */
class WhatsNewCoordinator(
    private val whatsNewRepository: WhatsNewRepository,
    private val experimentalStore: ExperimentalStore,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val currentVersionName: () -> String,
) : ShellCoordinator() {

    private val _state = MutableStateFlow<WhatsNewState>(WhatsNewState.Idle)
    val state: StateFlow<WhatsNewState> = _state.asStateFlow()

    /**
     * Launch-time hook, called once session restore completes (the same
     * callback that drives [UpdateCoordinator.onSessionRestored]).
     */
    fun onSessionRestored() {
        commandScope.launch {
            val installed = currentVersionName()
            val seen = experimentalStore.whatsNewSeenVersion.first()
            val onboardingCompleted = appRuntimeStateStore.isOnboardingCompleted()

            when (WhatsNewDecision.launchPhase(installed, seen, onboardingCompleted)) {
                WhatsNewDecision.Phase.NONE -> whatsNewRepository.refresh()
                WhatsNewDecision.Phase.STAMP_SILENTLY -> {
                    experimentalStore.setWhatsNewSeenVersion(installed)
                    whatsNewRepository.refresh()
                }
                WhatsNewDecision.Phase.SHOW_IF_CONTENT -> presentOrUpdateStamp(installed)
            }
        }
    }

    /**
     * The SHOW_IF_CONTENT arm: offline-first content lookup. The cached feed
     * is checked synchronously; only a version the cache doesn't know yet
     * waits for one remote fetch.
     */
    private suspend fun presentOrUpdateStamp(installed: String) {
        val cached = WhatsNewDecision.releaseToShow(installed, whatsNewRepository.releasesSnapshot())
        if (cached != null) {
            _state.value = WhatsNewState.Show(cached)
            // Warm the archive without gating the presentation.
            whatsNewRepository.refresh()
            return
        }
        // The cache doesn't know this version: give the remote exactly one
        // chance. Stamp ONLY on a successful fetch that still lacks the
        // version — a failed one (offline launch) leaves the stamp alone so
        // the next launch retries rather than losing the prompt forever.
        val refreshed = whatsNewRepository.refresh()
        if (refreshed.isFailure) return
        val fetched = WhatsNewDecision.releaseToShow(installed, whatsNewRepository.releasesSnapshot())
        if (fetched != null) {
            _state.value = WhatsNewState.Show(fetched)
        } else {
            experimentalStore.setWhatsNewSeenVersion(installed)
        }
    }

    /**
     * Closes the sheet and stamps the shown version seen — the single exit
     * path (explicit dismiss AND deep-link navigation both land here), which
     * is what makes the presentation once-per-release.
     */
    fun dismiss() {
        val shown = (_state.value as? WhatsNewState.Show)?.release
        _state.value = WhatsNewState.Idle
        if (shown != null) {
            commandScope.launch {
                experimentalStore.setWhatsNewSeenVersion(
                    WhatsNewRelease.normalizeVersion(shown.version),
                )
            }
        }
    }
}
