package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.JellyPlayEventsRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayMessage
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The companion-plugin inbox screen's model (ADR 0010): the plugin's
 * admin-published messages with read state.
 *
 * The screen is reachable ONLY through the settings root's capability-gated
 * "Messages" entry (plugin probe AVAILABLE + the `messages` feature key),
 * but the api calls are STILL feature-gated here — the gate is re-checked at
 * every call, so the api client is never touched without it (ADR 0010's
 * gating rule; the capabilities contract can degrade mid-session).
 */
class JellyPlayMessagesViewModel(
    private val eventsRepository: JellyPlayEventsRepository,
    private val statusStore: JellyPlayPluginStatusStore,
) : JellyPlayViewModel() {

    /** The inbox as the repository last refreshed it (screen refreshes on open). */
    val messages: StateFlow<List<JellyPlayMessage>> = eventsRepository.inbox

    /** One refresh fired by the screen on open; failure keeps the current list. */
    fun refresh() {
        scope.launch {
            if (statusStore.hasFeature(JellyPlayPluginFeatures.Messages)) {
                eventsRepository.refreshInbox()
            }
        }
    }

    /** Marks [messageId] read (optimistic fold lives in the repository). */
    fun markRead(messageId: String) {
        scope.launch {
            if (statusStore.hasFeature(JellyPlayPluginFeatures.Messages)) {
                eventsRepository.markRead(messageId)
            }
        }
    }
}
