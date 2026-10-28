plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Kotlin Multiplatform (shared/ tree).
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.ksp) apply false
    // `apply false` so Kover is only put on the build's plugin classpath —
    // with its version from this catalog — but never applied to the root
    // project by default. Application itself is gated on -PenableCoverage
    // below.
    alias(libs.plugins.kover) apply false
    // CI-only flake absorption for the shared-tree test suites (see the
    // subprojects gate below for the rationale).
    alias(libs.plugins.test.retry) apply false
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

// Kover instrumentation is meaningless for the macrobenchmark producer.
// Single source for both the plugin gate above and the aggregation wiring
// below.
val koverExcludedModules = setOf("baselineprofile")

subprojects {
    if (enableCoverage.get() && name !in koverExcludedModules) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }


    // Centralized Compose compiler configuration. Every module that applies the
    // Kotlin Compose plugin gets the same stability config (immutable JDK value
    // types like java.time.Instant) and, when opted in via -PenableComposeMetrics,
    // the same reports/metrics destinations.
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension>("composeCompiler") {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
            // Strong skipping: default-on in this toolchain (compiler 2.0.20+),
            // so the toolchain default IS the declaration and there is
            // deliberately no `strongSkipping` toggle to pin. The metrics
            // gate below is the check that skipping behavior stays as expected.
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

// Test-retry: the shared/ suites are load-flake-prone under real CI load —
// DataStore stateIn lag on slow disks, a ViewModel NPE under load — classic
// load flakes, not deterministic regressions. Retry those on CI only (local
// runs keep failing loudly so flakes stay visible). Deterministic bugs are
// fixed for real, never retried.
val ciRun = providers.environmentVariable("CI").map { it == "true" }.getOrElse(false)

subprojects {
    // Test workers inherit Gradle's 512m default heap. That stalled the
    // settings suite (mockk's per-call kotlin-reflect value-class walk
    // allocates heavily) and OOM'd core:data's Robolectric workers into
    // cascading NoClassDefFoundError failures — both looked like hung or
    // broken tests. Every module that owns test tasks gets the same bump.
    listOf(
        "org.jetbrains.kotlin.multiplatform",
        "org.jetbrains.kotlin.jvm",
        "com.android.library",
        "com.android.application",
        "com.android.test",
    ).forEach { plugin ->
        pluginManager.withPlugin(plugin) {
            tasks.withType<Test>().configureEach {
                maxHeapSize = "2g"
            }
        }
    }
    listOf(
        "org.jetbrains.kotlin.multiplatform",
        "org.jetbrains.kotlin.jvm",
    ).forEach { kotlinPlugin ->
        pluginManager.withPlugin(kotlinPlugin) {
            if (ciRun) {
                apply(plugin = "org.gradle.test-retry")
                tasks.withType<Test>().configureEach {
                    // `apply(plugin = ...)` generates no type-safe accessor for
                    // the plugin's retry { } DSL — configure the task extension
                    // (org.gradle.testretry.TestRetryTaskExtension) by name.
                    extensions.configure<org.gradle.testretry.TestRetryTaskExtension>("retry") {
                        maxRetries.set(3)
                        maxFailures.set(5)
                        failOnPassedAfterRetry.set(false)
                    }
                }
            }
        }
    }
}

// Aggregation wiring: each subproject is added to the root's "kover" dependency
// configuration so its data feeds the merged report tasks. That configuration
// only exists when the plugin was applied above, hence the same gate. "kover"
// is referenced by name because the plugins-block alias carries apply false,
// so no type-safe accessor is generated.
if (enableCoverage.get()) {
    dependencies {
        subprojects.forEach {
            if (it.name !in koverExcludedModules) {
                "kover"(it)
            }
        }
    }
}

// ── Locale-string propagation (repo-wide) ────────────────────────────────────
// The propagator (shared/core/i18n-tools jvmMain .../StringsPropagator.kt)
// reads a module's default-locale values/*.xml family as the declaration and
// inserts every key missing from a values-<lang>/ family verbatim, marked
// PENDING TRANSLATION — the write-side counterpart of the i18n parity
// ratchet (I18nParityRatchetTest, in :shared:core:i18n-tools' jvmTest),
// which counts PENDING-marked entries as still-missing so a repo-wide
// propagation cannot fake parity. The tasks run REPO-WIDE from the root:
// `--discover <repo-root>` walks shared/ + apps/ for every
// composeResources/values/strings.xml (I18nResourceDiscovery — the same
// walk the ratchet does), so one task pair covers all resource-carrying
// modules and the settings module's old per-module wiring is gone. Run
// `./gradlew propagateStrings` after adding default-locale keys;
// `checkStrings` fails while any gap remains. (No root `check` wiring: the
// root project applies no lifecycle plugins, and the ratchet's jvmTest —
// in every CI lane through the settings.gradle.kts derivation — enforces
// the same invariant.)
// Same shape as the generateSettingsCatalog/checkSettingsCatalog precedent.

// Resolvable classpath for the JavaExec tasks below: :shared:core:i18n-tools'
// JVM runtime variant (its compiled classes + kotlin-stdlib). Attribute-pinned
// so KMP variant selection cannot land on the module's android or metadata
// variant — this classpath must be the JVM one. A configuration (not a
// cross-project compilation read) because the build runs with the
// configuration cache: root may not reach into a subproject's model.
val stringsToolRuntimeClasspath = configurations.create("stringsToolRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(
            org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Usage.JAVA_RUNTIME),
        )
        attribute(
            org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Category.LIBRARY),
        )
        attribute(
            org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
            org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm,
        )
    }
}

dependencies {
    "stringsToolRuntimeClasspath"(project(":shared:core:i18n-tools"))
}

tasks.register<JavaExec>("propagateStrings") {
    group = "jellyplay"
    description = "Propagates default-locale strings into every shared/app module's locale tables (marked PENDING TRANSLATION)."
    mainClass.set("com.raulshma.jellyplay.core.i18ntools.StringsPropagatorKt")
    classpath = stringsToolRuntimeClasspath
    args(listOf("--discover", rootDir.absolutePath))
}

tasks.register<JavaExec>("checkStrings") {
    group = "verification"
    description = "Fails while any module's locale table misses default-locale keys."
    mainClass.set("com.raulshma.jellyplay.core.i18ntools.StringsPropagatorKt")
    classpath = stringsToolRuntimeClasspath
    args(listOf("--check", "--discover", rootDir.absolutePath))
}
