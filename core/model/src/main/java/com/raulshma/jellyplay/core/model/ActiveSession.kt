package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * The fully established session — one server plus its authenticated user,
 * published by the network engine as ONE atomic value.
 *
 * `JellyfinApiEngine` also exposes `currentServer` and `currentUser` as
 * separate StateFlows, so any caller that updates them as two assignments
 * (login / switchUser / disconnect) would let a downstream
 * `combine(currentServer, currentUser)` observe a synthetic mixed
 * `(newServer, oldUser)` intermediate — an identity that never existed. The
 * session flow carrying this type is updated inside the engine's critical
 * sections, so a transition is always observed as a single step from one
 * stable session to the next, or to/from `null` (= no fully established
 * identity; either side missing).
 */
@Immutable
data class ActiveSession(
    val server: ServerInfo,
    val user: UserInfo,
)
