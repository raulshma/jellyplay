package com.raulshma.jellyplay.feature.player.audio.di

import com.raulshma.jellyplay.feature.player.audio.AudioTrackDownloads
import com.raulshma.jellyplay.feature.player.audio.JvmAudioTrackDownloads
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformPlayerAudioModule(): Module = org.koin.dsl.module {
    // The audio player VM resolves `AudioTrackDownloads` via get() — without
    // this binding android/desktop crash with NoDefinitionException at first
    // VM creation. Mirrors SettingsPlatformModule.jvm.
    single<AudioTrackDownloads> { JvmAudioTrackDownloads(get()) }
}
