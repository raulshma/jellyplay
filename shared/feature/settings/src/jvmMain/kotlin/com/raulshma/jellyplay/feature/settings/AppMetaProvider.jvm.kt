package com.raulshma.jellyplay.feature.settings

/**
 * Desktop actual of the [AppMetaProvider] seam: no package manager, so the
 * build declares literals — a "dev" version string, a release-style
 * non-debug build, and SDK placeholders (surfaced as "API 0") because the
 * min/target Android rows have no desktop equivalent.
 */
internal class DesktopAppMetaProvider : AppMetaProvider {
    override val versionName: String = "dev"
    override val isDebugBuild: Boolean = false
    override val minSdk: Int = 0
    override val targetSdk: Int = 0
}
