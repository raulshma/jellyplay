package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ALPHABET = ('A'..'Z').toList() + '#'

data class AlphabetSection(
    val letter: Char,
    val firstItemIndex: Int,
)

@Composable
fun AlphabetScroller(
    sections: List<AlphabetSection>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var activeLetterIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState, sections) {
        if (sections.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                val section = sections.lastOrNull { it.firstItemIndex <= firstVisible }
                if (section != null) {
                    activeLetter = section.letter
                    activeLetterIndex = sections.indexOf(section)
                }
            }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(end = 4.dp)
                .pointerInput(sections) {
                    val letterHeight = size.height.toFloat() / ALPHABET.size
                    detectDragGestures(
                        onDragStart = { offset ->
                            val idx = (offset.y / letterHeight).toInt().coerceIn(0, ALPHABET.lastIndex)
                            activeLetter = ALPHABET[idx]
                            val section = sections.find { it.letter == ALPHABET[idx] }
                            if (section != null) scope.launch { listState.scrollToItem(section.firstItemIndex) }
                        },
                        onDrag = { change, _ ->
                            val idx = (change.position.y / letterHeight).toInt().coerceIn(0, ALPHABET.lastIndex)
                            val letter = ALPHABET[idx]
                            activeLetter = letter
                            val section = sections.find { it.letter == letter }
                            if (section != null) scope.launch { listState.scrollToItem(section.firstItemIndex) }
                        },
                        onDragEnd = { activeLetter = null },
                        onDragCancel = { activeLetter = null },
                    )
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ALPHABET.forEach { letter ->
                val isActive = activeLetter == letter
                Text(
                    text = letter.toString(),
                    fontSize = if (isActive) 11.sp else 9.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = activeLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 40.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeLetter?.toString() ?: "",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
