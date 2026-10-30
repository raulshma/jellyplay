package com.raulshma.jellyplay.feature.shell.di

import com.raulshma.jellyplay.core.data.session.JellyPlayBroadcastMessenger
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The companion-plugin broadcast seam (ADR 0010) bridged to the shared
 * UserMessageBus — replaces the byte-identical per-shell copies (the
 * Android interop-adapter module's and desktop's inline def): the bridge
 * has zero per-shell content, so one def rides the sharedFeatureModules
 * spread both JVM shells consume.
 */
val shellBroadcastMessengerModule: Module = module {
    single<JellyPlayBroadcastMessenger> {
        val bus: UserMessageBus = get()
        JellyPlayBroadcastMessenger { text -> bus.info(text) }
    }
}
