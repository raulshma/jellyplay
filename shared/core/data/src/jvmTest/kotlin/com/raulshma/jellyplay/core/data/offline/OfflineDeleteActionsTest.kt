package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Moved with [OfflineDeleteActions] from `feature/details` — the
 * detail-side cases (content-mutated signaling, collapse classification) run
 * unchanged, proving the lift is behavior-preserving, plus the new cases that
 * replace Home's and Downloads' now-deleted private copies: `Set` input,
 * [OfflineDeleteActions.deleteDownload] routing, and zero-episode-season
 * safety.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDeleteActionsTest {

    private val offlineRepository: OfflineRepository = mockk(relaxed = true)

    private fun actions(
        scope: TestScope,
        episodes: Map<String, List<MediaItem>> = emptyMap(),
        seasons: List<MediaItem> = emptyList(),
        onContentMutated: () -> Unit = {},
    ): OfflineDeleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
        episodesProvider = { episodes },
        seasonsProvider = { seasons },
        onContentMutated = onContentMutated,
    )

    private fun episode(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
    )

    private fun season(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.SEASON,
    )

    private fun movie(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
    )

    // region deleteOfflineEpisodes — whole-season vs partial classification
    @Test
    fun `deleteOfflineEpisodes with full season selection drops the whole season`() = runTest {
        val episodes = mapOf("season1" to listOf(episode("e1"), episode("e2")))
        val seasons = listOf(season("season1"))
        val actions = actions(this, episodes, seasons)

        actions.deleteOfflineEpisodes(listOf("e1", "e2"))
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineSeason("season1") }
        // No per-episode deletes for the season dropped wholesale.
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e2") }
    }

    @Test
    fun `deleteOfflineEpisodes with partial selection deletes each remaining id`() = runTest {
        val episodes = mapOf("season1" to listOf(episode("e1"), episode("e2"), episode("e3")))
        val seasons = listOf(season("season1"))
        val actions = actions(this, episodes, seasons)

        actions.deleteOfflineEpisodes(listOf("e1", "e3"))
        advanceUntilIdle()

        // Not a full season → no season drop.
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason(any()) }
        coVerify { offlineRepository.deleteOfflineItem("e1") }
        coVerify { offlineRepository.deleteOfflineItem("e3") }
        // Unselected episode left untouched.
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e2") }
    }

    @Test
    fun `deleteOfflineEpisodes honors ids not present under any known season`() = runTest {
        val episodes = mapOf("season1" to listOf(episode("e1")))
        val seasons = listOf(season("season1"))
        val actions = actions(this, episodes, seasons)

        actions.deleteOfflineEpisodes(listOf("orphan"))
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineItem("orphan") }
        // No season drop (orphan isn't a full season).
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason(any()) }
    }

    @Test
    fun `deleteOfflineEpisodes with empty list is a no-op`() = runTest {
        val actions = actions(this)

        actions.deleteOfflineEpisodes(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason(any()) }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem(any()) }
    }

    @Test
    fun `deleteOfflineEpisodes accepts a Set input like the home sheet`() = runTest {
        val episodes = mapOf(
            "season1" to listOf(episode("e1"), episode("e2")),
            "season2" to listOf(episode("e3"), episode("e4")),
        )
        val seasons = listOf(season("season1"), season("season2"))
        val actions = actions(this, episodes, seasons)

        // The offline home's advanced delete sheet multi-selects across seasons
        // with a Set<String>: season1 is fully selected (collapses), season2
        // only partially (per-episode fallback, e4 left untouched).
        actions.deleteOfflineEpisodes(setOf("e1", "e2", "e3"))
        advanceUntilIdle()

        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeason("season1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason("season2") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e2") }
        coVerify(exactly = 1) { offlineRepository.deleteOfflineItem("e3") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e4") }
    }

    @Test
    fun `deleteOfflineEpisodes never collapses a season with zero downloaded episodes`() = runTest {
        // Home's sheet pre-filters empty seasons out; detail's snapshot keeps
        // them. Both must be safe: a season with no episode rows can never be
        // "fully selected", so it must not trigger a whole-season drop.
        val episodes = mapOf("season1" to listOf(episode("e1")))
        val seasons = listOf(season("season1"), season("emptySeason"))
        val actions = actions(this, episodes, seasons)

        actions.deleteOfflineEpisodes(listOf("e1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeason("season1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason("emptySeason") }
    }
    // endregion

    // region deleteDownload — quick-action routing (Home / Downloads quick menus)
    @Test
    fun `deleteDownload routes a series to whole-series delete`() = runTest {
        val actions = actions(this)

        actions.deleteDownload(season("s1").copy(mediaType = MediaType.SERIES))
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineSeries("s1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem(any()) }
    }

    @Test
    fun `deleteDownload routes a non-series item to a single-item delete`() = runTest {
        val actions = actions(this)

        actions.deleteDownload(movie("m1"))
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineItem("m1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeries(any()) }
    }
    // endregion

    // region single-item delegates
    @Test
    fun `deleteOfflineItem delegates to repository`() = runTest {
        val actions = actions(this)

        actions.deleteOfflineItem("id1")
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineItem("id1") }
    }

    @Test
    fun `deleteOfflineEpisode delegates to deleteOfflineItem`() = runTest {
        val actions = actions(this)

        actions.deleteOfflineEpisode("ep1")
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineItem("ep1") }
    }

    @Test
    fun `deleteOfflineSeason delegates to repository`() = runTest {
        val actions = actions(this)

        actions.deleteOfflineSeason("season1")
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineSeason("season1") }
    }

    @Test
    fun `deleteOfflineSeries delegates to repository`() = runTest {
        val actions = actions(this)

        actions.deleteOfflineSeries("series1")
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineSeries("series1") }
    }
    // endregion

    // region post-delete refresh signal
    @Test
    fun `deleteOfflineEpisode signals content mutation after the delete lands`() = runTest {
        var mutations = 0
        val actions = actions(this, onContentMutated = { mutations++ })

        actions.deleteOfflineEpisode("ep1")
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineItem("ep1") }
        // Exactly one refresh signal, and only after the repository delete ran.
        assertEquals(1, mutations)
    }

    @Test
    fun `deleteOfflineEpisodes signals content mutation once after the batch`() = runTest {
        var mutations = 0
        val episodes = mapOf("season1" to listOf(episode("e1"), episode("e2")))
        val seasons = listOf(season("season1"))
        val actions = actions(this, episodes, seasons, onContentMutated = { mutations++ })

        actions.deleteOfflineEpisodes(listOf("e1", "e2"))
        advanceUntilIdle()

        // Whole-season drop + a single mutation signal (not one per episode).
        coVerify { offlineRepository.deleteOfflineSeason("season1") }
        assertEquals(1, mutations)
    }

    @Test
    fun `onContentMutated defaults to a no-op for reactive consumers`() = runTest {
        // Home/downloads construct the actions without the callback (their
        // Room-backed offline library flows refresh on their own); the default
        // must simply never fire.
        val actions = OfflineDeleteActions(
            scope = this,
            offlineRepository = offlineRepository,
        )

        actions.deleteOfflineItem("id1")
        advanceUntilIdle()

        coVerify { offlineRepository.deleteOfflineItem("id1") }
    }
    // endregion
}
