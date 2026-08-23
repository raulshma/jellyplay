package com.raulshma.jellyplay.feature.settings

import android.content.Context

/**
 * Android actual of the [AppMetaProvider] seam — the pre-migration
 * AboutViewModel bodies, verbatim: versionName from the package info,
 * FLAG_DEBUGGABLE from the application info, and runtime min/target SDK read
 * from the same application info so the About screen never drifts from the
 * actual build configuration in app/build.gradle.kts.
 */
internal class AndroidAppMetaProvider(
    private val context: Context,
) : AppMetaProvider {
    override val versionName: String?
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    override val isDebugBuild: Boolean
        get() = (context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override val minSdk: Int
        get() = context.applicationInfo.minSdkVersion

    override val targetSdk: Int
        get() = context.applicationInfo.targetSdkVersion
}
