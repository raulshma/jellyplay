package com.raulshma.jellyplay.feature.auth.di

import com.raulshma.jellyplay.feature.auth.LocalNetworkStatus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick for the auth feature's local-network seam
 * (desktopSettingsPlatformModule / desktopMusicMessageBusModule shape): the
 * desktop JVM has no Android-17-style local-network runtime permission, so
 * the gate never blames a connection failure on one — the classifier falls
 * through to the concrete network error (resolve/connect/timeout/SSL), which
 * is exactly the legacy behavior on non-enforcing Android versions.
 */
val desktopAuthPlatformModule: Module = module {
    single<LocalNetworkStatus> { LocalNetworkStatus { false } }
}
