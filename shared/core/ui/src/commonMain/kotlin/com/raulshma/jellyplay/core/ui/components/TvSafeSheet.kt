package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSafeSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isTv = LocalJellyPlayUi.current.isTv

    if (isTv) {
        TvSheetDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            modifier = modifier,
            content = content,
        )
    } else {
        MobileBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            content = content,
        )
    }
}

@Composable
private fun TvSheetDialog(
    onDismissRequest: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        HideDialogSystemBars()

        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            Surface(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 48.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = ShapeCache.smooth20,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // focusGroup + focusRequester on the content (not the title Text — Text has no
                    // focus target) so requesting initialFocus redirects into the first focusable
                    // child, matching the TvFocusable* container contract. Same modifier order:
                    // focusGroup first, requester last.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .focusRequester(initialFocus),
                        content = content,
                    )
                }
            }
        }
    }

    // 3-frame retry defends against sheet content that composes focusables asynchronously —
    // same idiom as RequestOrRestoreFocus.
    LaunchedEffect(Unit) {
        for (attempt in 1..3) {
            withFrameNanos { }
            if (initialFocus.tryRequestFocus("tv_sheet_init")) break
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = ShapeCache.smoothTop28,
        // surface (not surfaceContainer) so the sheet matches the app/screen
        // background in every mode — pure #000 in OLED, identical to Library /
        // MediaDetail, instead of the slightly-lifted surfaceContainer #111.
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = { SheetDragHandle() },
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            // Edge-to-edge: the sheet's own window does not inset its content, so
            // callers must not add navigationBars/IME padding themselves — it is
            // applied once here (sheet background still draws behind the bars).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                content()
            }
        }
    }
}

/**
 * Hides system bars inside a fullscreen TV sheet dialog. Android drives the
 * dialog window's insets controller; other platforms have nothing to hide.
 */
@Composable
internal expect fun HideDialogSystemBars()
