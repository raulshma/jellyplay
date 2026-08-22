package com.raulshma.jellyplay.core.model

/**
 * A `UserDataChanged` push from the server: the listed items' user data
 * (played / favorite / playback position) changed for [userId], e.g. from
 * another client or a server-side task.
 */
data class UserDataChange(
    val userId: String,
    val itemIds: List<String>,
)
