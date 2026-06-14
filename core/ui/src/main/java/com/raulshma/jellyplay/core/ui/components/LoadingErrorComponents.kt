package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    // On TV the spinner must hold focus while real data is unavailable, otherwise focus is orphaned
    // (nothing else on the screen is focusable until the list/grid composes).
    if (isTv) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus("loading") }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.focusRequester(focusRequester).focusable() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        JellyPlayLoadingIndicator(
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * TV-friendly loading page that grabs focus IMMEDIATELY but only shows the spinner after [delay].
 * Avoids flicker on fast loads while still preventing the D-pad from navigating to nonexistent
 * elements behind the loading state.
 *
 * The focus grab is unconditional on TV — even if the spinner never appears, focus is held so the
 * D-pad cannot drift to a stale screen underneath. The visible spinner is gated by [delay] (default
 * 300ms) so loads that complete in <300ms show nothing at all.
 */
@Composable
fun DelayedLoadingScreen(
    modifier: Modifier = Modifier,
    delay: Duration = 300.milliseconds,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    var showSpinner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isTv) focusRequester.tryRequestFocus("delayed_loading")
        kotlinx.coroutines.delay(delay)
        showSpinner = true
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.focusRequester(focusRequester).focusable() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = showSpinner) {
            JellyPlayLoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val retryFocusRequester = remember { FocusRequester() }
    // On TV, deterministically focus the Retry button when present so an error screen is never
    // left without an actionable focus target.
    if (isTv && onRetry != null) {
        LaunchedEffect(Unit) { retryFocusRequester.tryRequestFocus("error_retry") }
    }
    AnimatedEntrance(visible = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onRetry,
                        modifier = if (isTv) Modifier.focusRequester(retryFocusRequester) else Modifier,
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
