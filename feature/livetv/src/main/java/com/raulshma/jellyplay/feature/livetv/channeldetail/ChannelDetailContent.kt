package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.livetv.R
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The channel detail content: header + Watch Live, now-playing hero (with live
 * progress bar), and the "On Today" timeline of upcoming programs.
 */
@Composable
internal fun ChannelDetailContent(
    state: ChannelDetailUiState,
    onPlayChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = contentPad, end = contentPad, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Header: logo/name/number + Watch Live ──
        item(key = "header") {
            ChannelHeader(state = state, onWatchLive = onPlayChannel)
        }

        // ── Now-playing hero ──
        val current = state.currentProgram
        if (current != null) {
            item(key = "hero_${current.id}") {
                NowPlayingHero(program = current)
            }
        }

        // ── On Today timeline ──
        item(key = "timeline_header") {
            Text(
                text = stringResource(R.string.livetv_channel_on_today),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        if (state.programs.isEmpty()) {
            item(key = "timeline_empty") {
                Text(
                    text = stringResource(R.string.livetv_channel_no_program_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val airingId = current?.id
            items(items = state.programs, key = { it.id }) { program ->
                ProgramTimelineRow(
                    program = program,
                    isAiring = program.id == airingId,
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(state: ChannelDetailUiState, onWatchLive: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = state.channelName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.channelNumber?.let { num ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Ch. $num",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        // Watch Live button — styled like a primary play action.
        Row(
            modifier = Modifier
                .clip(ShapeCache.smooth28)
                .background(MaterialTheme.colorScheme.primary)
                .focusIndicator(ShapeCache.smooth28)
                .clickable(onClick = onWatchLive)
                .padding(horizontal = 32.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Tabler.Outline.PlayerPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.livetv_channel_watch_live),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun NowPlayingHero(program: LiveTvProgram) {
    val progress = rememberLiveProgress(program)
    val infinite = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.livetv_channel_live),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = program.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        program.episodeTitle?.takeIf { it.isNotBlank() }?.let { ep ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = ep,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        )
        program.overview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgramTimelineRow(program: LiveTvProgram, isAiring: Boolean) {
    val airingProgress = if (isAiring) rememberLiveProgress(program) else 0f
    val time = program.startDate?.let {
        runCatching {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .format(DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrNull()
    } ?: "--"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth14)
            .background(
                if (isAiring) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = if (isAiring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isAiring) FontWeight.SemiBold else FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            program.episodeTitle?.takeIf { it.isNotBlank() }?.let { ep ->
                Text(
                    text = ep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isAiring) {
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { airingProgress },
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            )
        }
    }
}

/**
 * Live progress fraction (0..1) of a program, derived from its start/end ISO
 * strings. Computed once per composition (no periodic tick — refresh on open
 * only per the design decision).
 */
@Composable
private fun rememberLiveProgress(program: LiveTvProgram): Float {
    val now = Instant.now()
    val start = program.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val end = program.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val total = end.epochSecond - start.epochSecond
    return if (total <= 0) 0f
    else (((now.epochSecond - start.epochSecond).toFloat() / total)).coerceIn(0f, 1f)
}
