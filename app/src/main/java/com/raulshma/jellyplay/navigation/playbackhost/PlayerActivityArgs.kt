package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.PlayerActivity

/**
 * Typed launch contract for [PlayerActivity] — the single adapter that both
 * **builds** and **parses** the intent extras the activity consumes.
 *
 * Producers and consumers that previously joined only through loose string
 * literals (the intent-building block formerly in JellyPlayApp's
 * `navigateFilter` and PlayerActivity's own hand-written parser) now share
 * this one file:
 *
 *  - [buildIntent] writes the extras for the dedicated-activity host
 *    ([PlaybackHostRouter] answers [HostDecision.DedicatedActivity]).
 *  - [fromIntent] reads them back in PlayerActivity's `onCreate` /
 *    `onNewIntent`, returning `null` when the variant's mandatory id is
 *    absent (the activity's `finish()` path depends on that null).
 *
 * Wave 19C (live PiP): the contract is sealed — the dedicated host now also
 * mounts Live TV (so system PiP serves live), and a live launch carries a
 * different payload than the video-shaped one:
 *
 *  - [Video] — the original five-extras item contract (`itemId`,
 *    `mediaSourceId`, `startPositionTicks`, stream indexes).
 *  - [Live] — `channelId` + `channelName` + the same stream-index extras
 *    (identical keys and `>= 0` sentinel as the video variant — one parser
 *    helper serves both). `channelId` deliberately does NOT ride
 *    [EXTRA_ITEM_ID]: a live channel is not a library item, and the
 *    `MediaSessionController` mirror keyed on `player_item_id` must keep
 *    meaning video items only.
 *
 * Wire format: every intent written by [buildIntent] carries a
 * [EXTRA_VARIANT] discriminator (`"video"` / `"live"`). [fromIntent] with a
 * missing/unknown discriminator falls back to the video parse — byte-for-byte
 * compatible with the pre-seal five-extras format, so a foreign producer
 * that only writes `"player_item_id"` (the media-notification content
 * intent, mirrored by value in `MediaSessionController`) still round-trips
 * as a [Video] with defaulted fields.
 *
 * `MediaSessionController` (`:feature:player:video`, which cannot see the app
 * module) still mirrors `"player_item_id"` by value for the media-notification
 * content intent — its comment names this class as the contract owner, and the
 * round-trip test pins the literal both sides must agree on.
 */
sealed class PlayerActivityArgs {

    /**
     * VOD/library-item playback — the original five-extras contract, mounted
     * as [com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen].
     */
    data class Video(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0L,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : PlayerActivityArgs()

    /**
     * Live-TV channel playback (wave 19C), mounted as
     * [com.raulshma.jellyplay.feature.player.live.LivePlayerScreen]. No
     * `startPositionTicks`: a live stream has no resume position (mirrors
     * the external-player handoff, which passes zero for live).
     */
    data class Live(
        val channelId: String,
        val channelName: String,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : PlayerActivityArgs()

    /** Build the launch [Intent] for [PlayerActivity] carrying these args. */
    fun buildIntent(context: Context): Intent =
        Intent(context, PlayerActivity::class.java).also(::writeTo)

    private fun writeTo(intent: Intent) {
        when (this) {
            is Video -> {
                intent.putExtra(EXTRA_VARIANT, VARIANT_VIDEO)
                intent.putExtra(EXTRA_ITEM_ID, itemId)
                mediaSourceId?.let { intent.putExtra(EXTRA_MEDIA_SOURCE_ID, it) }
                intent.putExtra(EXTRA_START_POSITION_TICKS, startPositionTicks)
                writeStreamIndexes(intent, subtitleStreamIndex, audioStreamIndex)
            }
            is Live -> {
                intent.putExtra(EXTRA_VARIANT, VARIANT_LIVE)
                intent.putExtra(EXTRA_CHANNEL_ID, channelId)
                intent.putExtra(EXTRA_CHANNEL_NAME, channelName)
                writeStreamIndexes(intent, subtitleStreamIndex, audioStreamIndex)
            }
        }
    }

    companion object {
        const val EXTRA_VARIANT = "player_args_variant"
        const val VARIANT_VIDEO = "video"
        const val VARIANT_LIVE = "live"
        const val EXTRA_ITEM_ID = "player_item_id"
        const val EXTRA_MEDIA_SOURCE_ID = "player_media_source_id"
        const val EXTRA_START_POSITION_TICKS = "player_start_position_ticks"
        const val EXTRA_SUBTITLE_STREAM_INDEX = "player_subtitle_stream_index"
        const val EXTRA_AUDIO_STREAM_INDEX = "player_audio_stream_index"
        const val EXTRA_CHANNEL_ID = "player_channel_id"
        const val EXTRA_CHANNEL_NAME = "player_channel_name"

        /**
         * Parse the launch args from a start/new [Intent]. Returns `null` when
         * the variant's mandatory id is absent — PlayerActivity finishes
         * itself in that case. A missing/unknown [EXTRA_VARIANT] parses as
         * [Video] (backward compatibility with pre-seal producers).
         */
        fun fromIntent(intent: Intent): PlayerActivityArgs? = when (
            intent.getStringExtra(EXTRA_VARIANT)
        ) {
            VARIANT_LIVE -> liveFrom(intent)
            else -> videoFrom(intent)
        }

        private fun videoFrom(intent: Intent): Video? {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return null
            return Video(
                itemId = itemId,
                mediaSourceId = intent.getStringExtra(EXTRA_MEDIA_SOURCE_ID),
                startPositionTicks = intent.getLongExtra(EXTRA_START_POSITION_TICKS, 0L),
                subtitleStreamIndex = intent.readStreamIndex(EXTRA_SUBTITLE_STREAM_INDEX),
                audioStreamIndex = intent.readStreamIndex(EXTRA_AUDIO_STREAM_INDEX),
            )
        }

        private fun liveFrom(intent: Intent): Live? {
            val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: return null
            return Live(
                channelId = channelId,
                channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty(),
                subtitleStreamIndex = intent.readStreamIndex(EXTRA_SUBTITLE_STREAM_INDEX),
                audioStreamIndex = intent.readStreamIndex(EXTRA_AUDIO_STREAM_INDEX),
            )
        }

        /**
         * Stream overrides share one pair of extras (and the `>= 0` sentinel
         * read) across both variants — a missing extra round-trips to null.
         */
        private fun writeStreamIndexes(intent: Intent, subtitleStreamIndex: Int?, audioStreamIndex: Int?) {
            subtitleStreamIndex?.let { intent.putExtra(EXTRA_SUBTITLE_STREAM_INDEX, it) }
            audioStreamIndex?.let { intent.putExtra(EXTRA_AUDIO_STREAM_INDEX, it) }
        }

        private fun Intent.readStreamIndex(key: String): Int? =
            getIntExtra(key, -1).takeIf { it >= 0 }
    }
}
