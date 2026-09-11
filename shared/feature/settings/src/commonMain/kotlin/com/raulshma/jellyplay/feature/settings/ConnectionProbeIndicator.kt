package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.CircleCheck
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connected
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connection_failed_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connecting

/**
 * How much of the connection-probe affordance renders. One visual language for
 * all three integrations: the pip color mapping (green reachable, amber
 * in-flight, red failed, gray idle) and the status text resolution (verbatim
 * server message vs localized [ConnectionProbe.FallbackText]) live exactly
 * once, here.
 */
enum class ConnectionProbeIndicatorStyle {
    /**
     * The 10dp status pip only. Used inline next to a row title
     * (*arr [ArrSettingsScreen.ServerRow]).
     */
    Pip,

    /**
     * A text-only status line under a row: rendered for [ConnectionProbe.Status.Testing]
     * ("Connecting…") and [ConnectionProbe.Status.Error] (failure text).
     * [ConnectionProbe.Status.Connected] renders NOTHING here — the *arr
     * precedent: healthy rows would each grow a redundant "Connected" line,
     * the pip already tells the story.
     */
    Message,

    /**
     * Pip + text side by side, rendered for every non-Idle state including
     * Connected ("Connected"). Used by the subtitle provider cards, replacing
     * their former per-card spinner + label (the spinner is unified into the
     * amber pip + "Connecting…" text).
     */
    Inline,

    /**
     * The full banner card (icon + title + subtitle) for
     * [ConnectionProbe.Status.Connected]/[Error]; other states render nothing.
     * Retained for [SeerrSettingsScreen]: its connection narrative needs the
     * two-line title/subtitle shape (the version text is integration-specific
     * and passed in via [ConnectionProbeStatusIndicator.connectedSubtitle]),
     * while its retry/login actions stay in the form — the banner carries no
     * actions by design.
     */
    Banner,
}

/**
 * The one status affordance for [ConnectionProbe] statuses (see
 * [ConnectionProbeIndicatorStyle] for the rendering rules per style). Resolves
 * the fallback error-text policy at render time: [ConnectionProbe.Failure.Reported]
 * messages render verbatim, [ConnectionProbe.Failure.Declared] texts localize
 * through the module's Compose resources.
 *
 * @param connectedSubtitle banner-only: the Connected subtitle line
 *   (integration-specific — e.g. Seerr's version text). Null hides the line.
 */
@Composable
fun ConnectionProbeStatusIndicator(
    status: ConnectionProbe.Status<*>,
    modifier: Modifier = Modifier,
    style: ConnectionProbeIndicatorStyle = ConnectionProbeIndicatorStyle.Pip,
    connectedSubtitle: String? = null,
) {
    when (style) {
        ConnectionProbeIndicatorStyle.Pip -> ProbePip(status, modifier)
        ConnectionProbeIndicatorStyle.Message -> ProbeMessage(status, modifier, showPip = false)
        ConnectionProbeIndicatorStyle.Inline -> ProbeMessage(status, modifier, showPip = true)
        ConnectionProbeIndicatorStyle.Banner -> ProbeBanner(status, modifier, connectedSubtitle)
    }
}

/**
 * The status → color mapping every style shares. Green = reachable, amber =
 * probe in flight, red = last probe failed, gray = not yet probed.
 */
@Composable
private fun probeStatusColor(status: ConnectionProbe.Status<*>): Color = when (status) {
    is ConnectionProbe.Status.Connected -> StatusColors.available
    is ConnectionProbe.Status.Testing -> StatusColors.pending
    is ConnectionProbe.Status.Error -> MaterialTheme.colorScheme.error
    is ConnectionProbe.Status.Idle -> MaterialTheme.colorScheme.outline
}

/**
 * The failure-text resolution half of the probe module's fallback policy:
 * integration-provided messages render verbatim, declared fallbacks localize
 * through this one function — the indicator styles and the screens' summary
 * folds all resolve here, so no summary ever degrades to the generic
 * "connection failed" text while the banner shows the specific reason.
 */
@Composable
internal fun probeFailureText(failure: ConnectionProbe.Failure): String = when (failure) {
    is ConnectionProbe.Failure.Reported -> failure.message
    is ConnectionProbe.Failure.Declared -> stringResource(failure.text.resource())
}

@Composable
private fun ProbePip(status: ConnectionProbe.Status<*>, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(probeStatusColor(status)),
    )
}

@Composable
private fun ProbeMessage(status: ConnectionProbe.Status<*>, modifier: Modifier, showPip: Boolean) {
    val text = when (status) {
        is ConnectionProbe.Status.Testing -> stringResource(Res.string.settings_connecting)
        is ConnectionProbe.Status.Error -> probeFailureText(status.failure)
        // Message style: Connected renders nothing (declared *arr rule — see
        // [ConnectionProbeIndicatorStyle.Message]); Inline shows the label.
        is ConnectionProbe.Status.Connected ->
            if (showPip) stringResource(Res.string.settings_connected) else return
        is ConnectionProbe.Status.Idle -> return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showPip) {
            ProbePip(status, Modifier)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (status is ConnectionProbe.Status.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ProbeBanner(
    status: ConnectionProbe.Status<*>,
    modifier: Modifier,
    connectedSubtitle: String?,
) {
    val (icon, title, subtitle, color) = when (status) {
        is ConnectionProbe.Status.Connected -> BannerContent(
            icon = Tabler.Outline.CircleCheck,
            title = stringResource(Res.string.settings_connected),
            subtitle = connectedSubtitle,
            // The shared mapping's Connected color ([probeStatusColor]) — the
            // pip and the banner must agree on what "reachable" looks like.
            color = StatusColors.available,
        )
        is ConnectionProbe.Status.Error -> BannerContent(
            icon = Tabler.Outline.AlertTriangle,
            title = stringResource(Res.string.settings_connection_failed_title),
            subtitle = probeFailureText(status.failure),
            color = MaterialTheme.colorScheme.error,
        )
        // Testing/Idle never banner (the AnimatedVisibility gate at the call
        // sites already keeps them out; this is the belt to those braces).
        is ConnectionProbe.Status.Testing, is ConnectionProbe.Status.Idle -> return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class BannerContent(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val color: Color,
)
