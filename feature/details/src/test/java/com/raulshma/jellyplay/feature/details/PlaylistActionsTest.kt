package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaylistActionsTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val appRuntimeStateStore: AppRuntimeStateStore = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val messages = mutableListOf<DetailMessage>()
    private val messageSink: (DetailMessage) -> Unit = { messages += it }

    private val movieDetail = MediaDetail(
        item = MediaItem(id = "m1", name = "A Movie", mediaType = MediaType.MOVIE),
    )
    private val audioDetail = MediaDetail(
        item = MediaItem(id = "a1", name = "An Album", mediaType = MediaType.AUDIO),
    )
    private val seriesDetail = MediaDetail(
        item = MediaItem(id = "s1", name = "A Series", mediaType = MediaType.SERIES),
    )

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun actions(
        scope: CoroutineScope,
        detail: () -> MediaDetail? = { movieDetail },
        sortedEpisodes: () -> List<MediaItem> = { emptyList() },
        canonicalEpisodeIds: suspend (String) -> List<String> = { emptyList() },
    ): PlaylistActions = PlaylistActions(
        scope = scope,
        mediaRepository = mediaRepository,
        appRuntimeStateStore = appRuntimeStateStore,
        context = context,
        detailProvider = detail,
        sortedEpisodesProvider = sortedEpisodes,
        canonicalEpisodeIds = canonicalEpisodeIds,
        messageSink = messageSink,
    )

    // region openPlaylistPicker
    @Test
    fun `openPlaylistPicker with a movie sets showPlaylistPicker and loads editable playlists`() = runTest {
        val editable = Playlist(id = "p1", name = "Favs", canEdit = true)
        val readOnly = Playlist(id = "p2", name = "Read Only", canEdit = false)
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(listOf(editable, readOnly))
        val a = actions(this)

        a.openPlaylistPicker()
        advanceUntilIdle()

        val state = a.state.value
        assertTrue(state.showPlaylistPicker)
        assertFalse(state.isLoadingPlaylists)
        assertEquals(listOf(editable), state.playlists)
    }

    @Test
    fun `openPlaylistPicker with an audio detail is a no-op`() = runTest {
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = actions(this, detail = { audioDetail })

        a.openPlaylistPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPlaylistPicker)
        assertTrue(a.state.value.playlists.isEmpty())
        // The audio type is ineligible, so the list should never be fetched.
        coVerify(exactly = 0) { mediaRepository.getPlaylists(any()) }
    }
    // endregion

    // region addToPlaylist
    @Test
    fun `addToPlaylist success emits the added-to-playlist message`() = runTest {
        every { context.getString(R.string.detail_msg_added_to_playlist, any()) } returns "added"
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        val a = actions(this)

        a.addToPlaylist(playlist)
        advanceUntilIdle()

        assertTrue(messages.contains(DetailMessage.Text("added")))
        assertFalse(a.state.value.isAddingToPlaylist)
        assertFalse(a.state.value.showPlaylistPicker)
        coVerify { mediaRepository.addItemsToPlaylist("p1", listOf("m1")) }
    }

    @Test
    fun `addToPlaylist for a series with no episodes emits the no-episodes-queued message`() = runTest {
        every { context.getString(R.string.detail_msg_no_episodes_queued) } returns "no episodes"
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        // Series with no sorted episodes and no canonical ids resolves to empty.
        val a = actions(
            this,
            detail = { seriesDetail },
            sortedEpisodes = { emptyList() },
            canonicalEpisodeIds = { emptyList() },
        )

        a.addToPlaylist(playlist)
        advanceUntilIdle()

        assertTrue(messages.contains(DetailMessage.Text("no episodes")))
        // Nothing should be queued against an empty resolution.
        coVerify(exactly = 0) { mediaRepository.addItemsToPlaylist(any(), any()) }
        assertFalse(a.state.value.isAddingToPlaylist)
    }
    // endregion
}
