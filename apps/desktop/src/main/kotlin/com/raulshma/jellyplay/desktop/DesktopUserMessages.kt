package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.data.remote.DisplayMessagePayload
import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.feature.music.feedback.DesktopMusicMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.shell.displayMessageText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * The UserMessageHost source assembly (pure — no Compose imports; pinned by
 * DesktopUserMessagesTest): ONE collector behind every message source this
 * shell shows. The shared [UserMessageBus] that the shared ViewModels
 * (Home, Library, …) post through feeds it, alongside the music relay below;
 * both feed the shared UserMessageHost, whose severity→duration policy is the
 * shared module's (the snackbar present adapter stays in the scaffold). The
 * receiver's server-pushed DisplayMessage commands are the third source, via
 * the desktop receiver port — the same push Android's MainViewModel surfaces
 * as one-shot user messages.
 *
 * Order is host-first: the shared bus's messages precede the music relay in
 * [desktopUserMessageSources]' list; the receiver source is appended after
 * them.
 */

/**
 * Music message-bus host source: the desktop [MusicMessageBus] actual is a
 * buffering relay ([DesktopMusicMessageBus], desktopMusicMessageBusModule),
 * mapped onto [UserMessage.Error] — the severity Android's own bridge
 * (AppMusicMessageBus) gives the same messages. The is-check (not a cast) is
 * the degrade path: a hypothetical custom Koin binding for [MusicMessageBus]
 * simply goes unhosted, never crashes.
 */
internal fun desktopMusicMessages(musicMessageBus: MusicMessageBus): Flow<UserMessage> =
    if (musicMessageBus is DesktopMusicMessageBus) {
        musicMessageBus.messages.map { UserMessage.Error(UiText.Raw(it)) }
    } else {
        emptyFlow()
    }

/**
 * The receiver's DisplayMessage host source: server-pushed
 * [DisplayMessagePayload]s mapped onto [UserMessage.Info] over the shared
 * [displayMessageText] fold (:shared:feature:shell — the exact header+text
 * fold the Android shell's MainViewModel collector gives these pushes, so
 * one server message reads identically on both shells).
 */
internal fun desktopDisplayMessages(
    displayMessages: Flow<DisplayMessagePayload>,
): Flow<UserMessage> =
    displayMessages.mapNotNull { payload ->
        displayMessageText(payload)?.let { UserMessage.Info(UiText.Raw(it)) }
    }

/**
 * The assembled source list the scaffold's UserMessageHost collects:
 * the shared bus's messages first, then the music relay, then the
 * receiver's DisplayMessages (see file KDoc).
 *
 * DELIBERATELY ABSENT — the receiver's `playEvents` ("Now playing from
 * another device"): on desktop every remote Play already opens the player
 * through the navigation bridge (the dispatcher's OpenVideoPlayer /
 * OpenAudioPlayer commands land on the remote-nav collector), so the player
 * route itself is the visible confirmation the
 * Android now-playing banner exists to provide; a snackbar under the
 * fullscreen player switch would only duplicate it. Uncollected here by
 * decision, not oversight — revisit only if desktop gains a
 * background-play path with no navigating surface.
 */
internal fun desktopUserMessageSources(
    sharedUserMessageBus: UserMessageBus,
    musicMessageBus: MusicMessageBus,
    remoteDisplayMessages: Flow<DisplayMessagePayload>,
): List<Flow<UserMessage>> = listOf(
    sharedUserMessageBus.messages,
    desktopMusicMessages(musicMessageBus),
    desktopDisplayMessages(remoteDisplayMessages),
)
