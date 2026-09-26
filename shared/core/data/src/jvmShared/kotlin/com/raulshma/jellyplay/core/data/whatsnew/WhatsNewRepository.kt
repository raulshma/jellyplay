package com.raulshma.jellyplay.core.data.whatsnew

import com.raulshma.jellyplay.core.model.WhatsNewRelease
import kotlinx.coroutines.flow.StateFlow

/**
 * The What's-New view over the GitHub release notes (core:model's
 * `com.raulshma.jellyplay.core.model.WhatsNewFeed`): the per-release cards
 * behind the post-update sheet and the Settings archive, derived from each
 * release body's `## What's New` table — one content artifact, shared with
 * the update-available sheet's notes.
 */
interface WhatsNewRepository {

    /**
     * Every known release, newest version first. Starts empty (no compiled-in
     * snapshot — the GitHub release body is the single authoring surface),
     * folds in the last-fetched cache, and reflects a successful [refresh] —
     * remote releases override the cache per version, so editing a release
     * body on GitHub corrects what was fetched.
     */
    val releases: StateFlow<List<WhatsNewRelease>>

    /**
     * Await-once variant of [releases] for decide-then-act consumers (the
     * post-update prompt decision must not race the async cache fold-in).
     */
    suspend fun releasesSnapshot(): List<WhatsNewRelease>

    /**
     * Fetches the GitHub releases list, caches it, and folds it into
     * [releases]. Fails soft: on any failure the existing cached state is
     * untouched and the failure is returned (the caller decides
     * whether to surface it — the launch path never does).
     */
    suspend fun refresh(): Result<Unit>
}
