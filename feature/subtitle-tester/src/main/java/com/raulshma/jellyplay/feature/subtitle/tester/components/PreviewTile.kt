package com.raulshma.jellyplay.feature.subtitle.tester.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.feature.subtitle.tester.preview.PreviewEngineHost

@Composable
fun PreviewTile(
    host: PreviewEngineHost,
    isApplying: Boolean,
    applyingLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { host.container },
            modifier = Modifier.fillMaxWidth(),
        )
        if (isApplying) {
            Text(applyingLabel, modifier = Modifier.padding(8.dp))
        }
    }
}
