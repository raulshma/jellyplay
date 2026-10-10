package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Devices
import com.composables.icons.tabler.outline.Graph
import com.composables.icons.tabler.outline.Inbox
import com.composables.icons.tabler.outline.PlugConnected
import com.composables.icons.tabler.outline.Stars
import com.raulshma.jellyplay.core.data.repository.JellyPushState
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_entry_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_unread_count
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_entry_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_locked
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_plugin_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_plugin_section_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_push_state_no_distributor
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_push_state_registering
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_push_state_registered
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_push_state_server_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_push_state_unregistered
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_entry_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_push_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_sync_enabled_subtitle
import org.jetbrains.compose.resources.stringResource

/**
 * The `group_jellyplay` section: the companion plugin's whole root-screen face
 * (ADR 0010) in ONE grouped row — the navigation entries (Sync across devices,
 * Messages, My ratings, Your watching) above the per-feature switches, inside
 * a single [SettingsGroup] card instead of the five sibling sections these
 * used to be scattered across. The section hides unless the probe reports
 * AVAILABLE, and each row inside still rides its own gate: the sync entry
 * needs the `settings-sync` meta key exposed; the Messages / My ratings /
 * Your-watching entries need their feature key exposed AND the user's toggle
 * on; the switches need their key exposed, the admin-context ones also an
 * admin user. A gate-closed row just doesn't compose, and an all-gates-closed
 * section renders nothing (the graceful-absent contract: a stock server shows
 * nothing here). A probe-exposed feature whose toggle is off still renders its
 * switch — otherwise it could never be switched back on; the switch STATE
 * rides the gate's toggle set, not the probe.
 *
 * The admin-defaults tri-state reaches the switches as FORCED LOCKS: the sync
 * engine's [SettingsViewModel.jellyPlayForcedKeys] carries the keys (the
 * exact `"ns/key"` wire composite [jellyPlayToggleSyncKey] derives) whose
 * admin default is `forced` — those switches render disabled with the
 * "Set by your server admin" subtitle (the `unset`/`suggested` modes keep the
 * switch writable; suggested prefill only). The switch STATE still rides the
 * gate's toggle set — a forced-off switch reads off, a forced-on one on.
 *
 * The `push` switch is the one row with a DYNAMIC subtitle: it reads the push
 * repository's state machine (Registered / No distributor app / Server push
 * disabled), so the user sees why pushes aren't landing without leaving the
 * section — unless the row is admin-locked, which outranks the state line.
 * Its toggle write stays the ordinary gate write — the repository's own gate
 * collector (running with the events session) performs the register/unregister
 * half.
 *
 * The Messages unread count — previously the messages entry's trailing text —
 * rides the collapsed group's header badge, so the signal survives the
 * consolidation. The inbox is refreshed once each visit where the messages
 * gate is open (the session controller only refreshes it at stream start).
 *
 * Probing rides the section's visibility: opening settings is the only moment
 * a user can act on anything here, so it is the only moment worth probing.
 * UNKNOWN → one refresh; UNAVAILABLE/AVAILABLE never re-probe (the identity
 * reset in the store re-arms the next visit).
 */
@Composable
internal fun SettingsJellyPlayPluginSection(
    viewModel: SettingsViewModel,
    lastClickedSettingId: String?,
    onOpenSync: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenUserRatings: () -> Unit,
    onOpenYourWatching: () -> Unit,
) {
    val pluginStatus by viewModel.jellyPlayPluginStatus.collectAsStateWithLifecycle()
    val features by viewModel.jellyPlayPluginFeatures.collectAsStateWithLifecycle()
    val featureToggles by viewModel.jellyPlayFeatureToggles.collectAsStateWithLifecycle()
    val pushState by viewModel.jellyPlayPushState.collectAsStateWithLifecycle()
    val forcedKeys by viewModel.jellyPlayForcedKeys.collectAsStateWithLifecycle()
    val unreadCount by viewModel.jellyPlayUnreadMessageCount.collectAsStateWithLifecycle()
    val isAdmin = viewModel.currentUser?.isAdmin == true

    LaunchedEffect(pluginStatus) {
        if (pluginStatus == JellyPlayPluginStatus.UNKNOWN) {
            viewModel.refreshJellyPlayPluginStatus()
        }
    }

    if (pluginStatus != JellyPlayPluginStatus.AVAILABLE) {
        return
    }

    val syncVisible = JellyPlayPluginFeatures.SettingsSync in features
    val messagesVisible = JellyPlayPluginFeatures.Messages in features &&
        JellyPlayPluginFeatures.Messages in featureToggles
    val userRatingsVisible = JellyPlayPluginFeatures.UserRatings in features &&
        JellyPlayPluginFeatures.UserRatings in featureToggles
    val yourWatchingVisible = JellyPlayPluginFeatures.Analytics in features &&
        JellyPlayPluginFeatures.Analytics in featureToggles
    val visibleToggles = JellyPlayFeatureRows.declared.filter { declared ->
        declared.featureKey in features && (!declared.adminOnly || isAdmin)
    }
    // rowCount is a documented non-derivable (ADR 0009 rule 2): the four
    // navigation entries carry content gates with no admission vocabulary,
    // and the toggle half's membership comes from the LIVE probe registry,
    // so no declared group can count it.
    val rowCount = listOf(syncVisible, messagesVisible, userRatingsVisible, yourWatchingVisible)
        .count { it } + visibleToggles.size
    if (rowCount == 0) {
        return
    }

    // One inbox pull per settings visit where the gate is open — the session
    // controller only refreshes the inbox at stream start.
    LaunchedEffect(messagesVisible) {
        if (messagesVisible) viewModel.refreshJellyPlayInbox()
    }

    SettingsGroup(
        icon = Tabler.Outline.PlugConnected,
        title = stringResource(Res.string.settings_jellyplay_plugin_section),
        summary = { stringResource(Res.string.settings_jellyplay_plugin_section_summary) },
        badge = if (messagesVisible && unreadCount > 0) {
            {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.jellyplay_msgs_unread_count, unreadCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        } else null,
        initiallyExpanded = false,
    ) {
        SettingsItemList(total = rowCount) {
            if (syncVisible) {
                SettingListItem(
                    icon = Tabler.Outline.Devices,
                    title = stringResource(Res.string.settings_jellyplay_sync),
                    subtitle = stringResource(Res.string.ss_jellyplay_sync_enabled_subtitle),
                    onClick = onOpenSync,
                )
            }
            if (messagesVisible) {
                SettingListItem(
                    icon = Tabler.Outline.Inbox,
                    title = stringResource(Res.string.jellyplay_msgs_title),
                    subtitle = stringResource(Res.string.jellyplay_msgs_entry_subtitle),
                    trailingText = if (unreadCount > 0) {
                        stringResource(Res.string.jellyplay_msgs_unread_count, unreadCount)
                    } else {
                        null
                    },
                    onClick = onOpenMessages,
                )
            }
            if (userRatingsVisible) {
                SettingListItem(
                    icon = Tabler.Outline.Stars,
                    title = stringResource(Res.string.jellyplay_ur_title),
                    subtitle = stringResource(Res.string.jellyplay_ur_entry_subtitle),
                    onClick = onOpenUserRatings,
                )
            }
            if (yourWatchingVisible) {
                SettingListItem(
                    icon = Tabler.Outline.Graph,
                    title = stringResource(Res.string.settings_jellyplay_yw_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_yw_entry_subtitle),
                    onClick = onOpenYourWatching,
                )
            }
            // ADR 0009 carve-out: unlike the static per-row emission bodies the
            // ADR keeps hand-written, these rows are PROBE-DRIVEN — membership
            // comes from the live `features` registry, not a static list, and
            // every row is the SAME uniform toggle payload. The only dynamic
            // faces (the admin lock and the push state machine's subtitle)
            // stay inline at this emission site, per the ADR's "dynamic value
            // reads stay at emission sites" rule.
            visibleToggles.forEach { declared ->
                val row = declared.row
                val locked = jellyPlayFeatureLocked(declared.featureKey, forcedKeys)
                SettingToggleItem(
                    icon = rowIcon(row),
                    title = rowTitle(row),
                    subtitle = when {
                        locked -> stringResource(Res.string.settings_jellyplay_feature_locked)
                        declared.featureKey == JellyPlayPluginFeatures.Push -> jellyPlayPushSubtitle(pushState)
                        else -> rowSubtitle(row)
                    },
                    checked = declared.featureKey in featureToggles,
                    highlighted = lastClickedSettingId == row.id,
                    enabled = !locked,
                    onCheckedChange = { enabled ->
                        viewModel.setJellyPlayFeatureEnabled(declared.featureKey, enabled)
                    },
                )
            }
        }
    }
}

/**
 * The sync wire key one feature's toggle rides — the composite `"ns/key"`
 * form the server's admin-defaults `modes` map addresses: the `prefs`
 * namespace (the sync adapter's namespace) over the DataStore KEY NAME the
 * gate writes — the key grammar single-homed on
 * [com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate.toggleKeyName];
 * dots included; the adapter syncs raw DataStore key names verbatim (see
 * [com.raulshma.jellyplay.core.data.repository.JellyPlayPreferencesSyncAdapter]'s
 * snapshot), so the composite is `prefs/pluginFeature.events.enabled`, never
 * a slash-separated feature path.
 */
internal fun jellyPlayToggleSyncKey(featureKey: String): String =
    "prefs/" + com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate.toggleKeyName(featureKey)

/** Whether one feature's toggle is admin-locked (`forced` in the resolved modes). */
internal fun jellyPlayFeatureLocked(featureKey: String, forcedKeys: Set<String>): Boolean =
    jellyPlayToggleSyncKey(featureKey) in forcedKeys

/**
 * The push row's subtitle: the registration machine's state, word for word
 * the states the JellyPushRepository KDoc names. [JellyPushState.Unregistered]
 * renders the static search subtitle (the "enable to register" default).
 */
@Composable
private fun jellyPlayPushSubtitle(state: JellyPushState): String = when (state) {
    JellyPushState.Unregistered ->
        stringResource(Res.string.ss_jellyplay_feature_push_subtitle)
    JellyPushState.Registering ->
        stringResource(Res.string.settings_jellyplay_push_state_registering)
    is JellyPushState.Registered ->
        stringResource(Res.string.settings_jellyplay_push_state_registered)
    JellyPushState.NoDistributor ->
        stringResource(Res.string.settings_jellyplay_push_state_no_distributor)
    JellyPushState.ServerPushOff ->
        stringResource(Res.string.settings_jellyplay_push_state_server_off)
}

/**
 * Whether one plugin feature's ENTRY may render (probe AVAILABLE + the
 * feature exposed + the user's toggle on) — the reactive visibility predicate
 * for the plugin-backed entries outside the section (the profile banner's
 * newsletter icon).
 */
@Composable
internal fun isJellyPlayFeatureShown(
    viewModel: SettingsViewModel,
    featureKey: String,
): Boolean {
    val pluginStatus by viewModel.jellyPlayPluginStatus.collectAsStateWithLifecycle()
    val features by viewModel.jellyPlayPluginFeatures.collectAsStateWithLifecycle()
    val featureToggles by viewModel.jellyPlayFeatureToggles.collectAsStateWithLifecycle()
    return pluginStatus == JellyPlayPluginStatus.AVAILABLE &&
        featureKey in features &&
        featureKey in featureToggles
}

/**
 * A plugin screen's section heading (the sync + your-watching screens' shared
 * label — same type ramp and color, the caller owns the padding).
 */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
