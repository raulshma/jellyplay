package com.raulshma.jellyplay.feature.player.audio.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
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
 *
 * Position arrives as a [LongState] so the 4 Hz tick only recomposes the active
 * line's leaf ([KaraokeLine]); this body does not re-execute per tick (rows are
 * keyed and LyricsLine is @Immutable, so LazyColumn items skip).
 */
@Composable
fun KaraokeLyricsView(
    lyrics: List<LyricsLine>,
    currentIndex: Int,
    currentPositionMs: LongState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
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
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "karaokeLineAlpha_$index",
                )
                if (isCurrent && line.words.isNotEmpty()) {
                    KaraokeLine(
                        line = line,
                        // Pass the state itself: only this leaf re-executes on
                        // the position tick, not the LazyColumn above.
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
    positionMs: LongState,
    modifier: Modifier = Modifier,
) {
    val positionInLine = (positionMs.value - line.timeMs).coerceAtLeast(0L)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = Color.White.copy(alpha = 0.6f)

    // Compute directly from positionInLine (a plain Long, not a tracked State) so the
    // active word advances correctly. The previous derivedStateOf captured positionInLine
    // once and never re-evaluated, so per-word highlighting was frozen. Reading
    // positionMs.value here scopes the 4 Hz recomposition to this leaf only.
    val activeWordIndex = remember(line, positionInLine) {
        LrcParser.findCurrentWordIndex(line, positionInLine)
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
