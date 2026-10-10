package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberSharedElementModifier
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus

/**
 * One media row in the library's LIST view mode (flat and grouped paths).
 * Tap opens the item; long-press opens the host screen's quick-action sheet
 * when a controller is wired ([LocalMediaQuickActionController] — the
 * PosterCard pattern; the row predates the sheet and never had the
 * affordance). No card chrome: the row keeps its own plain
 * [combinedClickable] with the default indication, plus the focus-tracking /
 * marquee pair it has always carried.
 */
@Composable
fun LibraryListItem(
    item: MediaItem,
    title: String,
    subtitle: AnnotatedString?,
    imageUrl: String?,
    blurHash: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null,
    fallbackUrls: List<String> = emptyList(),
) {
    var isFocused by remember { mutableStateOf(false) }
    // Same stable-lambda pattern as PosterCard: reads the controller from
    // composition scope so the sheet target is always this row's exact item,
    // remembered per (item, controller) so the gesture detector isn't
    // restarted mid-press. Null controller → plain click, no long-press.
    val quickActionController = LocalMediaQuickActionController.current
    val onQuickActionsLongPress = quickActionController?.let { controller ->
        remember(item, controller) { { controller.show(item) } }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .combinedClickable(onClick = onClick, onLongClick = onQuickActionsLongPress)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .focusIndicator()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(ShapeCache.smooth8)
                .then(rememberSharedElementModifier(sharedElementKey)),
        ) {
            if (imageUrl != null) {
                MediaImage(
                    url = imageUrl,
                    fallbackUrls = fallbackUrls,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    blurHash = blurHash,
                    // Size the decode to the 48×72 dp box (2× density = 96×144 px)
                    // instead of the MediaImage default 384² (~4-6× oversampled).
                    size = CoilSize(96, 144),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = isFocused),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
