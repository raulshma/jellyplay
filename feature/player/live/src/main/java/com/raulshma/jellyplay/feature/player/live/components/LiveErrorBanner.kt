package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator

@Composable
fun LiveErrorBanner(
    isBuffering: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isBuffering -> JellyPlayLoadingIndicator(color = Color.White)
            errorMessage != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    errorMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onRetry) { Text("Retry") }
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}
