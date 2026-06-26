package com.raulshma.jellyplay.feature.player.audio.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.LyricsLine

/**
 * Karaoke-style lyrics display that highlights each word as it is being sung.
 * Falls back to a non-word-level display when the current line has no inline
 * word timings.
 */
@Composable
fun KaraokeLyricsView(
    lyrics: List<LyricsLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentLine = lyrics.getOrNull(currentIndex)
    val hasAnyWordTimings = remember(lyrics) { lyrics.any { it.words.isNotEmpty() } }
    val activeAlpha = 1f
    val upcomingAlpha = 0.4f

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(
                index = currentIndex.coerceAtLeast(0),
                scrollOffset = -120,
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            contentPadding = PaddingValues(vertical = 80.dp, horizontal = 24.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(items = lyrics, key = { _, line -> "${line.timeMs}_${line.text.hashCode()}" }) { index, line ->
                val isCurrent = index == currentIndex
                val lineAlpha by animateFloatAsState(
                    targetValue = if (isCurrent) activeAlpha else upcomingAlpha,
                    animationSpec = tween(durationMillis = 250),
                    label = "karaokeLineAlpha_$index",
                )
                if (isCurrent && line.words.isNotEmpty()) {
                    KaraokeLine(
                        line = line,
                        positionMs = currentPositionMs,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val display = if (line.text.isBlank()) "♪" else line.text
                    Text(
                        text = display,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 26.sp,
                        ),
                        color = Color.White.copy(alpha = lineAlpha),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokeLine(
    line: LyricsLine,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val positionInLine = (positionMs - line.timeMs).coerceAtLeast(0L)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = Color.White.copy(alpha = 0.6f)

    val activeWordIndex by remember {
        derivedStateOf { LrcParser.findCurrentWordIndex(line, positionInLine) }
    }

    val annotated: AnnotatedString = remember(activeWordIndex) {
        buildAnnotatedString {
            line.words.forEachIndexed { index, word ->
                val isActive = index == activeWordIndex
                val isFinished = index < activeWordIndex
                val color = when {
                    isActive || isFinished -> activeColor
                    else -> inactiveColor
                }
                withStyle(
                    style = SpanStyle(
                        color = color,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                    )
                ) {
                    append(word.text)
                }
                if (index != line.words.lastIndex) {
                    append(" ")
                }
            }
        }
    }

    val activeScale by animateFloatAsState(
        targetValue = if (activeWordIndex >= 0) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "karaokeLineScale",
    )

    Text(
        text = annotated,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        ),
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
fun KaraokeModeHint(visible: Boolean) {
    AnimatedVisibility(visible = visible) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(24.dp),
        ) {
            Text(
                text = "Karaoke mode",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = "Word-by-word highlighting requires Enhanced LRC lyrics with inline timestamps.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
