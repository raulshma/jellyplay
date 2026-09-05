package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_added_to_playlist
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_added_to_watch_later
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_add_to_playlist
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_no_episodes_queued
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_playlist_created
import com.raulshma.jellyplay.feature.details.generated.resources.detail_playlist_watch_later

/**
 * Drives the playlist adapter over [AddToTargetActions] plus
 * [WatchLaterActions] through their interfaces — the merged module's playlist
 * surface (formerly the PlaylistActions mirror).
 */
class PlaylistTargetsTest {

    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val appRuntimeStateStore: AppRuntimeStateStore = mockk(relaxed = true)
    private val mediaDetailProvider: MediaDetailProvider = mockk(relaxed = true)

    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    private val movieDetail = MediaDetail(
        item = MediaItem(id = "m1", name = "A Movie", mediaType = MediaType.MOVIE),
    )
    private val audioDetail = MediaDetail(
        item = MediaItem(id = "a1", name = "An Album", mediaType = MediaType.AUDIO),
    )
    private val seriesDetail = MediaDetail(
        item = MediaItem(id = "s1", name = "A Series", mediaType = MediaType.SERIES),
    )

    @BeforeTest
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun targets(
        scope: CoroutineScope,
        detail: MediaDetail? = movieDetail,
        sortedEpisodes: List<MediaItem> = emptyList(),
        canonicalEpisodeIds: (String) -> List<String> = { emptyList() },
    ): PlaylistTargets {
        coEvery { mediaDetailProvider.canonicalEpisodeIds(any()) } coAnswers {
            canonicalEpisodeIds(firstArg())
        }
        return PlaylistTargets.Factory(playlistRepository, appRuntimeStateStore).create(
            scope = scope,
            session = MutableStateFlow(
                DetailSession(
                    itemId = detail?.item?.id ?: "m1",
                    detail = detail,
                    sortedEpisodes = sortedEpisodes,
                ),
            ),
            messages = messages.flow,
            strings = strings,
            mediaDetailProvider = mediaDetailProvider,
        )
    }

    // region openPicker
    @Test
    fun `openPicker with a movie sets showPicker and loads editable playlists`() = runTest {
        val editable = Playlist(id = "p1", name = "Favs", canEdit = true)
        val readOnly = Playlist(id = "p2", name = "Read Only", canEdit = false)
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(listOf(editable, readOnly))
        val a = targets(this).picker

        a.openPicker()
        advanceUntilIdle()

        val state = a.state.value
        assertTrue(state.showPicker)
        assertFalse(state.isLoadingTargets)
        assertEquals(listOf(editable), state.targets)
    }

    @Test
    fun `openPicker with an audio detail is a no-op`() = runTest {
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = targets(this, detail = audioDetail).picker

        a.openPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPicker)
        assertTrue(a.state.value.targets.isEmpty())
        // The audio type is ineligible, so the list should never be fetched.
        coVerify(exactly = 0) { playlistRepository.getPlaylists(any()) }
    }

    @Test
    fun `openPicker with a series sets showPicker and loads playlists`() = runTest {
        val editable = Playlist(id = "p1", name = "Favs", canEdit = true)
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(listOf(editable))
        val a = targets(this, detail = seriesDetail).picker

        a.openPicker()
        advanceUntilIdle()

        assertTrue(a.state.value.showPicker)
        assertEquals(listOf(editable), a.state.value.targets)
    }

    @Test
    fun `openPicker with no loaded detail is a no-op`() = runTest {
        val a = targets(this, detail = null).picker

        a.openPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPicker)
        coVerify(exactly = 0) { playlistRepository.getPlaylists(any()) }
    }

    @Test
    fun `dismissPicker clears the picker flag`() = runTest {
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = targets(this).picker
        a.openPicker()
        advanceUntilIdle()
        assertTrue(a.state.value.showPicker)

        a.dismissPicker()

        assertFalse(a.state.value.showPicker)
    }

    @Test
    fun `openCreateDialog closes the picker and opens the dialog`() = runTest {
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = targets(this).picker
        a.openPicker()
        advanceUntilIdle()

        a.openCreateDialog()

        // The picker and create-dialog are mutually exclusive.
        assertFalse(a.state.value.showPicker)
        assertTrue(a.state.value.showCreateDialog)
    }

    @Test
    fun `dismissCreateDialog closes the dialog`() = runTest {
        val a = targets(this).picker
        a.openCreateDialog()
        assertTrue(a.state.value.showCreateDialog)

        a.dismissCreateDialog()

        assertFalse(a.state.value.showCreateDialog)
    }
    // endregion

    // region addTo
    @Test
    fun `addTo success emits the added-to-playlist message`() = runTest {
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        val a = targets(this).picker

        a.addTo(playlist)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_added_to_playlist, playlist.name))
            )
        )
        assertFalse(a.state.value.isAdding)
        assertFalse(a.state.value.showPicker)
        coVerify { playlistRepository.addItemsToPlaylist("p1", listOf("m1")) }
    }

    @Test
    fun `addTo for a series with no episodes emits the no-episodes-queued message`() = runTest {
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        // Series with no sorted episodes and no canonical ids resolves to empty.
        val a = targets(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        ).picker

        a.addTo(playlist)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_no_episodes_queued))
            )
        )
        // Nothing should be queued against an empty resolution.
        coVerify(exactly = 0) { playlistRepository.addItemsToPlaylist(any(), any()) }
        assertFalse(a.state.value.isAdding)
    }

    @Test
    fun `addTo for a series resolves ids from sorted episodes over a cold load`() = runTest {
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        val ep1 = MediaItem(id = "ep1", name = "E1", mediaType = MediaType.EPISODE)
        val ep2 = MediaItem(id = "ep2", name = "E2", mediaType = MediaType.EPISODE)
        var coldLoads = 0
        val a = targets(
            this,
            detail = seriesDetail,
            sortedEpisodes = listOf(ep1, ep2),
            canonicalEpisodeIds = {
                coldLoads++
                listOf("cold")
            },
        ).picker

        a.addTo(playlist)
        advanceUntilIdle()

        // Sorted episodes win; the cold-load fallback must not fire.
        coVerify { playlistRepository.addItemsToPlaylist("p1", listOf("ep1", "ep2")) }
        assertEquals(0, coldLoads)
    }

    @Test
    fun `addTo for a series falls back to canonicalEpisodeIds when the snapshot is empty`() = runTest {
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        val a = targets(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { listOf("cold-1", "cold-2") },
        ).picker

        a.addTo(playlist)
        advanceUntilIdle()

        coVerify { playlistRepository.addItemsToPlaylist("p1", listOf("cold-1", "cold-2")) }
    }

    @Test
    fun `addTo repository failure emits couldnt-add message`() = runTest {
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } returns Result.failure(RuntimeException("server"))
        val a = targets(this).picker

        a.addTo(Playlist(id = "p1", name = "Favs", canEdit = true))
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_couldnt_add_to_playlist))
            )
        )
        assertFalse(a.state.value.isAdding)
        assertFalse(a.state.value.showPicker)
    }
    // endregion

    // region addToWatchLater — cached id reuse + first-use creation
    @Test
    fun `addToWatchLater with cached id reuses it and adds the items`() = runTest {
        every { appRuntimeStateStore.state } returns
            MutableStateFlow(AppRuntimeState(watchLaterPlaylistId = "wl-1"))
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(emptyList())
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val t = targets(this)
        val a = t.watchLater

        // The Watch-Later row lives in the picker sheet — open it first so the
        // quick action's sheet choreography is observable.
        t.picker.openPicker()
        advanceUntilIdle()
        assertTrue(t.picker.state.value.showPicker)

        a.addToWatchLater()
        advanceUntilIdle()

        // Reuses the cached Watch Later id; never creates a new playlist.
        coVerify { playlistRepository.addItemsToPlaylist("wl-1", listOf("m1")) }
        coVerify(exactly = 0) { playlistRepository.createPlaylist(any(), any(), any(), any()) }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_added_to_watch_later))
            )
        )
        // Rides the picker sheet's flag: spinner cleared, sheet closed on settle.
        assertFalse(t.picker.state.value.isAdding)
        assertFalse(t.picker.state.value.showPicker)
    }

    @Test
    fun `addToWatchLater without cached id creates the playlist and persists its id`() = runTest {
        every { appRuntimeStateStore.state } returns
            MutableStateFlow(AppRuntimeState(watchLaterPlaylistId = null))
        coEvery {
            playlistRepository.createPlaylist(any(), any(), any(), any())
        } returns Result.success("wl-new")

        val a = targets(this).watchLater
        a.addToWatchLater()
        advanceUntilIdle()

        // Creates the Watch Later playlist seeded with the item and caches its id.
        coVerify {
            playlistRepository.createPlaylist(
                strings.get(Res.string.detail_playlist_watch_later),
                null,
                listOf("m1"),
                MediaType.MOVIE,
            )
        }
        coVerify { appRuntimeStateStore.setWatchLaterPlaylistId("wl-new") }
        coVerify(exactly = 0) { playlistRepository.addItemsToPlaylist(any(), any()) }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_added_to_watch_later))
            )
        )
    }

    @Test
    fun `addToWatchLater with no resolvable ids emits no-episodes-queued`() = runTest {
        every { appRuntimeStateStore.state } returns
            MutableStateFlow(AppRuntimeState(watchLaterPlaylistId = "wl-1"))
        val a = targets(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        ).watchLater

        a.addToWatchLater()
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_no_episodes_queued))
            )
        )
        coVerify(exactly = 0) { playlistRepository.addItemsToPlaylist(any(), any()) }
    }
    // endregion

    // region createAndAdd
    @Test
    fun `createAndAdd success creates the playlist and closes the dialog`() = runTest {
        coEvery {
            playlistRepository.createPlaylist(any(), any(), any(), any())
        } returns Result.success("pl-new")
        val a = targets(this).picker
        a.openCreateDialog()
        assertTrue(a.state.value.showCreateDialog)

        a.createAndAdd(" My List ", "overview text")
        advanceUntilIdle()

        // Name is trimmed; overview passes through; dialog closes on success.
        coVerify {
            playlistRepository.createPlaylist("My List", "overview text", listOf("m1"), MediaType.MOVIE)
        }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_playlist_created, "My List"))
            )
        )
        assertFalse(a.state.value.showCreateDialog)
        assertFalse(a.state.value.isAdding)
    }

    @Test
    fun `createAndAdd with blank name is a no-op`() = runTest {
        val a = targets(this).picker
        a.openCreateDialog()

        a.createAndAdd("   ", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.createPlaylist(any(), any(), any(), any()) }
        // Dialog stays open; the guard fired before any state mutation.
        assertTrue(a.state.value.showCreateDialog)
    }

    @Test
    fun `createAndAdd with no loaded detail is a no-op`() = runTest {
        val a = targets(this, detail = null).picker

        a.createAndAdd("Name", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.createPlaylist(any(), any(), any(), any()) }
    }

    @Test
    fun `createAndAdd with blank overview passes null overview`() = runTest {
        coEvery { playlistRepository.createPlaylist(any(), any(), any(), any()) } returns Result.success("pl-new")
        val a = targets(this).picker

        a.createAndAdd("Name", "   ")
        advanceUntilIdle()

        // A blank overview is normalized to null.
        coVerify { playlistRepository.createPlaylist("Name", null, listOf("m1"), MediaType.MOVIE) }
    }

    @Test
    fun `createAndAdd for a series with no episodes emits no-episodes-queued and skips create`() = runTest {
        // The drift this merge fixes: the playlist create path used to create
        // an EMPTY playlist here while claiming success. The shared module
        // guards both adapters; pin the playlist side.
        val a = targets(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        ).picker
        a.openCreateDialog()

        a.createAndAdd("Name")
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_no_episodes_queued))
            )
        )
        coVerify(exactly = 0) { playlistRepository.createPlaylist(any(), any(), any(), any()) }
        assertFalse(a.state.value.showCreateDialog)
        assertFalse(a.state.value.isAdding)
    }
    // endregion
}
