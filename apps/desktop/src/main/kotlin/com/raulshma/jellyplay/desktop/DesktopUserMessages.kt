package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.feature.music.feedback.DesktopMusicMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * The UserMessageHost source assembly, extracted from DesktopNavScaffold's
 * inline remembered blocks (pure — no Compose imports; pinned by
 * DesktopUserMessagesTest): ONE collector behind every message source this
 * shell shows. The shared [UserMessageBus] the migrated shared ViewModels
 * (Home, Library, …) post through was never collected on desktop before —
 * its messages were silently dropped; the music relay below was the only
 * hosted source. Both feed the shared UserMessageHost, whose
 * severity→duration policy is the shared module's (the snackbar present
 * adapter stays in the scaffold).
 *
 * Order is host-first: the shared bus's messages precede the music relay in
 * [desktopUserMessageSources]' list exactly as the inlined
 * `host(sharedUserMessageBus.messages, musicMessages)` call passed them.
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
 * The assembled source list the scaffold's UserMessageHost collects:
 * the shared bus's messages first, then the music relay (see class KDoc).
 */
internal fun desktopUserMessageSources(
    sharedUserMessageBus: UserMessageBus,
    musicMessageBus: MusicMessageBus,
): List<Flow<UserMessage>> = listOf(
    sharedUserMessageBus.messages,
    desktopMusicMessages(musicMessageBus),
)
