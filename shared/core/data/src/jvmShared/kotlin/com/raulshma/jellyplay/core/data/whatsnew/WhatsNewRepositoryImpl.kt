package com.raulshma.jellyplay.core.data.whatsnew

import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.WhatsNewFeed
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.core.model.mergeWhatsNewFeeds
import com.raulshma.jellyplay.core.model.parseWhatsNewFeed
import com.raulshma.jellyplay.core.model.whatsNewReleaseFromNotes
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The What's-New feed, assembled from its two sources in precedence order —
 * both of them the SAME artifact (the GitHub release notes body):
 *
 *  1. **Cached** — the last successfully fetched releases list (persisted by
 *     [ExperimentalStore] as a feed document), folded in at construction;
 *     the offline floor between launches.
 *  2. **Remote** — the GitHub Releases list ([GitHubReleasesApi]
 *     `fetchReleaseNotes`), fetched by [refresh]; overrides the cache per
 *     version (editing a release body on GitHub corrects what was fetched)
 *     and carries the full history for the Settings archive.
 *
 * There is no compiled-in snapshot: the GitHub release body is the single
 * authoring surface, so a fresh install starts empty and fills from the
 * first successful fetch (the post-update prompt waits for that one fetch —
 * see [com.raulshma.jellyplay.shell.WhatsNewCoordinator] for the launch
 * policy). A failed fetch never degrades the assembled feed: cached state
 * stays live and the failure is returned to the caller. Releases whose
 * version or body is blank are dropped by [whatsNewReleaseFromNotes].
 */
class WhatsNewRepositoryImpl(
    private val gitHubReleasesApi: GitHubReleasesApi,
    private val experimentalStore: ExperimentalStore,
    externalScope: CoroutineScope,
) : WhatsNewRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _releases = MutableStateFlow<List<WhatsNewRelease>>(emptyList())
    override val releases: StateFlow<List<WhatsNewRelease>> = _releases.asStateFlow()

    /**
     * Folds the cache into the empty start off the hot path. runCatching so
     * a DataStore read failure can never cancel the caller's scope — the
     * flow then simply stays empty, and [releasesSnapshot] (which joins
     * this job) still serves that degraded-but-valid state.
     */
    private val initJob: Job = externalScope.launch {
        runCatching {
            val cached = experimentalStore.whatsNewFeedJson.first()
            // A never-fetched cache parses to the empty feed — a no-op merge.
            foldIn(parseWhatsNewFeed(cached) ?: WhatsNewFeed())
        }
    }

    override suspend fun releasesSnapshot(): List<WhatsNewRelease> {
        initJob.join()
        return _releases.value
    }

    override suspend fun refresh(): Result<Unit> {
        val fetched = gitHubReleasesApi.fetchReleaseNotes()
        val notes = fetched.getOrElse { return fetched.map { } }
        val feed = WhatsNewFeed(
            releases = notes.mapNotNull { release ->
                whatsNewReleaseFromNotes(release.version, release.date, release.title, release.body)
            },
        )
        experimentalStore.setWhatsNewFeedCache(json.encodeToString(feed))
        foldIn(feed)
        return Result.success(Unit)
    }

    /**
     * Merges [incoming] into the published state (per-version union, incoming
     * wins) through the single write path, so the cache fold-in and a refresh
     * can never clobber each other with a stale base.
     */
    private fun foldIn(incoming: WhatsNewFeed) {
        val current = WhatsNewFeed(_releases.value)
        _releases.value = mergeWhatsNewFeeds(current, incoming).releases
    }
}
