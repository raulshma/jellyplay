package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserDataChange
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins [pagedGridQuickActions]' wiring — the one place the six
 * `PagedMediaGridHost` quick-action seams are typed (the four grid view
 * models spread the bundle verbatim, so a routing regression here would
 * multiply by four):
 *
 * - the seams surface the collaborators' values verbatim (change feed,
 *   downloaded-id flow, pipeline gate),
 * - [PagedGridQuickActions.setPlayedSilently] rides the mutator's silent
 *   default (the grid screens' contract),
 * - [PagedGridQuickActions.requestDownload] pre-binds the factory's
 *   [DownloadOutcomeMessenger] — and passes none when the factory got none,
 *   the favorites/studio/search shape,
 * - [PagedGridQuickActions.removeDownloadItem] keeps the shared
 *   series-vs-item delete routing.
 */
class PagedGridQuickActionsTest {

    private val userDataEvents =
        MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    private val mediaRepository: MediaRepository = mockk {
        every { userDataChanges } returns userDataEvents
    }
    private val userDataMutator: UserDataMutator = mockk(relaxed = true)
    private val quickDownloadActions: QuickDownloadActions = mockk(relaxed = true)
    private val messenger: DownloadOutcomeMessenger = mockk(relaxed = true)

    private fun item(id: String, type: MediaType) = MediaItem(
        id = id,
        name = id,
        mediaType = type,
    )

    // ── the seams surface the collaborators verbatim ─────────────────────────

    @Test
    fun `seams surface the change feed the id set and the pipeline gate`() {
        every { quickDownloadActions.downloadedIds } returns mockk()
        every { quickDownloadActions.isSupported } returns true

        val quickActions = pagedGridQuickActions(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            quickDownloadActions = quickDownloadActions,
        )

        assertSame(userDataEvents, quickActions.userDataChanges)
        assertSame(quickDownloadActions.downloadedIds, quickActions.downloadedIds)
        assertEquals(true, quickActions.downloadSupported)
    }

    // ── setPlayedSilently: the mutator's silent default ──────────────────────

    @Test
    fun `setPlayedSilently routes through the mutator's silent default`() = runTest {
        val quickActions = pagedGridQuickActions(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            quickDownloadActions = quickDownloadActions,
        )

        quickActions.setPlayedSilently("m1", true)

        coVerify(exactly = 1) {
            userDataMutator.setPlayed("m1", true, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
    }

    // ── requestDownload: the messenger pre-binding is the factory's one delta ──

    @Test
    fun `requestDownload pre-binds the factory's messenger`() = runTest {
        val quickActions = pagedGridQuickActions(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            quickDownloadActions = quickDownloadActions,
            messenger = messenger,
        )
        val onOpenDetail: (String, Boolean) -> Unit = { _, _ -> }

        quickActions.requestDownload(item("m1", MediaType.MOVIE), onOpenDetail, true)

        coVerify(exactly = 1) {
            quickDownloadActions.downloadAndReport(any(), onOpenDetail, true, messenger)
        }
    }

    @Test
    fun `requestDownload without a messenger passes none - the platform default`() = runTest {
        val quickActions = pagedGridQuickActions(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            quickDownloadActions = quickDownloadActions,
        )
        val onOpenDetail: (String, Boolean) -> Unit = { _, _ -> }

        quickActions.requestDownload(item("m1", MediaType.MOVIE), onOpenDetail, false)

        coVerify(exactly = 1) {
            quickDownloadActions.downloadAndReport(any(), onOpenDetail, false, null)
        }
    }

    // ── removeDownloadItem: the shared delete routing ────────────────────────

    @Test
    fun `removeDownloadItem delegates to the shared remove routing`() {
        val quickActions = pagedGridQuickActions(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            quickDownloadActions = quickDownloadActions,
        )
        val movie = item("m1", MediaType.MOVIE)

        quickActions.removeDownloadItem(movie)

        verify(exactly = 1) { quickDownloadActions.removeDownload(movie) }
    }

    @Test
    fun `seams default to an unsupported pipeline gate`() {
        every { quickDownloadActions.isSupported } returns false

        val quickActions = pagedGridQuickActions(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            quickDownloadActions = quickDownloadActions,
        )

        assertTrue(!quickActions.downloadSupported)
    }
}
