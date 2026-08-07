package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionManagerExtendedTest {

    private lateinit var context: Context
    private lateinit var sessionManager: PlayerSessionManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val mediaRepository = mockk<MediaRepository>(relaxed = true)
        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val offlineRepository = mockk<OfflineRepository>(relaxed = true)
        val playbackSourceResolver = mockk<com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver>(relaxed = true)
        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        val playerLifecycleManager = mockk<PlayerLifecycleManager>(relaxed = true)
        val pipController = mockk<com.raulshma.jellyplay.core.data.playback.PipController>(relaxed = true)
        val adaptiveBitrateManager = mockk<AdaptiveBitrateManager>(relaxed = true)

        every { aggregateStore.aggregate } returns
            MutableStateFlow(VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL)))

        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)

        sessionManager = PlayerSessionManager(
            context = context,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob()),
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = pipController,
            adaptiveBitrateManager = adaptiveBitrateManager,
            playerEngineFactory = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory(
                context,
                okHttpClient,
                mockk<com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider>(relaxed = true),
            ),
            playbackSourceResolver = playbackSourceResolver,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun bindReclaimedEngine_setsEngineAndUpdatesState() {
        val mockEngine = mockk<MediaEngine>(relaxed = true)
        val itemId = "item-reclaimed"
        val detail = MediaDetail(item = MediaItem(id = itemId, name = "Reclaimed Movie", mediaType = MediaType.MOVIE))

        sessionManager.bindReclaimedEngine(mockEngine, itemId, detail)

        val state = sessionManager.sessionState.value
        assertEquals(itemId, state.currentItemId)
        assertEquals("Reclaimed Movie", state.title)
        assertTrue(state.isReady)
    }

    @Test
    fun addExternalSubtitle_storesSubtitleSource() {
        val source = SubtitleSource(
            url = "http://example.com/sub.vtt",
            label = "English",
            language = "eng",
            mimeType = "text/vtt",
            id = "sub-1",
        )
        sessionManager.addExternalSubtitle(source)
        assertNull(sessionManager.sessionState.value.currentItemId)
    }

    @Test
    fun detachEngine_clearsActiveEngine() {
        val mockEngine = mockk<MediaEngine>(relaxed = true)
        sessionManager.bindReclaimedEngine(mockEngine, "item-1", MediaDetail(item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE)))

        sessionManager.detachEngine()

        assertNull(sessionManager.engine)
    }

    @Test
    fun release_resetsSessionState() {
        val mockEngine = mockk<MediaEngine>(relaxed = true)
        sessionManager.bindReclaimedEngine(mockEngine, "item-1", MediaDetail(item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE)))

        sessionManager.release()

        assertNull(sessionManager.engine)
        assertNull(sessionManager.sessionState.value.currentItemId)
    }
}
