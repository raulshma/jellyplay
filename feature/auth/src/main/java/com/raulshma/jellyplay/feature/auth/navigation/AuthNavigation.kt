package com.raulshma.jellyplay.feature.auth.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.auth.AddServerScreen
import com.raulshma.jellyplay.feature.auth.LoginScreen
import com.raulshma.jellyplay.feature.auth.ServerListScreen

fun EntryProviderScope<NavKey>.authSection(
    navigator: Navigator,
    onAuthenticated: () -> Unit,
) {
    entry<Route.ServerList> {
        ServerListScreen(
            onAddServer = { navigator.navigate(Route.AddServer) },
            onServerSelected = { address -> navigator.navigate(Route.Login(address)) },
        )
    }
    entry<Route.AddServer> {
        AddServerScreen(
            onServerAdded = { address ->
                navigator.goBack()
                navigator.navigate(Route.Login(address))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.Login> { key ->
        LoginScreen(
            serverAddress = key.serverAddress,
            onLoginSuccess = { onAuthenticated() },
            onBack = { navigator.goBack() },
        )
    }
}
