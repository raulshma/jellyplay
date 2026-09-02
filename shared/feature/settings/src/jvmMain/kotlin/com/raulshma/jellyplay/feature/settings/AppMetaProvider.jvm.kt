package com.raulshma.jellyplay.feature.settings

import java.util.Properties

/**
 * Desktop actual of the [AppMetaProvider] seam: no package manager, so the
 * build declares literals. The version comes from the
 * `desktop-build.properties` classpath resource that :apps:desktop's build
 * generates (`-PjellyplayVersionName`, e.g. "0.11.0-alpha.1" on KMP alpha
 * release lanes); when the resource is absent — settings-module unit tests,
 * IDE runs before a first processResources — it falls back to the historical
 * "dev". Still a release-style non-debug build with SDK placeholders
 * (surfaced as "API 0") because the min/target Android rows have no desktop
 * equivalent.
 */
internal class DesktopAppMetaProvider : AppMetaProvider {
    override val versionName: String by lazy {
        runCatching {
            DesktopAppMetaProvider::class.java.classLoader
                .getResourceAsStream("desktop-build.properties")
                ?.use { stream -> Properties().apply { load(stream) }.getProperty("versionName") }
        }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "dev"
    }
    override val isDebugBuild: Boolean = false
    override val minSdk: Int = 0
    override val targetSdk: Int = 0
}
