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
 * adapter that builds and parses PlayerActivity's five intent extras.
 *
 * `buildIntent` + `fromIntent` must be byte-compatible with the hand-written
 * build/parse pair this class replaced (same keys, same presence rules), and
 * [fromIntent] must return null when the mandatory item id is absent —
 * PlayerActivity's `finish()` path depends on that null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlayerActivityArgsTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `full args round-trip`() {
        val args = PlayerActivityArgs(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startPositionTicks = 10_000_000L,
            subtitleStreamIndex = 3,
            audioStreamIndex = 1,
        )

        assertEquals(args, PlayerActivityArgs.fromIntent(args.buildIntent(context)))
    }

    @Test
    fun `minimal args round-trip`() {
        val args = PlayerActivityArgs(itemId = "item-min")

        val parsed = PlayerActivityArgs.fromIntent(args.buildIntent(context))

        assertEquals(args, parsed)
        assertNotNull(parsed)
        assertNull(parsed!!.mediaSourceId)
        assertEquals(0L, parsed.startPositionTicks)
        assertNull(parsed.subtitleStreamIndex)
        assertNull(parsed.audioStreamIndex)
    }

    @Test
    fun `stream indexes are written only when non-null and read back through the sentinel`() {
        // Presence rules: a null stream index must not appear in the intent,
        // and a missing extra must parse back to null (the `>= 0` sentinel).
        val args = PlayerActivityArgs(
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

        val parsed = PlayerActivityArgs.fromIntent(intent)

        assertNotNull(parsed)
        assertNull(parsed!!.subtitleStreamIndex)
        assertNull(parsed.audioStreamIndex)
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
        val intent = argsFull().buildIntent(context)

        assertEquals(
            com.raulshma.jellyplay.PlayerActivity::class.java.name,
            intent.component?.className,
        )
    }

    private fun argsFull() = PlayerActivityArgs(
        itemId = "item-1",
        mediaSourceId = "source-1",
        startPositionTicks = 10_000_000L,
        subtitleStreamIndex = 3,
        audioStreamIndex = 1,
    )
}
