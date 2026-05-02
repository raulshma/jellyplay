package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo

interface PlaybackRepository {

    suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit>

    suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit>

    suspend fun reportPlaybackStopped(itemId: String, sessionId: String, positionTicks: Long): Result<Unit>

    fun getImageUrl(itemId: String, imageType: String = "Primary", maxWidth: Int = 400): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = 1280): String

    fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long = 0): String

    fun getSubtitleDeliveryUrl(deliveryUrl: String): String
}
