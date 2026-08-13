package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.isAudioType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns the "Start instant mix" concern for the detail screen. A plain helper
 * class (no `@Inject`) constructed by [DetailViewModel], structurally a mirror
 * of [PlaylistActions] / [CollectionActions]: coroutines launch on the supplied
 * [scope] and user-facing messages push through [messageSink] so the helper
 * owns no message channel of its own.
 *
 * Fire-and-forget: success is implicit (playback starts) and there is no
 * StateFlow to fold into uiState — the only UI feedback is the failure / empty
 * snackbar emitted via [DetailMessage]. Extracted from [DetailViewModel] to
 * keep the one-shot audio concern out of the (already large) ViewModel, per the
 * detail-screen helper-class standard.
 */
internal class InstantMixActions(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val context: Context,
    private val detailProvider: () -> MediaDetail?,
    private val currentItemProvider: () -> String?,
    private val messageSink: (DetailMessage) -> Unit,
) {
    /**
     * One-shot "Start instant mix" action for audio-type items. Fetches a
     * Jellyfin-built mix seeded off the current item via
     * [MediaRepository.getInstantMix] and hands it straight to
     * [AudioPlaybackManager.playQueue] at index 0. Fire-and-forget: success is
     * implicit (playback starts) and the only UI feedback is the failure / empty
     * snackbar emitted via [DetailMessage]. Mirrors `playAlbum`'s queue build +
     * dispatcher, and guards navigation drift so a mix resolved after the user
     * navigated away cannot start playback on the wrong screen.
     */
    fun startInstantMix() {
        val detail = detailProvider() ?: return
        val item = detail.item
        if (!item.mediaType.isAudioType) return
        val itemId = item.id
        val albumFallback = item.album ?: item.name
        // Queue construction builds N image URLs + N queue items; keep it off the
        // Main dispatcher, matching playAlbum (the click handler is non-suspend).
        scope.launch(Dispatchers.Default) {
            mediaRepository.getInstantMix(itemId)
                .onSuccess { mix ->
                    // Don't start a mix for a screen the user has already left.
                    if (currentItemProvider() != itemId) return@onSuccess
                    if (mix.isEmpty()) {
                        messageSink(DetailMessage.Text(context.getString(R.string.detail_instant_mix_empty)))
                        return@onSuccess
                    }
                    val queueItems = mix.map { track ->
                        track.toAudioQueueItem(
                            imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                            albumFallback = albumFallback,
                        )
                    }
                    audioPlaybackManager.playQueue(queueItems, 0)
                }
                .onFailure {
                    messageSink(DetailMessage.Text(context.getString(R.string.detail_instant_mix_failed)))
                }
        }
    }
}
