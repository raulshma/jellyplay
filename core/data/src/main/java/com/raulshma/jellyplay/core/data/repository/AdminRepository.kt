package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SystemInfo

/**
 * Server-administration seam for feature screens. Features consume screen
 * operations here instead of the wide [com.raulshma.jellyplay.core.network.JellyfinApiClient]
 * transport interface; the implementation owns fan-out, lookups, fallbacks,
 * and realtime channels so callers cannot hand-roll them per screen.
 */
interface AdminRepository {

    /** Server telemetry for the About screen and the admin dashboard. */
    suspend fun getSystemInfo(): Result<SystemInfo>
}
