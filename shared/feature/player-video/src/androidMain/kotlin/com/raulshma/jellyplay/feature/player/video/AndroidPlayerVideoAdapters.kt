package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import com.raulshma.jellyplay.core.ui.feedback.uiTextOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Android adapter over the Hilt-owned legacy `core:data`
 * ActivePlayerController singleton (wave 8C seam): the registry remote-control
 * paths read to drive playback without a ViewModel reference.
 */
internal class AndroidActivePlayerController(
    private val delegate: ActivePlayerController,
) : com.raulshma.jellyplay.feature.player.video.ActivePlayerController {

    override val engine: RemotePlayableEngine? get() = delegate.engine

    override fun bindEngine(engine: RemotePlayableEngine) = delegate.bindEngine(engine)

    override fun clearEngine() = delegate.clearEngine()
}

/**
 * Android adapter over the Hilt-owned legacy "Play On" strategy (wave 8C
 * seam): the commonMain interface keeps the isConnected gate + loadMedia
 * handoff the ViewModel's remote-play routing early-return needs.
 */
internal class AndroidJellyfinRemotePlayCastStrategy(
    private val delegate: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy,
) : JellyfinRemotePlayCastStrategy {

    override val isConnected: StateFlow<Boolean> get() = delegate.isConnected

    override fun loadMedia(
        itemId: String,
        startPositionMs: Long,
        mediaSourceId: String?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) = delegate.loadMedia(
        itemId = itemId,
        startPositionMs = startPositionMs,
        mediaSourceId = mediaSourceId,
        audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex,
    )
}

/**
 * Android actual of the [VideoMediaSessionFactory] seam (wave 8C): constructs
 * the androidMain [AndroidMediaSessionController] with the app [Context] and
 * the Hilt-owned legacy [PlaybackSessionManager] the ViewModel used to take
 * as a constructor dependency — the legacy type now lives entirely on the
 * Android side of the seam. [getPlayer]'s opaque handle is narrowed to media3
 * `Player?` inside the controller.
 */
internal class AndroidMediaSessionFactory(
    private val context: Context,
    private val sessionManager: PlaybackSessionManager,
) : VideoMediaSessionFactory {

    override fun create(
        getPlayer: () -> Any?,
        getImageUrl: (itemId: String, maxWidth: Int) -> String,
    ): MediaSessionController = AndroidMediaSessionController(
        context = context,
        sessionManager = sessionManager,
        getPlayer = { getPlayer() as? androidx.media3.common.Player },
        getImageUrl = getImageUrl,
    )
}

/**
 * Android adapter bridging the module-local [PlayerVideoMessageBus] seam onto
 * the Hilt-owned legacy `core:ui` UserMessageBus (wave 8C seam; MusicMessageBus
 * precedent): strings post as UiText.Raw, the [PlayerVideoMessage.SmartDownloadDeleted]
 * seal resolves the legacy string entry the ViewModel used to build inline —
 * the resource stays in the legacy table (no string files touched).
 */
internal class AndroidUserMessageBridge(
    private val delegate: com.raulshma.jellyplay.core.ui.feedback.UserMessageBus,
) : PlayerVideoMessageBus {

    override fun info(message: String) = delegate.info(message)

    override fun error(message: String) = delegate.error(message)

    override fun info(message: PlayerVideoMessage) {
        when (message) {
            PlayerVideoMessage.SmartDownloadDeleted -> delegate.info(
                uiTextOf(com.raulshma.jellyplay.core.ui.R.string.msg_smart_download_deleted),
            )
        }
    }
}
