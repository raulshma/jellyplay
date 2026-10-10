package com.raulshma.jellyplay.core.data.notification

import com.raulshma.jellyplay.core.data.repository.JellyPlayPluginEvent

/**
 * The platform seam that maps the companion-plugin's live SSE `new-media`
 * push (ADR 0010) onto the host's local-notification surface — on Android
 * the existing NotificationDispatcher/NotificationChannelManager tray path,
 * so the event lands in the same notification tray / notification-center
 * surface the periodic new-media check already uses.
 *
 * The JellyPlay events session controller resolves this seam with Koin's
 * `getOrNull`: Android's notification module registers the real impl;
 * desktop registers nothing, so a `new-media` event degrades to the log-only
 * path there (no tray-notification infrastructure exists on the desktop
 * shell — the smallest honest version, per ADR 0010's additive light-ups).
 */
fun interface JellyPlayNewMediaNotifier {

    /** Post [event] to the platform's local-notification surface. */
    fun notifyNewMedia(event: JellyPlayPluginEvent.NewMedia)
}
