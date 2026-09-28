package com.raulshma.jellyplay.desktop.integration

import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.desktop.discord.DiscordIpcClient
import com.raulshma.jellyplay.desktop.discord.DiscordPresenceService
import com.raulshma.jellyplay.desktop.hooks.DesktopHookRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The desktop-shell integration services (features 4.2 + 4.3): the
 * hand-rolled Discord Rich Presence stack ([DiscordIpcClient] +
 * [DiscordPresenceService]) and the playback-event shell hooks
 * ([DesktopHookRunner]). Both observe the shared [NowPlayingReporter] spine
 * (video publishes from the player session manager, audio mirrors from the
 * desktop audio queue manager, idle transitions forward from the shell's
 * services holder) — registered here because every collaborator is a
 * desktop-shell or shared-graph type; there is no shared-module half of
 * either feature.
 *
 * The client's join lambda resolves the service lazily (the Join event can
 * only fire long after the graph is up — no construction cycle). Nothing
 * here starts anything: [DiscordPresenceService.start] /
 * [DesktopHookRunner.start] are invoked from launchDesktopStartup, off the
 * critical path.
 */
internal fun desktopIntegrationModule(): Module = module {
    single {
        DiscordIpcClient(
            clientId = DISCORD_APPLICATION_ID,
            scope = get<CoroutineScope>(DatastoreQualifiers.applicationScope),
            onActivityJoin = { secret -> get<DiscordPresenceService>().handleJoinSecret(secret) },
        )
    }
    single {
        DiscordPresenceService(
            scope = get<CoroutineScope>(DatastoreQualifiers.applicationScope),
            client = get(),
            nowPlayingReporter = get(),
            activePlayerController = get<ActivePlayerController>(),
            audioIsPlaying = get<DesktopAudioQueueManager>().isPlaying,
            syncPlayManager = get(),
            enabled = get<ScreensaverStore>().screensaver.map { it.discordPresenceEnabled },
        )
    }
    single {
        DesktopHookRunner(
            scope = get<CoroutineScope>(DatastoreQualifiers.applicationScope),
            settings = get<ScreensaverStore>().screensaver,
            events = get<NowPlayingReporter>().events,
        )
    }
}

/**
 * The Discord application id the IPC handshake presents. Rich Presence
 * renders under this application's name/assets — replace with the shell
 * build's registered application id to change the presence branding.
 */
private const val DISCORD_APPLICATION_ID = "1334098617032253441"
