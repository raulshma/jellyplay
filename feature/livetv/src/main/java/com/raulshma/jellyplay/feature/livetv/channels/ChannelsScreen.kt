package com.raulshma.jellyplay.feature.livetv.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onChannelClick: (String, String) -> Unit,
    onGuideClick: () -> Unit,
    onDvrClick: () -> Unit = {},
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live TV") },
                actions = {
                    IconButton(onClick = onDvrClick) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = "Recordings")
                    }
                    IconButton(onClick = onGuideClick) {
                        Icon(Icons.Default.CalendarViewWeek, contentDescription = "Program Guide")
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading && viewModel.channels.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            viewModel.error != null && viewModel.channels.isEmpty() -> {
                ErrorScreen(
                    message = viewModel.error!!,
                    onRetry = { viewModel.loadChannels() },
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    items(viewModel.channels.size) { index ->
                        val channel = viewModel.channels[index]
                        ChannelItem(
                            channel = channel,
                            imageUrl = viewModel.getImageUrl(channel.id, channel.imageTag),
                            onClick = { onChannelClick(channel.id, channel.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: LiveTvChannel,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp, 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = channel.name,
                    blurHash = channel.primaryBlurHash,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val currentProgram = channel.currentProgram
            if (currentProgram != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentProgram.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val number = channel.number
            if (number != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Ch. $number",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        IconButton(onClick = onClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Watch")
        }
    }
}
