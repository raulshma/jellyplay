package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.PlayerActivity

/**
 * Typed launch contract for [PlayerActivity] — the single adapter that both
 * **builds** and **parses** the five intent extras the activity consumes.
 *
 * Producers and consumers that previously joined only through loose string
 * literals (the intent-building block formerly in JellyPlayApp's
 * `navigateFilter` and PlayerActivity's own hand-written parser) now share
 * this one file:
 *
 *  - [buildIntent] writes the extras for the dedicated-activity host
 *    ([PlaybackHostRouter] answers [HostDecision.DedicatedActivity]).
 *  - [fromIntent] reads them back in PlayerActivity's `onCreate` /
 *    `onNewIntent`, returning `null` when the mandatory item id is absent
 *    (the activity's `finish()` path depends on that null).
 *
 * Byte-for-byte compatible with the hand-written adapters it replaces: same
 * keys, same presence rules (`mediaSourceId` and the stream indexes are
 * written only when non-null; the indexes are read back through the
 * `>= 0` sentinel so a missing extra round-trips to `null`).
 *
 * `MediaSessionController` (`:feature:player:video`, which cannot see the app
 * module) still mirrors `"player_item_id"` by value for the media-notification
 * content intent — its comment names this class as the contract owner, and the
 * round-trip test pins the literal both sides must agree on.
 */
data class PlayerActivityArgs(
    val itemId: String,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0L,
    val subtitleStreamIndex: Int? = null,
    val audioStreamIndex: Int? = null,
) {
    /** Build the launch [Intent] for [PlayerActivity] carrying these args. */
    fun buildIntent(context: Context): Intent =
        Intent(context, PlayerActivity::class.java).also(::writeTo)

    private fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_ITEM_ID, itemId)
        mediaSourceId?.let { intent.putExtra(EXTRA_MEDIA_SOURCE_ID, it) }
        intent.putExtra(EXTRA_START_POSITION_TICKS, startPositionTicks)
        subtitleStreamIndex?.let { intent.putExtra(EXTRA_SUBTITLE_STREAM_INDEX, it) }
        audioStreamIndex?.let { intent.putExtra(EXTRA_AUDIO_STREAM_INDEX, it) }
    }

    companion object {
        const val EXTRA_ITEM_ID = "player_item_id"
        const val EXTRA_MEDIA_SOURCE_ID = "player_media_source_id"
        const val EXTRA_START_POSITION_TICKS = "player_start_position_ticks"
        const val EXTRA_SUBTITLE_STREAM_INDEX = "player_subtitle_stream_index"
        const val EXTRA_AUDIO_STREAM_INDEX = "player_audio_stream_index"

        /**
         * Parse the launch args from a start/new [Intent]. Returns `null` when
         * the mandatory [EXTRA_ITEM_ID] is absent — PlayerActivity finishes
         * itself in that case.
         */
        fun fromIntent(intent: Intent): PlayerActivityArgs? {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return null
            return PlayerActivityArgs(
                itemId = itemId,
                mediaSourceId = intent.getStringExtra(EXTRA_MEDIA_SOURCE_ID),
                startPositionTicks = intent.getLongExtra(EXTRA_START_POSITION_TICKS, 0L),
                subtitleStreamIndex = intent.getIntExtra(EXTRA_SUBTITLE_STREAM_INDEX, -1)
                    .takeIf { it >= 0 },
                audioStreamIndex = intent.getIntExtra(EXTRA_AUDIO_STREAM_INDEX, -1)
                    .takeIf { it >= 0 },
            )
        }
    }
}
