package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable

/**
 * One-shot user-facing messages emitted by [ManageSeriesViewModel] and
 * rendered by [ManageSeriesScreen] as a snackbar — the details module's
 * one-shot message-channel idiom (DetailMessage / the codified
 * StateManagementConventions §2: buffered Channel, exposed as
 * `receiveAsFlow()`, consumed exactly once).
 *
 * Replaces the former `ManageSeriesUiState.userMessage` parked field + the
 * [ManageSeriesUiEvent.ClearUserMessage] consume dance: a StateFlow replays
 * its current value to every new collector, so the old field re-fired the
 * snackbar on state restoration / process recreation / re-subscription for
 * an action that happened once. The Channel never replays.
 *
 * Every message today is fully-resolved text — the ViewModel already folded
 * the [com.raulshma.jellyplay.core.data.error.UserErrorMessages]
 * server-text/fallback resolution and interpolated the episode/season
 * titles — so a single [Text] variant. New message shapes land here as new
 * variants rather than as new nullable uiState fields.
 */
@Immutable
sealed interface ManageSeriesMessage {
    data class Text(val text: String) : ManageSeriesMessage
}
