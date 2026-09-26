package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.feature.music.feedback.DesktopMusicMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the UserMessageHost source assembly extracted from DesktopNavScaffold
 * ([desktopUserMessageSources] + [desktopMusicMessages] — the seam that made
 * the shared UserMessageBus's messages reach the desktop snackbar instead of
 * being silently dropped):
 *
 *  - the desktop [MusicMessageBus] actual (the buffering relay) maps every
 *    relayed string onto a [UserMessage.Error] carrying [UiText.Raw] — the
 *    severity Android's own bridge (AppMusicMessageBus) gives the same
 *    messages;
 *  - a NON-desktop Koin binding for [MusicMessageBus] degrades to the empty
 *    flow (the is-check, not a cast: unhosted, never a crash);
 *  - the assembled source list is shared-bus-first, music-relay-second —
 *    the order the scaffold's host(...) call collected in.
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
        val sources = desktopUserMessageSources(shared, music)
        assertEquals(2, sources.size, "exactly the two hosted sources")

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
}
