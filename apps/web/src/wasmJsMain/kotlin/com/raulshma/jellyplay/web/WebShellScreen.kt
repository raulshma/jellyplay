package com.raulshma.jellyplay.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState

/**
 * Phase W placeholder (docs/kmp-migration-plan.md §Phase W): proves the
 * shell boots — theme applied, Koin container alive, Compose rendering.
 * Real screens arrive with the W.1+ slices; do not grow this file into a UI.
 *
 * W.1 chunk 3: the network-status line is the ONE bit of network wiring the
 * shell exposes — it reads the shared [AtomicSessionState] the wasm API
 * clients publish into (via Koin, see Main.kt). Until a login screen lands
 * it stays in its "ready, not connected" state; the flows are wired so the
 * first auth flow flips it with zero further changes here.
 */
@Composable
fun WebShellScreen(sessionState: AtomicSessionState) {
    val server by sessionState.currentServer.collectAsState()
    val user by sessionState.currentUser.collectAsState()

    val networkStatus = when {
        server != null && user != null ->
            "Network stack ready — connected to ${server?.name} as ${user?.name}."
        server != null ->
            "Network stack ready — server ${server?.name}, not signed in."
        else ->
            "Network stack ready — no server connected (auth UI is a later slice)."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "JellyPlay",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = networkStatus,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Preferences persist to localStorage via the shared datastore stack.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
