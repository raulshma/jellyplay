package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.animation.pressScale
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.MarkdownText
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.whatsnew.WhatsNewEntryCard
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.WhatsNewTargets
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreRes
import com.raulshma.jellyplay.core.ui.generated.resources.detail_cd_collapse
import com.raulshma.jellyplay.core.ui.generated.resources.detail_cd_expand
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_whatsnew_archive_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_whatsnew_title
import org.koin.compose.viewmodel.koinViewModel

/**
 * The What's New archive: every known release (newest first) with the same
 * entry cards the post-update sheet shows — this screen is the durable home
 * of that content (the sheet is the transient one). Only the newest release
 * opens expanded; every older one starts collapsed to its header, expandable
 * on tap (the [SettingsGroup]-style chevron). Deep links navigate through
 * [onNavigate], the same seam every settings drill-in uses.
 */
@Composable
fun WhatsNewScreen(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: WhatsNewViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    val expandedVersions = rememberExpandableReleases(viewModel.releases)

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_whatsnew_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) {
        if (viewModel.releases.isEmpty()) {
            Text(
                text = stringResource(CoreRes.string.core_ui_whatsnew_archive_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        } else {
            val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(isTv) + 8.dp,
                    end = adaptiveInfo.contentPadding(isTv) + 8.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                viewModel.releases.forEach { release ->
                    item(key = release.version) {
                        ReleaseSection(
                            release = release,
                            expanded = release.version in expandedVersions.value,
                            onToggleExpanded = { expandedVersions.toggle(release.version) },
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }
}

/** [rememberSaveable] saver for the expanded-release version set. */
private val ExpandedVersionsSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toHashSet() },
)

/**
 * The archive's expansion state: the set of expanded release versions. The
 * list arrives asynchronously, so the "newest expanded" default can't be the
 * [rememberSaveable] initializer — it is seeded once, on the first non-empty
 * list, and after that left entirely to the user (collapsing the newest
 * sticks across refreshes, rotation, and process death).
 */
@Composable
private fun rememberExpandableReleases(
    releases: List<WhatsNewRelease>,
): MutableState<Set<String>> {
    val expanded = rememberSaveable(stateSaver = ExpandedVersionsSaver) {
        mutableStateOf(setOf<String>())
    }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(releases) {
        val latest = releases.firstOrNull() ?: return@LaunchedEffect
        if (!seeded) {
            seeded = true
            expanded.value += latest.version
        }
    }
    return expanded
}

/** Flips one release's membership in the expanded set (new set, not in-place). */
private fun MutableState<Set<String>>.toggle(version: String) {
    value = if (version in value) value - version else value + version
}

@Composable
private fun ReleaseSection(
    release: WhatsNewRelease,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "releaseChevronRotation",
    )
    val tvFocusState = rememberTvFocusState(focusedScale = 1.02f)
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(
                    interactionSource = interactionSource,
                    defaultScale = 0.98f,
                    spec = MaterialTheme.motionScheme.fastSpatialSpec(),
                )
                .clip(ShapeCache.smooth16)
                .then(tvFocusState.focusModifier)
                .tvFocusIndicator(tvFocusState, ShapeCache.smooth16)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onToggleExpanded() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "v${release.version}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                release.date?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                release.title?.let { headline ->
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Tabler.Outline.ChevronDown,
                contentDescription = stringResource(
                    if (expanded) CoreRes.string.detail_cd_collapse
                    else CoreRes.string.detail_cd_expand,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ) + expandVertically(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            exit = fadeOut(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            ) + shrinkVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (release.entries.isNotEmpty()) {
                    release.entries.forEach { entry ->
                        WhatsNewEntryCard(
                            entry = entry,
                            onNavigate = WhatsNewTargets
                                .resolve(entry.target, entry.highlightSettingId)
                                ?.let { route -> { onNavigate(route) } },
                        )
                    }
                } else {
                    val body = release.body
                    if (!body.isNullOrBlank()) {
                        // Releases authored before the What's New table (or
                        // corrected without one) still render their notes.
                        MarkdownText(
                            text = body,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
