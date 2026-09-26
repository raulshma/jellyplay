package com.raulshma.jellyplay.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.core.ui.components.MarkdownText
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.whatsnew.WhatsNewEntryCard
import com.raulshma.jellyplay.core.ui.components.focusIndicator

/**
 * The post-update What's New sheet (mobile bottom sheet / TV dialog, via
 * [TvSafeSheet] — same chassis as the update sheet): the release's entry
 * cards derived from its release-notes `## What's New` table, each with an
 * optional deep link — or, for a release authored without the table, the
 * release body as markdown. Any close path goes through [onDismiss] (the
 * coordinator stamps the version seen) — including the deep-link path, so
 * navigating away also consumes the prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    release: WhatsNewRelease,
    onNavigateToEntry: (com.raulshma.jellyplay.core.model.WhatsNewEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TvSafeSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Mirror the update sheet's expanded-notes sizing: the cards
                // region scrolls inside a ~90% height surface.
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.whatsnew_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.whatsnew_release_title, release.version),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            release.date?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                release.title?.let { headline ->
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                val body = release.body
                if (release.entries.isNotEmpty()) {
                    release.entries.forEach { entry ->
                        WhatsNewEntryCard(
                            entry = entry,
                            onNavigate = if (entry.target != null) {
                                { onNavigateToEntry(entry) }
                            } else {
                                null
                            },
                        )
                    }
                } else if (!body.isNullOrBlank()) {
                    // A release authored without the What's New table still
                    // has its prose: render the body as markdown.
                    MarkdownText(
                        text = body,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.focusIndicator(),
                ) { Text(stringResource(R.string.whatsnew_got_it)) }
            }
        }
    }
}
