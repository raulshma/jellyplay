package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.List
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReadingLayout
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_animated_turns
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_behavior
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_ltr
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_rtl
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_mode
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_original
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_page
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_width
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_layout
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_layout_double
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_layout_single
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_settings
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc_rail
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_volume_keys
import org.jetbrains.compose.resources.stringResource

/**
 * The settings sheet for PAGED books: the per-book reading direction chips,
 * the per-session page-fit chips ([ReaderFitMode] is view state — deliberately
 * not persisted), the behavior section, plus the TOC entry when the format
 * has one (PDF outlines only — CBZ/CBR books have no TOC story, so the row is
 * absent rather than disabled). One of the reader's per-sheet files (split
 * from ReaderSheets.kt; shared rows live in ReaderChrome.kt, the edit bundles
 * in ReaderSheetState.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PagedSettingsSheet(
    direction: ReadingDirection,
    layout: ReadingLayout,
    tocAvailable: Boolean,
    fitMode: ReaderFitMode,
    prefs: ReaderPrefsSnapshot,
    onSetDirection: (ReadingDirection) -> Unit,
    onSetLayout: (ReadingLayout) -> Unit,
    onSetFitMode: (ReaderFitMode) -> Unit,
    onBehaviorChange: (ReaderBehaviorState) -> Unit,
    onOpenToc: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val behavior = prefs.behaviorState()
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            SheetTitle(text = stringResource(Res.string.book_reader_settings))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = direction == ReadingDirection.LTR,
                    onClick = { onSetDirection(ReadingDirection.LTR) },
                    label = { Text(stringResource(Res.string.book_reader_direction_ltr)) },
                )
                FilterChip(
                    selected = direction == ReadingDirection.RTL,
                    onClick = { onSetDirection(ReadingDirection.RTL) },
                    label = { Text(stringResource(Res.string.book_reader_direction_rtl)) },
                )
            }
            SectionLabel(text = stringResource(Res.string.book_reader_layout), topPadding = 8.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = layout == ReadingLayout.SINGLE,
                    onClick = { onSetLayout(ReadingLayout.SINGLE) },
                    label = { Text(stringResource(Res.string.book_reader_layout_single)) },
                )
                FilterChip(
                    selected = layout == ReadingLayout.DOUBLE,
                    onClick = { onSetLayout(ReadingLayout.DOUBLE) },
                    label = { Text(stringResource(Res.string.book_reader_layout_double)) },
                )
            }
            SectionLabel(text = stringResource(Res.string.book_reader_fit_mode), topPadding = 8.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = fitMode == ReaderFitMode.FIT_WIDTH,
                    onClick = { onSetFitMode(ReaderFitMode.FIT_WIDTH) },
                    label = { Text(stringResource(Res.string.book_reader_fit_width)) },
                )
                FilterChip(
                    selected = fitMode == ReaderFitMode.FIT_PAGE,
                    onClick = { onSetFitMode(ReaderFitMode.FIT_PAGE) },
                    label = { Text(stringResource(Res.string.book_reader_fit_page)) },
                )
                FilterChip(
                    selected = fitMode == ReaderFitMode.ORIGINAL,
                    onClick = { onSetFitMode(ReaderFitMode.ORIGINAL) },
                    label = { Text(stringResource(Res.string.book_reader_fit_original)) },
                )
            }
            SectionLabel(text = stringResource(Res.string.book_reader_behavior))
            SettingsSwitchRow(
                label = stringResource(Res.string.book_reader_volume_keys),
                checked = behavior.volumeKeyPaging,
                onChange = { onBehaviorChange(behavior.copy(volumeKeyPaging = it)) },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.book_reader_animated_turns),
                checked = behavior.animatedPageTurns,
                onChange = { onBehaviorChange(behavior.copy(animatedPageTurns = it)) },
            )
            if (tocAvailable) {
                SettingsSwitchRow(
                    label = stringResource(Res.string.book_reader_toc_rail),
                    checked = behavior.tocRailVisible,
                    onChange = { onBehaviorChange(behavior.copy(tocRailVisible = it)) },
                )
            }
            if (tocAvailable) {
                TextButton(
                    onClick = onOpenToc,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(imageVector = Tabler.Outline.List, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(Res.string.book_reader_toc))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
