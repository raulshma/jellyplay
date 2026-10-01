package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.focus.AndroidFocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.AudioPlaybackManagerSurface
import com.raulshma.jellyplay.core.data.playback.focus.DefaultPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.FocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.VideoPlaybackSurface
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The cross-player-exclusivity family of the androidCoreDataModule split
 * (ADR-0004 — see [androidCoreDataModule] for the construction-owner
 * rules). Since the video slice the executor commands the VIDEO-family
 * surface too (the VOD wiring and the live player bind their current
 * engine/player into the [VideoPlaybackSurface] singleton from their own
 * modules).
 */
internal fun androidPlaybackFocusModule(context: Context): Module = module {
    // ── Cross-player exclusivity (PlaybackFocus, ADR-0004) ─────────────
    single<FocusArbiter> { AndroidFocusArbiter(context.applicationContext) }
    single { VideoPlaybackSurface() }
    single<PlaybackFocus> {
        DefaultPlaybackFocus(
            arbiter = get(),
            surfaces = listOf(
                AudioPlaybackManagerSurface(manager = lazy { get<AudioPlaybackManager>() }),
                get<VideoPlaybackSurface>(),
            ),
        )
    }
}
