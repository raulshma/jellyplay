package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataRefreshOption
import com.raulshma.jellyplay.core.model.MetadataRefreshParams
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetadataAdminActionsTest {

    private val editorRepository: MetadataEditorRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()

    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messages.reset()
        coEvery { editorRepository.refreshItemMetadata(any(), any<com.raulshma.jellyplay.core.model.MetadataRefreshParams>()) } returns Result.success(Unit)
    }

    private fun actions(
        scope: kotlinx.coroutines.CoroutineScope? = null,
        session: MutableStateFlow<DetailSession?> = MutableStateFlow(null),
        isAdmin: Boolean = false,
        onRefreshAccepted: () -> Unit = {},
    ): MetadataAdminActions {
        every { authRepository.currentUser } returns flowOf(
            UserInfo(id = "u1", name = "u", serverAddress = "http://s", accessToken = "t", isAdmin = isAdmin),
        )
        return MetadataAdminActions(
            scope = scope ?: kotlinx.coroutines.test.TestScope(),
            session = session,
            messages = messages.flow,
            strings = strings,
            editorRepository = editorRepository,
            mediaRepository = mockk<MediaRepository>(relaxed = true),
            onRefreshAccepted = onRefreshAccepted,
            authRepository = authRepository,
        )
    }

    @Test
    fun `refreshScreenItem maps the option onto the refresh endpoint params`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Series", mediaType = MediaType.SERIES),
        )
        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail)))
            .refreshScreenItem(MetadataRefreshOption.REPLACE_ALL_METADATA)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            editorRepository.refreshItemMetadata("m1", MetadataRefreshParams("FullRefresh", "FullRefresh", replaceAllMetadata = true, replaceAllImages = false))
        }
    }

    @Test
    fun `refresh success surfaces a started message`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Series", mediaType = MediaType.SERIES),
        )
        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail)))
            .refreshScreenItem(MetadataRefreshOption.DEFAULT)
        advanceUntilIdle()

        val emitted = messages.recorded
        assertEquals(1, emitted.size)
        assertTrue(emitted.single() is DetailMessage.Text)
    }

    @Test
    fun `refresh failure surfaces a message`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Series", mediaType = MediaType.SERIES),
        )
        coEvery { editorRepository.refreshItemMetadata(any(), any<com.raulshma.jellyplay.core.model.MetadataRefreshParams>()) } returns
            Result.failure(IllegalStateException("boom"))

        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail)))
            .refreshScreenItem(MetadataRefreshOption.DEFAULT)
        advanceUntilIdle()

        assertEquals(1, messages.recorded.size)
    }

    @Test
    fun `refresh without a loaded session is a no-op`() = runTest {
        actions(this).refreshScreenItem(MetadataRefreshOption.DEFAULT)
        advanceUntilIdle()

        coVerify(exactly = 0) { editorRepository.refreshItemMetadata(any(), any<com.raulshma.jellyplay.core.model.MetadataRefreshParams>()) }
    }

    @Test
    fun `accepted refresh schedules exactly one delayed screen reload`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Series", mediaType = MediaType.SERIES),
        )
        var reloads = 0
        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail)), onRefreshAccepted = { reloads++ })
            .refreshScreenItem(MetadataRefreshOption.DEFAULT)
        advanceUntilIdle()

        assertEquals(1, reloads)
    }

    @Test
    fun `a newer refresh replaces the pending reload`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Series", mediaType = MediaType.SERIES),
        )
        var reloads = 0
        val session: MutableStateFlow<DetailSession?> = MutableStateFlow(DetailSession(itemId = "m1", detail = detail))
        val admin = actions(this, session, onRefreshAccepted = { reloads++ })
        admin.refreshScreenItem(MetadataRefreshOption.DEFAULT)
        advanceTimeBy(2_500)
        admin.refreshScreenItem(MetadataRefreshOption.REPLACE_IMAGES)
        advanceUntilIdle()

        assertEquals(1, reloads)
    }

    @Test
    fun `failed refresh schedules no reload`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Series", mediaType = MediaType.SERIES),
        )
        coEvery { editorRepository.refreshItemMetadata(any(), any<com.raulshma.jellyplay.core.model.MetadataRefreshParams>()) } returns
            Result.failure(IllegalStateException("boom"))
        var reloads = 0
        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail)), onRefreshAccepted = { reloads++ })
            .refreshScreenItem(MetadataRefreshOption.DEFAULT)
        advanceUntilIdle()

        assertEquals(0, reloads)
    }

    @Test
    fun `isAdmin folds the current user`() = runTest {
        assertTrue(actions(isAdmin = true).isAdmin.first())
        assertFalse(actions(isAdmin = false).isAdmin.first())
    }
}
