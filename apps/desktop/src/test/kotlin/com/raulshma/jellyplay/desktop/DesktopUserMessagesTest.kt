package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.data.remote.DisplayMessagePayload
import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.feature.music.feedback.DesktopMusicMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the UserMessageHost source assembly extracted from DesktopNavScaffold
 * ([desktopUserMessageSources] + [desktopMusicMessages] +
 * [desktopDisplayMessages] — the seams that made the shared
 * [UserMessageBus]'s messages and the receiver's server-pushed
 * DisplayMessages reach the desktop snackbar instead of being silently
 * dropped):
 *
 *  - the desktop [MusicMessageBus] actual (the buffering relay) maps every
 *    relayed string onto a [UserMessage.Error] carrying [UiText.Raw] — the
 *    severity Android's own bridge (AppMusicMessageBus) gives the same
 *    messages;
 *  - a NON-desktop Koin binding for [MusicMessageBus] degrades to the empty
 *    flow (the is-check, not a cast: unhosted, never a crash);
 *  - the receiver's DisplayMessage payloads map onto [UserMessage.Info]
 *    through the same header+text fold the Android collector runs (header
 *    prefix when present, blank pushes dropped);
 *  - the assembled source list is shared-bus-first, music-relay-second,
 *    receiver-third — the order the scaffold's host(...) call collected in,
 *    with the receiver source appended after the two it already hosted.
 */
class DesktopUserMessagesTest {

    /** A hypothetical custom binding: the degrade path stays unhosted. */
    private class UnhostedMusicBus : MusicMessageBus {
        override fun error(message: String) = Unit
    }

    @Test
    fun `the desktop relay maps each string onto an error message with raw text`() = runTest {
        val bus = DesktopMusicMessageBus()
        val collected = mutableListOf<UserMessage>()
        val job = launch { desktopMusicMessages(bus).toList(collected) }
        runCurrent() // the collector subscribes before the relay emits (no replay)

        bus.error("Library refresh failed")
        bus.error("Queue stalled")
        runCurrent()
        job.cancel()

        assertEquals(
            listOf<UserMessage>(
                UserMessage.Error(UiText.Raw("Library refresh failed")),
                UserMessage.Error(UiText.Raw("Queue stalled")),
            ),
            collected,
        )
    }

    @Test
    fun `a non-desktop MusicMessageBus binding goes unhosted as the empty flow`() = runTest {
        val collected = mutableListOf<UserMessage>()
        // emptyFlow completes immediately — nothing to collect, nothing hangs.
        desktopMusicMessages(UnhostedMusicBus()).toList(collected)
        assertTrue(collected.isEmpty())
    }

    @Test
    fun `the assembled sources are the shared bus first then the music relay`() = runTest {
        val shared = UserMessageBus()
        val music = DesktopMusicMessageBus()
        // Same shape the receiver's real flow has (no replay, buffer 4).
        val receiverPushes = MutableSharedFlow<DisplayMessagePayload>(extraBufferCapacity = 4)
        val sources = desktopUserMessageSources(shared, music, receiverPushes)
        assertEquals(3, sources.size, "exactly the three hosted sources")

        val sharedCollected = mutableListOf<UserMessage>()
        val musicCollected = mutableListOf<UserMessage>()
        val sharedJob = launch { sources[0].toList(sharedCollected) }
        val musicJob = launch { sources[1].toList(musicCollected) }
        runCurrent() // both collectors subscribe before the emissions

        shared.error("shared failure")
        music.error("music failure")
        runCurrent()
        sharedJob.cancel()
        musicJob.cancel()

        assertEquals(listOf<UserMessage>(UserMessage.Error(UiText.Raw("shared failure"))), sharedCollected)
        assertEquals(listOf<UserMessage>(UserMessage.Error(UiText.Raw("music failure"))), musicCollected)
    }

    @Test
    fun `a receiver DisplayMessage surfaces as an info message with the android header fold`() = runTest {
        // Same shape the receiver's real flow has (no replay, buffer 4).
        val receiverPushes = MutableSharedFlow<DisplayMessagePayload>(extraBufferCapacity = 4)
        val collected = mutableListOf<UserMessage>()
        val job = launch { desktopDisplayMessages(receiverPushes).toList(collected) }
        runCurrent() // subscribe before the pushes (SharedFlow, no replay)

        receiverPushes.tryEmit(DisplayMessagePayload(header = "Server notice", text = "Restarting tonight", timeoutMs = 5_000))
        receiverPushes.tryEmit(DisplayMessagePayload(header = "", text = "Bare text push", timeoutMs = null))
        runCurrent()
        job.cancel()

        assertEquals(
            listOf<UserMessage>(
                // The Android MainViewModel collector's exact fold: header
                // prefixed on its own line when present, bare text otherwise,
                // severity Info (an informational server push, not an error).
                UserMessage.Info(UiText.Raw("Server notice\nRestarting tonight")),
                UserMessage.Info(UiText.Raw("Bare text push")),
            ),
            collected,
        )
    }

    @Test
    fun `an all-blank DisplayMessage push is dropped by the text fold`() {
        assertNull(desktopDisplayMessageText(DisplayMessagePayload(header = "", text = "", timeoutMs = null)))
        assertNull(desktopDisplayMessageText(DisplayMessagePayload(header = "  ", text = "  ", timeoutMs = 1)))
        assertEquals(
            "Header\n",
            desktopDisplayMessageText(DisplayMessagePayload(header = "Header", text = "", timeoutMs = null)),
            // A real header with blank text still shows the header line —
            // the fold only drops the all-blank RESULT, matching Android.
        )
    }
}

