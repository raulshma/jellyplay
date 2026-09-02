package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Mail
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.home.generated.resources.home_newsletter_subtitle
import com.raulshma.jellyplay.feature.home.generated.resources.home_newsletter_ready
import com.raulshma.jellyplay.feature.home.generated.resources.home_dismiss
import com.raulshma.jellyplay.feature.home.generated.resources.Res

@Composable
fun NewsletterBanner(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val bannerBrush = remember(primaryContainer, tertiaryContainer) {
        Brush.horizontalGradient(
            colors = listOf(primaryContainer, tertiaryContainer)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(ShapeCache.smooth20)
            .background(bannerBrush)
            .focusIndicator()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Tabler.Outline.Mail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.home_newsletter_ready),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(Res.string.home_newsletter_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                    .focusIndicator(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Tabler.Outline.X,
                    contentDescription = stringResource(Res.string.home_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
