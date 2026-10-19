package com.raulshma.jellyplay.feature.auth.di

import com.raulshma.jellyplay.feature.auth.AddServerViewModel
import com.raulshma.jellyplay.feature.auth.AuthViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the auth feature. Koin is the single
 * constructor owner (one framework per type). Every ctor dep is Koin-native
 * on BOTH platforms, so this module has zero Hilt
 * interop (calendar/requests/shortcuts class):
 *  - AuthRepository and ServerDiscoveryRepository resolve from dataJvmModule
 *    (singles in the shared data graph).
 *  - LocalNetworkStatus (AddServerViewModel's Android-17-permission gate) is
 *    NOT defined here: it needs a Context on Android and none on desktop, so
 *    each platform registers its own definition — androidAuthModule(context)
 *    (androidMain) and desktopAuthPlatformModule (jvmMain), the settings
 *    StorageMountsProvider shape. Any module that registers THIS module must
 *    register one of those two alongside it.
 */
val authModule: Module = module {
    viewModel {
        AuthViewModel(
            authRepository = get(),
        )
    }
    viewModel {
        AddServerViewModel(
            authRepository = get(),
            serverDiscoveryRepository = get(),
            localNetworkStatus = get(),
            // Self-signed trust grants persist into the network DataStore
            // (datastoreCommonModule single); the OkHttp config StateFlow the
            // network layer reads at handshake time flows from it.
            networkOfflineStore = get(),
        )
    }
}
