package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONException
import org.json.JSONObject

/**
 * Pins every rawType branch of [SyncPlayEventHandler.parse] against the
 * Jellyfin SyncPlay websocket payloads:
 *
 *  - `SyncPlayCommand` → [SyncPlayEvent.PlaybackCommand] for the four known
 *    commands (Unpause/Pause/Seek/Stop) with timestamp (ISO instant, `When`
 *    preferred over `EmittedAt`) and ticks (nested `{Value: n}` or plain long);
 *    an unknown command is null;
 *  - `SyncPlayGroupUpdate` → dispatched on the inner `Type`: PlayQueue,
 *    GroupUpdate/GroupJoined, StateUpdate (Waiting is NOT "playing" — only
 *    "Playing" counts, Paused stays in sync), GroupWait, UserJoined/UserLeft,
 *    GroupLeft/NotInGroup, the error notifications, and the unknown-type
 *    catch-all notification;
 *  - top-level `GroupJoined` / `GroupLeft`;
 *  - unknown rawType → null, and a payload that explodes mid-parse → null
 *    (never thrown into the websocket loop).
 */
class SyncPlayEventHandlerTest {

    private val handler = SyncPlayEventHandler()

    // ── SyncPlayCommand ─────────────────────────────────────────────────

    @Test
    fun `SyncPlayCommand Seek parses ticks timestamp playlist item and emittedAt`() {
        val data = JSONObject(
            """
            {
              "Command": "Seek",
              "PositionTicks": {"Value": 987654321},
              "When": "2026-01-02T03:04:05Z",
              "PlaylistItemId": "pl-9",
              "EmittedAt": "2026-01-02T03:04:06Z"
            }
            """.trimIndent(),
        )

        val event = handler.parse("SyncPlayCommand", data)

        val command = assertIs<SyncPlayEvent.PlaybackCommand>(event)
        assertEquals("Seek", command.cmd.command)
        assertEquals(987_654_321L, command.cmd.positionTicks)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), command.cmd.whenMs)
        assertEquals(Instant.parse("2026-01-02T03:04:06Z").toEpochMilli(), command.cmd.emittedAtMs)
        assertEquals("pl-9", command.cmd.playlistItemId)
    }

    @Test
    fun `SyncPlayCommand accepts plain long ticks`() {
        val data = JSONObject("""{"Command": "Pause", "PositionTicks": 42}""")

        val command = assertIs<SyncPlayEvent.PlaybackCommand>(handler.parse("SyncPlayCommand", data))

        assertEquals("Pause", command.cmd.command)
        assertEquals(42L, command.cmd.positionTicks)
    }

    @Test
    fun `SyncPlayCommand Unpause and Stop parse with missing optional fields`() {
        val unpause = assertIs<SyncPlayEvent.PlaybackCommand>(
            handler.parse("SyncPlayCommand", JSONObject("""{"Command": "Unpause"}""")),
        )
        val stop = assertIs<SyncPlayEvent.PlaybackCommand>(
            handler.parse("SyncPlayCommand", JSONObject("""{"Command": "Stop"}""")),
        )

        assertEquals("Unpause", unpause.cmd.command)
        assertEquals("Stop", stop.cmd.command)
        assertEquals(0L, unpause.cmd.positionTicks)
        assertEquals(0L, unpause.cmd.whenMs)
        assertEquals("", unpause.cmd.playlistItemId)
    }

    @Test
    fun `SyncPlayCommand with an unknown command is null`() {
        assertNull(handler.parse("SyncPlayCommand", JSONObject("""{"Command": "Louder"}""")))
    }

    @Test
    fun `garbage timestamps degrade to 0ms instead of failing the event`() {
        val data = JSONObject().put("Command", "Pause").put("When", "not-a-date")

        val command = assertIs<SyncPlayEvent.PlaybackCommand>(handler.parse("SyncPlayCommand", data))

        assertEquals(0L, command.cmd.whenMs)
    }

    // ── SyncPlayGroupUpdate → PlayQueue ─────────────────────────────────

    @Test
    fun `PlayQueue update maps the playlist and playing item by index`() {
        val data = JSONObject(
            """
            {
              "Type": "PlayQueue",
              "Data": {
                "Playlist": [
                  {"ItemId": "i1", "PlaylistItemId": "p1"},
                  {"ItemId": "i2", "PlaylistItemId": "p2"}
                ],
                "PlayingItemIndex": 1,
                "StartPositionTicks": 5000,
                "IsPlaying": true,
                "When": "2026-01-02T03:04:05Z",
                "LastUpdate": "2026-01-02T03:04:07Z",
                "RepeatMode": "RepeatAll",
                "ShuffleMode": "Shuffle",
                "Reason": "SetPlayQueue"
              }
            }
            """.trimIndent(),
        )

        val event = handler.parse("SyncPlayGroupUpdate", data)

        val queue = assertIs<SyncPlayEvent.PlayQueueUpdate>(event).data
        assertEquals(listOf("i1", "i2"), queue.itemIds)
        assertEquals(listOf("p1", "p2"), queue.playlistItemIds)
        assertEquals(1, queue.playingItemIndex)
        assertEquals("i2", queue.playingItemId)
        assertEquals("p2", queue.playingPlaylistItemId)
        assertEquals(5000L, queue.startPositionTicks)
        assertTrue(queue.isPlaying)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), queue.whenMs)
        assertEquals(Instant.parse("2026-01-02T03:04:07Z").toEpochMilli(), queue.lastUpdateMs)
        assertEquals(SyncPlayRepeatMode.REPEAT_ALL, queue.repeatMode)
        assertEquals(SyncPlayShuffleMode.SHUFFLE, queue.shuffleMode)
        assertEquals("SetPlayQueue", queue.reason)
    }

    @Test
    fun `PlayQueue update with empty playlist yields empty ids and a blank playing item`() {
        val data = JSONObject("""{"Type": "PlayQueue", "Data": {"Playlist": []}}""")

        val queue = assertIs<SyncPlayEvent.PlayQueueUpdate>(
            handler.parse("SyncPlayGroupUpdate", data),
        ).data

        assertEquals(emptyList(), queue.itemIds)
        assertEquals("", queue.playingItemId)
        assertEquals(SyncPlayRepeatMode.REPEAT_NONE, queue.repeatMode)
        assertEquals(SyncPlayShuffleMode.SORTED, queue.shuffleMode)
        assertEquals("NewPlaylist", queue.reason) // documented default
    }

    // ── SyncPlayGroupUpdate → GroupUpdate / StateUpdate / WaitForGroup ──

    @Test
    fun `GroupUpdate inner type reports group name and participant count`() {
        val data = JSONObject(
            """
            {
              "Type": "GroupUpdate",
              "Data": {"GroupName": "Movie Night", "Participants": ["a", "b", "c"]}
            }
            """.trimIndent(),
        )

        val event = handler.parse("SyncPlayGroupUpdate", data)

        val update = assertIs<SyncPlayEvent.GroupUpdate>(event)
        assertEquals("Movie Night", update.groupName)
        assertEquals(3, update.participantCount)
    }

    @Test
    fun `StateUpdate distinguishes Waiting from Paused - neither counts as playing`() {
        val waiting = assertIs<SyncPlayEvent.StateUpdate>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "StateUpdate", "Data": {"State": "Waiting", "Reason": "Buffering"}}"""),
            ),
        )
        val paused = assertIs<SyncPlayEvent.StateUpdate>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "StateUpdate", "Data": {"State": "Paused", "Reason": "UserPaused"}}"""),
            ),
        )
        val playing = assertIs<SyncPlayEvent.StateUpdate>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "StateUpdate", "Data": {"State": "Playing", "Reason": ""}}"""),
            ),
        )

        // Waiting is the transient catch-up state; Paused is still in sync.
        assertEquals(false, waiting.isPlaying)
        assertEquals("Waiting", waiting.state)
        assertEquals("Buffering", waiting.reason)
        assertEquals(false, paused.isPlaying)
        assertEquals("Paused", paused.state)
        assertEquals(true, playing.isPlaying)
        assertEquals("Playing", playing.state)
    }

    @Test
    fun `GroupWait surfaces the blocking user`() {
        val event = handler.parse(
            "SyncPlayGroupUpdate",
            JSONObject("""{"Type": "GroupWait", "Data": {"UserName": "alice"}}"""),
        )

        assertEquals(SyncPlayEvent.WaitForGroup("alice"), event)
    }

    @Test
    fun `UserJoined and UserLeft become notifications`() {
        val joined = assertIs<SyncPlayEvent.Notification>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "UserJoined", "Data": {"UserName": "bob"}}"""),
            ),
        )
        val left = assertIs<SyncPlayEvent.Notification>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "UserLeft", "Data": {"UserName": "bob"}}"""),
            ),
        )

        assertEquals("bob joined the group", joined.message)
        assertEquals("bob left the group", left.message)
    }

    @Test
    fun `UserJoined with a raw-string Data degrades to the raw value in the message`() {
        val joined = assertIs<SyncPlayEvent.Notification>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "UserJoined", "Data": "carol"}"""),
            ),
        )

        assertEquals("carol joined the group", joined.message)
    }

    @Test
    fun `GroupLeft and NotInGroup inner types collapse to GroupLeft`() {
        assertEquals(
            SyncPlayEvent.GroupLeft,
            handler.parse("SyncPlayGroupUpdate", JSONObject("""{"Type": "GroupLeft"}""")),
        )
        assertEquals(
            SyncPlayEvent.GroupLeft,
            handler.parse("SyncPlayGroupUpdate", JSONObject("""{"Type": "NotInGroup"}""")),
        )
    }

    @Test
    fun `error inner types surface the server message as a notification`() {
        val withMessage = assertIs<SyncPlayEvent.Notification>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "SyncPlayIsDisabled", "Data": {"Message": "admin says no"}}"""),
            ),
        )
        val withoutMessage = assertIs<SyncPlayEvent.Notification>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "LibraryAccessDenied"}"""),
            ),
        )

        assertEquals("SyncPlay: admin says no", withMessage.message)
        assertEquals("SyncPlay: LibraryAccessDenied", withoutMessage.message)
    }

    @Test
    fun `unknown inner update type falls back to a catch-all notification`() {
        val notification = assertIs<SyncPlayEvent.Notification>(
            handler.parse(
                "SyncPlayGroupUpdate",
                JSONObject("""{"Type": "SomethingNew"}"""),
            ),
        )

        assertEquals("SyncPlay: Unknown update type SomethingNew", notification.message)
    }

    // ── top-level GroupJoined / GroupLeft / unknown ─────────────────────

    @Test
    fun `top-level GroupJoined reports the group and participant count`() {
        val event = handler.parse(
            "GroupJoined",
            JSONObject("""{"GroupName": "g1", "Participants": [{"UserId": "u1"}, {"UserId": "u2"}]}"""),
        )

        assertEquals(SyncPlayEvent.GroupUpdate("g1", 2), event)
    }

    @Test
    fun `top-level GroupJoined without participants counts zero`() {
        val event = handler.parse("GroupJoined", JSONObject("""{"GroupName": "g1"}"""))

        assertEquals(SyncPlayEvent.GroupUpdate("g1", 0), event)
    }

    @Test
    fun `top-level GroupLeft maps to GroupLeft without touching the payload`() {
        assertEquals(SyncPlayEvent.GroupLeft, handler.parse("GroupLeft", JSONObject()))
    }

    @Test
    fun `unknown rawType is null`() {
        assertNull(handler.parse("TotallyUnrelated", JSONObject("""{"x": 1}""")))
    }

    // ── malformed payload ───────────────────────────────────────────────

    /** A JSONObject whose accessors explode — stands in for a corrupt payload. */
    private class ExplodingJson : JSONObject() {
        override fun optString(key: String, fallback: String): String =
            throw JSONException("corrupt payload")
    }

    @Test
    fun `payload that throws mid-parse is swallowed to null`() {
        assertNull(handler.parse("SyncPlayCommand", ExplodingJson()))
        assertNull(handler.parse("GroupJoined", ExplodingJson()))
    }

    @Test
    fun `parseTicks reads the nested Value form and the plain form`() {
        assertEquals(
            7L,
            handler.parseTicks(JSONObject("""{"PositionTicks": {"Value": 7}}"""), "PositionTicks"),
        )
        assertEquals(
            9L,
            handler.parseTicks(JSONObject("""{"PositionTicks": 9}"""), "PositionTicks"),
        )
        assertEquals(0L, handler.parseTicks(JSONObject(), "PositionTicks"))
    }

    @Test
    fun `parseTimestamp prefers the first present key and tolerates bad values`() {
        val json = JSONObject()
            .put("EmittedAt", "2026-01-02T03:04:05Z")
            .put("When", "bogus")

        assertEquals(
            Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(),
            handler.parseTimestamp(json, listOf("When", "EmittedAt")),
        )
        assertEquals(0L, handler.parseTimestamp(JSONObject("""{"When": "???"}"""), listOf("When")))
        assertEquals(0L, handler.parseTimestamp(JSONObject(), listOf("When")))
    }
}
