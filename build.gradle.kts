plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kover)
}

subprojects {
    if (name != "website") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    // Centralized Compose compiler configuration. Every module that applies the
    // Kotlin Compose plugin gets the same stability config (immutable JDK value
    // types like java.time.Instant) and, when opted in via -PenableComposeMetrics,
    // the same reports/metrics destinations. This replaces a copy of the same
    // block that was previously duplicated across ~25 per-module build files.
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension>("composeCompiler") {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
            if (project.hasProperty("enableComposeMetrics")) {
                metricsDestination = layout.buildDirectory.dir("compose-metrics")
                reportsDestination = layout.buildDirectory.dir("compose-reports")
            }
        }
    }
}

dependencies {
    subprojects.forEach {
        if (it.name != "website" && it.name != "baselineprofile") {
            kover(it)
        }
    }
}
