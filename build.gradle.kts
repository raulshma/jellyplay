plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Kotlin Multiplatform (shared/ tree, docs/kmp-migration-plan.md).
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.ksp) apply false
    // `apply false` (instead of the previous plain alias) so Kover is only put
    // on the build's plugin classpath — with its version from this catalog —
    // but never applied to the root project by default. Application itself is
    // gated on -PenableCoverage below.
    alias(libs.plugins.kover) apply false
}

// Kover is opt-in: unless the build is invoked with -PenableCoverage (e.g.
// `./gradlew koverHtmlReport -PenableCoverage`), the plugin is neither applied
// nor configured on any project, keeping it out of everyday config time. A
// bare -PenableCoverage sets an empty string, so blank counts as enabled.
val enableCoverage = providers.gradleProperty("enableCoverage")
    .map { it.isBlank() || it.toBoolean() }
    .orElse(false)

// The root project hosts the aggregated (merged) Kover report tasks, so it
// needs the plugin too — but only when coverage is actually enabled.
if (enableCoverage.get()) {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
}

// Kover instrumentation is meaningless for the macrobenchmark producer, the
// docs site, and the shared test-fixtures module. Single source for both the
// plugin gate above and the aggregation wiring below.
val koverExcludedModules = setOf("website", "baselineprofile", "testing")

subprojects {
    if (enableCoverage.get() && name !in koverExcludedModules) {
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

// Aggregation wiring: each subproject is added to the root's "kover" dependency
// configuration so its data feeds the merged report tasks. That configuration
// only exists when the plugin was applied above, hence the same gate. "kover"
// is referenced by name because the type-safe accessor is generated only when
// Kover is applied via the plugins block (it no longer is).
if (enableCoverage.get()) {
    dependencies {
        subprojects.forEach {
            if (it.name !in koverExcludedModules) {
                "kover"(it)
            }
        }
    }
}
