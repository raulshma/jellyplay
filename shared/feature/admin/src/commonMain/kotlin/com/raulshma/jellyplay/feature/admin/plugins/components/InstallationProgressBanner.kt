package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel_cd
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_installing
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_plugin_version
import com.raulshma.jellyplay.core.model.PluginInstallationInfo

@Composable
fun InstallationProgressBanner(
    installations: List<PluginInstallationInfo>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        installations.forEach { installation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Tabler.Outline.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.admin_installing, installation.name ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    installation.version?.let { ver ->
                        Text(
                            text = stringResource(Res.string.admin_plugin_version, ver),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .height(4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onCancel(installation.guid) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Tabler.Outline.X,
                        contentDescription = stringResource(Res.string.admin_cancel_cd),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
