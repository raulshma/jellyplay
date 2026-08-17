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
    // Kover instrumentation is meaningless for the macrobenchmark producer and
    // for the shared test-fixtures module (both excluded from aggregation below).
    if (name != "website" && name != "baselineprofile" && name != "testing") {
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
            // hasProperty() would be true for the gradle.properties default
            // "enableComposeMetrics=false" — parse the value instead.
            if (providers.gradleProperty("enableComposeMetrics").map(String::toBoolean).getOrElse(false)) {
                metricsDestination = layout.buildDirectory.dir("compose-metrics")
                reportsDestination = layout.buildDirectory.dir("compose-reports")
            }
        }
    }

    // Explicit Kotlin JVM target, centralized. With AGP 9 built-in Kotlin no
    // module applies the Kotlin plugin, and the compiler's jvmTarget silently
    // follows each module's compileOptions.targetCompatibility; pinning it once
    // here (matching the Java 17 every module already declares) makes the
    // target explicit without a per-module block.
    listOf(
        "com.android.application",
        "com.android.library",
        "com.android.test",
    ).forEach { agp ->
        pluginManager.withPlugin(agp) {
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>("kotlin") {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }
}

dependencies {
    subprojects.forEach {
        if (it.name != "website" && it.name != "baselineprofile" && it.name != "testing") {
            kover(it)
        }
    }
}
