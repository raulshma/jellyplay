package jellyplay.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for `shared/` modules that use Compose: applies
 * [KmpLibraryBasePlugin] plus the Compose compiler plugin and the JetBrains
 * Compose Multiplatform distribution plugin (the only publisher of real JVM
 * compose binaries — see the catalog note on `composeMultiplatform`).
 *
 * The per-module `compose.resources` packageOfResClass tail stays in each
 * module's build file: the package string is per-module legacy data (and
 * `feature.player.book`'s breaks any derivable convention), so the plugin
 * cannot own it without introducing a config extension.
 */
class KmpLibraryComposePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val pluginManager = project.pluginManager
        pluginManager.apply(KmpLibraryBasePlugin::class.java)
        // Root build.gradle.kts centralizes the compose compiler stability
        // config via withPlugin("org.jetbrains.kotlin.plugin.compose") — that
        // gate fires for this application too, so no per-module config needed.
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")
    }
}
