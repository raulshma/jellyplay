package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaSessionControllerTest {

    private lateinit var context: Context
    private lateinit var sessionManager: PlaybackSessionManager
    private lateinit var mockPlayer: Player
    private lateinit var controller: MediaSessionController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = mockk(relaxed = true)
        mockPlayer = mockk(relaxed = true)

        controller = MediaSessionController(
            context = context,
            sessionManager = sessionManager,
            getPlayer = { mockPlayer },
            getImageUrl = { itemId, _ -> "https://example.com/art/$itemId.jpg" },
        )
    }

    @Test
    fun createForItem_safelyHandlesSessionCreation() {
        runCatching {
            controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action Movie")
        }
    }

    @Test
    fun createForPlayer_safelyHandlesSessionCreation() {
        runCatching {
            controller.createForPlayer(mockPlayer, sessionId = "test_session_id")
        }
    }

    @Test
    fun release_clearsSessionFromManager() {
        runCatching {
            controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action Movie")
        }
        controller.release()
        controller.release()
    }
}
