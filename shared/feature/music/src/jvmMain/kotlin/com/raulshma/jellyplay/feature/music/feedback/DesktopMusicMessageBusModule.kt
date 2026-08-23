package com.raulshma.jellyplay.feature.music.feedback

import org.koin.core.module.Module
import org.koin.dsl.module

/** Desktop wiring for the (inert) message seam — drops messages (no host yet). */
internal class DesktopMusicMessageBus : MusicMessageBus {
    override fun error(message: String) {}
}

fun desktopMusicMessageBusModule(): Module = module {
    single<MusicMessageBus> { DesktopMusicMessageBus() }
}
