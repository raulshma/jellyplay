package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam feeding [AboutViewModel]'s App Info group (V3 settings
 * conveyor). Android derives every member from PackageManager / the
 * application info; desktop has no package manager, so the actual declares
 * build literals ("dev", always-release, SDK 0 placeholders).
 */
interface AppMetaProvider {
    /** `versionName` from the package info, or null when the platform has none. */
    val versionName: String?

    /** FLAG_DEBUGGABLE on Android; the desktop build declares a release-style constant. */
    val isDebugBuild: Boolean

    /** Min SDK the build targets (0 = not applicable on this platform). */
    val minSdk: Int

    /** Target SDK the build targets (0 = not applicable on this platform). */
    val targetSdk: Int
}
