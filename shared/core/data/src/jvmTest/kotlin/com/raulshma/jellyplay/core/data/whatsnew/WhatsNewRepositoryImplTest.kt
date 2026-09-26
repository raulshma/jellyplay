package com.raulshma.jellyplay.core.data.whatsnew

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.github.GitHubReleaseNotes
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

/**
 * The two-source assembly contract of [WhatsNewRepositoryImpl] — cache
 * fold-in → GitHub-releases override, all of it the SAME artifact (the
 * release-notes body; there is no compiled-in snapshot). Fetch failures
 * leave the assembled feed untouched and the cache survives into a fresh
 * construction (the offline floor between launches).
 */
class WhatsNewRepositoryImplTest {

    private class FakeReleasesApi(var notes: List<GitHubReleaseNotes>? = null, var error: Exception? = null) : GitHubReleasesApi {
        override suspend fun fetchLatestUpdate(
            currentVersionName: String,
            flavor: String,
            supportedAbis: Array<String>,
        ): Result<AppUpdateInfo> = error?.let { Result.failure(it) }
            ?: Result.failure(IllegalStateException("not used by the repository"))

        override suspend fun fetchReleaseNotes(): Result<List<GitHubReleaseNotes>> {
            error?.let { return Result.failure(it) }
            return Result.success(requireNotNull(notes))
        }
    }

    /** One temp-file DataStore + scope, shareable across repo constructions. */
    private class Harness(notes: List<GitHubReleaseNotes>? = null, error: Exception? = null) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val api = FakeReleasesApi(notes, error)
        private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) { Files.createTempFile("whatsnew-test", ".preferences_pb").toFile().absolutePath.toPath() }
        val store = ExperimentalStore(dataStore, scope)
        val repo = WhatsNewRepositoryImpl(api, store, scope)

        /** A SECOND repo over the same DataStore (cache-survival case). */
        fun freshRepo(notes: List<GitHubReleaseNotes>? = null, error: Exception? = null): WhatsNewRepositoryImpl {
            api.notes = notes
            api.error = error
            return WhatsNewRepositoryImpl(api, store, scope)
        }
    }

    private fun notes(version: String, body: String, title: String? = null, date: String? = "2026-09-26") =
        GitHubReleaseNotes(version = version, date = date, title = title, body = body)

    private fun tableBody(title: String) = """
        ## What's New

        | Title | Category |
        |---|---|
        | $title | new |
    """.trimIndent()

    @Test
    fun `releases start empty before any fetch`() = runBlocking {
        val h = Harness()

        // No compiled-in snapshot: the GitHub release body is the single
        // authoring surface, so a fresh install (or a first launch with no
        // cache) serves nothing until the first successful refresh.
        assertTrue(h.repo.releasesSnapshot().isEmpty())
        h.scope.cancel()
    }

    @Test
    fun `refresh maps releases deriving entries from the whats-new table`() = runBlocking {
        val remote = listOf(
            notes("0.11.2", tableBody("Brand new"), title = "Fresh coat of paint"),
            notes("0.11.0", "plain prose notes", title = "Book reading experience 2.0"),
        )
        val h = Harness(notes = remote)

        val result = h.repo.refresh()

        assertEquals(Result.success(Unit), result)
        val releases = h.repo.releasesSnapshot()
        assertEquals(listOf("0.11.2", "0.11.0"), releases.map { it.version })
        assertEquals("Brand new", releases[0].entries.single().title)
        assertEquals("Fresh coat of paint", releases[0].title)
        // A body without the table keeps the prose and has no cards.
        assertTrue(releases[1].entries.isEmpty())
        assertEquals("plain prose notes", releases[1].body)
        h.scope.cancel()
    }

    @Test
    fun `refresh failure leaves the assembled feed untouched and nothing cached`() = runBlocking {
        val h = Harness(error = java.io.IOException("offline"))

        val result = h.repo.refresh()

        assertTrue(result.isFailure)
        assertTrue(h.repo.releasesSnapshot().isEmpty())
        assertNull(h.store.whatsNewFeedJson.first())
        h.scope.cancel()
    }

    @Test
    fun `blank releases are dropped instead of cached`() = runBlocking {
        val h = Harness(notes = listOf(notes("", "body")))

        val result = h.repo.refresh()

        assertEquals(Result.success(Unit), result)
        assertTrue(h.repo.releasesSnapshot().isEmpty())
        h.scope.cancel()
    }

    @Test
    fun `a later refresh overrides the cache per version`() = runBlocking {
        val h = Harness(notes = listOf(notes("0.11.2", tableBody("First take"))))
        assertTrue(h.repo.refresh().isSuccess)

        // Editing the release body on GitHub corrects the fetched copy.
        h.api.notes = listOf(notes("0.11.2", tableBody("Corrected entry")))
        assertTrue(h.repo.refresh().isSuccess)

        assertEquals("Corrected entry", h.repo.releasesSnapshot().single().entries.single().title)
        h.scope.cancel()
    }

    @Test
    fun `a fetched cache survives into a fresh construction with no network`() = runBlocking {
        val h = Harness(notes = listOf(notes("0.11.2", tableBody("Brand new"))))
        assertTrue(h.repo.refresh().isSuccess)

        // Fresh repo over the SAME store, network dead: the cached 0.11.2
        // must still be there (offline-first after one online moment).
        val second = h.freshRepo(error = java.io.IOException("offline"))
        val versions = second.releasesSnapshot().map { it.version }

        assertEquals(listOf("0.11.2"), versions)
        h.scope.cancel()
    }
}
