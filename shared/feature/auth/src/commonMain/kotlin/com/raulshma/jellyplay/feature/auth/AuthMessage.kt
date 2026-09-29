package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * Screen-forward message seal for auth flows — the commonMain-safe
 * replacement for the legacy `appContext.getString(R.string.…)` values the
 * ViewModels used to build error strings at failure time (music
 * MixErrorMessage / syncplay SyncPlayMessage conveyor pattern). The resource
 * stays unresolved until render so the locale resolves at display; exception
 * messages are carried raw (already final, no localized form).
 *
 * No variant carries format arguments: every auth error resource is
 * args-free (the two format-bearing strings, `auth_remove_server_message` /
 * `auth_remove_user_message`, are pre-resolved in composition at their
 * render sites — newsletter args-free seal shape).
 *
 * M3 conveyor unification: the seal was exactly the shared two-variant
 * [UiMessage] shape (args-free by the note above — [UiMessage.Resource]'s
 * optional args default to empty), so it is now a typealias. Kotlin forbids
 * reaching a nested classifier or companion through a typealias (KEEP-40),
 * so construction and `when` branches name the canonical container directly
 * — `UiMessage.Resource` / `UiMessage.Raw` / `UiMessage.of`, the same
 * container-qualified convention core.ui.message's `UiText.Resource` uses —
 * while every TYPE position (state fields, flow element types) keeps the
 * feature alias. Screens collapse with `asText` (core.ui.message).
 */
typealias AuthMessage = UiMessage
