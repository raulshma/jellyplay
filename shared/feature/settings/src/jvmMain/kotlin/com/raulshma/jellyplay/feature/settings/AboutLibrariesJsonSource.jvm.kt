package com.raulshma.jellyplay.feature.settings

/**
 * Desktop actual of the [AboutLibrariesJsonSource] seam: reads
 * `aboutlibraries.json` off the classpath (aboutlibraries-core's Gradle
 * plugin can drop it there for the desktop build). null — missing or failed
 * read — lands in the ViewModel's existing load-error state (desktop latent).
 */
internal class DesktopAboutLibrariesJsonSource : AboutLibrariesJsonSource {
    override fun read(): String? =
        runCatching {
            Thread.currentThread().contextClassLoader
                ?.getResourceAsStream("aboutlibraries.json")
                ?.readBytes()
                ?.decodeToString()
        }.getOrNull()
}
