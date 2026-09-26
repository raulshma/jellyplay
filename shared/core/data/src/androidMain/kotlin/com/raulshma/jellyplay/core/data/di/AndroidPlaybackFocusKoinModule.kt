package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.focus.AndroidFocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.AudioPlaybackManagerSurface
import com.raulshma.jellyplay.core.data.playback.focus.DefaultPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.FocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The cross-player-exclusivity family of the androidCoreDataModule split
 * (ADR-0004 — see [androidCoreDataModule] for the construction-owner
 * rules). Binding bodies moved verbatim from the pre-split single-module
 * layout.
 */
internal fun androidPlaybackFocusModule(context: Context): Module = module {
    // ── Cross-player exclusivity (PlaybackFocus, ADR-0004) ─────────────
    single<FocusArbiter> { AndroidFocusArbiter(context.applicationContext) }
    single<PlaybackFocus> {
        DefaultPlaybackFocus(
            arbiter = get(),
            surfaces = listOf(AudioPlaybackManagerSurface(manager = lazy { get<AudioPlaybackManager>() })),
        )
    }
}
