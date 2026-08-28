package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Round-trip pins for the [PlayerActivityArgs] launch contract — the single
 * adapter that builds and parses PlayerActivity's intent extras.
 *
 * `buildIntent` + `fromIntent` must be byte-compatible with the hand-written
 * build/parse pair this class replaced (same keys, same presence rules), and
 * [fromIntent] must return null when the variant's mandatory id is absent —
 * PlayerActivity's `finish()` path depends on that null. Wave 19C sealed the
 * contract into a Video/Live pair; the pins cover both variants, the
 * variant-discriminator extra, and the pre-seal backward-compat fallback
 * (no discriminator → Video).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlayerActivityArgsTest {

    private val context = RuntimeEnvironment.getApplication()

    // ── Video variant ──────────────────────────────────────────────────────

    @Test
    fun `full video args round-trip`() {
        val args = PlayerActivityArgs.Video(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startPositionTicks = 10_000_000L,
            subtitleStreamIndex = 3,
            audioStreamIndex = 1,
        )

        assertEquals(args, PlayerActivityArgs.fromIntent(args.buildIntent(context)))
    }

    @Test
    fun `minimal video args round-trip`() {
        val args = PlayerActivityArgs.Video(itemId = "item-min")

        val parsed = PlayerActivityArgs.fromIntent(args.buildIntent(context))

        // Equality above already proves the variant; narrow for field pins.
        parsed as PlayerActivityArgs.Video
        assertNull(parsed.mediaSourceId)
        assertEquals(0L, parsed.startPositionTicks)
        assertNull(parsed.subtitleStreamIndex)
        assertNull(parsed.audioStreamIndex)
    }

    @Test
    fun `stream indexes are written only when non-null and read back through the sentinel`() {
        // Presence rules: a null stream index must not appear in the intent,
        // and a missing extra must parse back to null (the `>= 0` sentinel).
        val args = PlayerActivityArgs.Video(
            itemId = "item-2",
            subtitleStreamIndex = null,
            audioStreamIndex = 7,
        )

        val intent = args.buildIntent(context)
        assertFalse(intent.hasExtra(PlayerActivityArgs.EXTRA_SUBTITLE_STREAM_INDEX))
        assertTrue(intent.hasExtra(PlayerActivityArgs.EXTRA_AUDIO_STREAM_INDEX))
        assertEquals(args, PlayerActivityArgs.fromIntent(intent))
    }

    @Test
    fun `fromIntent returns null when the item id extra is absent`() {
        val intent = Intent(context, com.raulshma.jellyplay.PlayerActivity::class.java)
            .putExtra(PlayerActivityArgs.EXTRA_START_POSITION_TICKS, 5L)

        assertNull(PlayerActivityArgs.fromIntent(intent))
    }

    @Test
    fun `fromIntent tolerates non-numeric junk in optional extras`() {
        // A foreign producer (e.g. a hand-built intent) may carry junk under
        // our keys; the parse must degrade, not throw.
        val intent = argsFull().buildIntent(context).apply {
            putExtra(PlayerActivityArgs.EXTRA_SUBTITLE_STREAM_INDEX, -1)
            putExtra(PlayerActivityArgs.EXTRA_AUDIO_STREAM_INDEX, -1)
        }

        val parsed = PlayerActivityArgs.fromIntent(intent) as? PlayerActivityArgs.Video

        assertNotNull(parsed)
        assertNull(parsed!!.subtitleStreamIndex)
        assertNull(parsed.audioStreamIndex)
    }

    // ── Live variant (wave 19C live PiP) ───────────────────────────────────

    @Test
    fun `full live args round-trip`() {
        val args = PlayerActivityArgs.Live(
            channelId = "chan-1",
            channelName = "Channel One",
            subtitleStreamIndex = 4,
            audioStreamIndex = 2,
        )

        assertEquals(args, PlayerActivityArgs.fromIntent(args.buildIntent(context)))
    }

    @Test
    fun `minimal live args round-trip with defaulted channel name and stream indexes`() {
        val args = PlayerActivityArgs.Live(channelId = "chan-2", channelName = "")

        val parsed = PlayerActivityArgs.fromIntent(args.buildIntent(context))

        // Equality above already proves the variant; narrow for field pins.
        parsed as PlayerActivityArgs.Live
        assertEquals("chan-2", parsed.channelId)
        assertEquals("", parsed.channelName)
        assertNull(parsed.subtitleStreamIndex)
        assertNull(parsed.audioStreamIndex)
    }

    @Test
    fun `live intent reuses the stream-index extras and leaves the item id unset`() {
        // The channel id deliberately rides its own extra, NOT player_item_id:
        // that key must keep meaning library items only (the
        // MediaSessionController notification mirror parses it as a Video).
        val intent = PlayerActivityArgs.Live(
            channelId = "chan-1",
            channelName = "Channel One",
            audioStreamIndex = 2,
        ).buildIntent(context)

        assertTrue(intent.hasExtra(PlayerActivityArgs.EXTRA_CHANNEL_ID))
        assertTrue(intent.hasExtra(PlayerActivityArgs.EXTRA_AUDIO_STREAM_INDEX))
        assertFalse(intent.hasExtra(PlayerActivityArgs.EXTRA_ITEM_ID))
        assertFalse(intent.hasExtra(PlayerActivityArgs.EXTRA_START_POSITION_TICKS))
        assertEquals(
            PlayerActivityArgs.Live(channelId = "chan-1", channelName = "Channel One", audioStreamIndex = 2),
            PlayerActivityArgs.fromIntent(intent),
        )
    }

    @Test
    fun `live intent without a channel id parses to null`() {
        // Mandatory id for the live variant is the channel id; its absence is
        // the activity's finish() path, same contract as a video intent
        // without an item id.
        val intent = Intent(context, com.raulshma.jellyplay.PlayerActivity::class.java)
            .putExtra(PlayerActivityArgs.EXTRA_VARIANT, PlayerActivityArgs.VARIANT_LIVE)
            .putExtra(PlayerActivityArgs.EXTRA_CHANNEL_NAME, "orphan")

        assertNull(PlayerActivityArgs.fromIntent(intent))
    }

    // ── Variant discriminator + backward compatibility ─────────────────────

    @Test
    fun `intent without a variant discriminator parses as video`() {
        // Backward-compat pin: a foreign producer that only writes the
        // pre-seal extras (the media-notification content intent mirrors
        // player_item_id by value) must keep round-tripping as a Video.
        val intent = Intent(context, com.raulshma.jellyplay.PlayerActivity::class.java)
            .putExtra(PlayerActivityArgs.EXTRA_ITEM_ID, "item-1")

        assertEquals(
            PlayerActivityArgs.Video(itemId = "item-1"),
            PlayerActivityArgs.fromIntent(intent),
        )
    }

    @Test
    fun `both variants stamp the variant discriminator`() {
        val videoIntent = argsFull().buildIntent(context)
        val liveIntent = argsLiveFull().buildIntent(context)

        assertEquals(
            PlayerActivityArgs.VARIANT_VIDEO,
            videoIntent.getStringExtra(PlayerActivityArgs.EXTRA_VARIANT),
        )
        assertEquals(
            PlayerActivityArgs.VARIANT_LIVE,
            liveIntent.getStringExtra(PlayerActivityArgs.EXTRA_VARIANT),
        )
    }

    @Test
    fun `extra keys pin the values the MediaSessionController mirror must agree on`() {
        // `:feature:player:video` re-declares the item-id key by value
        // ("player_item_id") because it cannot see the app module. This pins
        // the literal both files must share so a rename cannot drift silently.
        assertEquals("player_item_id", PlayerActivityArgs.EXTRA_ITEM_ID)
        assertEquals("player_media_source_id", PlayerActivityArgs.EXTRA_MEDIA_SOURCE_ID)
        assertEquals("player_start_position_ticks", PlayerActivityArgs.EXTRA_START_POSITION_TICKS)
        assertEquals("player_subtitle_stream_index", PlayerActivityArgs.EXTRA_SUBTITLE_STREAM_INDEX)
        assertEquals("player_audio_stream_index", PlayerActivityArgs.EXTRA_AUDIO_STREAM_INDEX)
    }

    @Test
    fun `buildIntent targets PlayerActivity by class`() {
        val videoIntent = argsFull().buildIntent(context)
        val liveIntent = argsLiveFull().buildIntent(context)

        assertEquals(
            com.raulshma.jellyplay.PlayerActivity::class.java.name,
            videoIntent.component?.className,
        )
        assertEquals(
            com.raulshma.jellyplay.PlayerActivity::class.java.name,
            liveIntent.component?.className,
        )
    }

    private fun argsFull() = PlayerActivityArgs.Video(
        itemId = "item-1",
        mediaSourceId = "source-1",
        startPositionTicks = 10_000_000L,
        subtitleStreamIndex = 3,
        audioStreamIndex = 1,
    )

    private fun argsLiveFull() = PlayerActivityArgs.Live(
        channelId = "chan-1",
        channelName = "Channel One",
        subtitleStreamIndex = 4,
        audioStreamIndex = 2,
    )
}
