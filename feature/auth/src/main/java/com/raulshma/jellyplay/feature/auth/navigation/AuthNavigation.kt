package com.raulshma.jellyplay.feature.auth.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.auth.AddServerScreen
import com.raulshma.jellyplay.feature.auth.LoginScreen
import com.raulshma.jellyplay.feature.auth.QuickConnectScreen
import com.raulshma.jellyplay.feature.auth.ServerListScreen
import com.raulshma.jellyplay.feature.auth.UserSelectionScreen

fun EntryProviderScope<NavKey>.authSection(
    navigator: Navigator,
    onAuthenticated: () -> Unit,
) {
    entry<Route.ServerList> {
        ServerListScreen(
            onAddServer = { navigator.navigate(Route.AddServer) },
            onServerSelected = { server ->
                navigator.navigate(
                    Route.UserSelection(
                        serverId = server.id,
                        serverAddress = server.address,
                        serverName = server.name,
                    )
                )
            },
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
            onQuickConnect = { navigator.navigate(Route.QuickConnect(key.serverAddress)) },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.QuickConnect> { key ->
        QuickConnectScreen(
            serverAddress = key.serverAddress,
            onLoginSuccess = { onAuthenticated() },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.UserSelection> { key ->
        UserSelectionScreen(
            serverId = key.serverId,
            serverAddress = key.serverAddress,
            serverName = key.serverName,
            onUserSelected = { onAuthenticated() },
            onAddUser = { navigator.navigate(Route.Login(key.serverAddress)) },
            onBack = { navigator.goBack() },
        )
    }
}
