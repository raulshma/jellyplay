package com.raulshma.jellyplay.feature.auth

/**
 * ViewModel-side platform seam for the Android 17+ local network permission
 * (settings-conveyor `StorageMountsProvider` shape — interface + per-platform
 * Koin definitions, because the check needs a Context on Android and the
 * legacy :core:ui LocalNetworkAccess object stays the single source of truth
 * for the platform logic).
 *
 * The add-server failure classifier asks whether a connection failure
 * against [address] is *plausibly caused* by a missing local-network
 * permission: enforcement active, grant absent, and the target actually
 * local (public hosts are unaffected by the permission, so blaming it there
 * would be misleading). Android folds the legacy
 * `enforced && !isGranted(context) && isLocalAddress(address)` expression;
 * desktop never blames (non-enforcing platform).
 */
fun interface LocalNetworkStatus {
    fun blamesFailureOnPermission(address: String): Boolean
}
