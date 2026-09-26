package com.raulshma.jellyplay.whatsnew

import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.core.model.compareVersions

/**
 * Pure launch decision for the post-update What's New prompt. All inputs are
 * plain values so the policy is unit-testable and identical across shells.
 */
object WhatsNewDecision {

    /** What [WhatsNewCoordinator.onSessionRestored] should do for a launch. */
    enum class Phase {
        /** Nothing to present and nothing to stamp (already seen this version). */
        NONE,

        /** Present the installed version's release when the feed carries one. */
        SHOW_IF_CONTENT,

        /** Fresh install — stamp the version seen without ever presenting. */
        STAMP_SILENTLY,
    }

    /**
     * The launch policy:
     *
     *  - `seen == null && !onboardingCompleted` → a genuinely fresh install
     *    (or mid-onboarding): stamp silently. The What's New of the version
     *    you installed with is not "new" to you.
     *  - `seen == null && onboardingCompleted` → an install that predates the
     *    feature: the user has a history, so treat the current version as an
     *    upgrade they haven't been shown.
     *  - `compareVersions(installed, seen) > 0` → a real upgrade since the
     *    last shown release: present it.
     *  - otherwise → already seen this (or newer) version: nothing.
     */
    fun launchPhase(
        installedVersion: String,
        seenVersion: String?,
        onboardingCompleted: Boolean,
    ): Phase = when {
        seenVersion == null && !onboardingCompleted -> Phase.STAMP_SILENTLY
        seenVersion == null -> Phase.SHOW_IF_CONTENT
        compareVersions(installedVersion, seenVersion) > 0 -> Phase.SHOW_IF_CONTENT
        else -> Phase.NONE
    }

    /**
     * The release to present for the installed version, if any — exact version
     * match against the assembled feed (cached ∪ fetched).
     */
    fun releaseToShow(
        installedVersion: String,
        releases: List<WhatsNewRelease>,
    ): WhatsNewRelease? = releases.firstOrNull { it.version == installedVersion }
}
