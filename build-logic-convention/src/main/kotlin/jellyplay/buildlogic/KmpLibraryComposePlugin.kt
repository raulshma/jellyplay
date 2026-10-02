package jellyplay.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Per-module tunables for the Compose convention plugin. Registered as the
 * `jellyplay` extension by [KmpLibraryComposePlugin]; every knob carries a
 * convention-plugin default so ordinary modules leave the block out entirely.
 */
interface JellyPlayExtension {

    /**
     * Fully-qualified class name of the generated compose-resources `Res`
     * holder (`compose.resources.packageOfResClass`).
     *
     * Default: derived from the module's Gradle path — `:shared` dropped,
     * remaining segments joined with dots (directory hyphens become dots),
     * suffixed with `.generated.resources`. `:shared:feature:home` →
     * `com.raulshma.jellyplay.feature.home.generated.resources` — the exact
     * string every migrated module's legacy build file carried (the legacy
     * per-module package, kept so migrated sources need no import churn).
     *
     * Override for modules whose legacy package breaks the derivation —
     * today only `feature:player-book`, whose legacy Res package is
     * `...feature.book...` (the `player` path segment was collapsed at the
     * legacy module's creation and its generated accessors were shipped in
     * that package ever since).
     */
    val resPackage: Property<String>
}

/**
 * Convention plugin for `shared/` modules that use Compose: applies
 * [KmpLibraryBasePlugin] plus the Compose compiler plugin and the JetBrains
 * Compose Multiplatform distribution plugin (the only publisher of real JVM
 * compose binaries — see the catalog note on `composeMultiplatform`).
 *
 * Also owns the UNIVERSAL commonMain dependency bundle (XC-2) — the subset
 * every Compose module's build file used to repeat verbatim (~90% of the
 * per-module dependency blocks). This deliberately reverses the "every
 * module owns its dependency list" stance recorded in the base plugin's
 * KDoc for exactly this subset; everything outside it (project edges,
 * coil/datetime/serialization/paging/saveable/activity/platform-lane deps,
 * test lanes, ...) stays declared per module.
 *
 * The bundle — `api` vs `implementation` mirrors what modules declared
 * themselves (core:designsystem api-exported the five JB compose artifacts
 * so every feature façade compiles against Compose; nothing else was ever
 * api-scoped, so keeping them implementation preserves each module's
 * exposed API byte-for-byte):
 *
 *  - `api`: jb-compose-runtime / ui / foundation / animation / material3
 *    (the JB CMP distribution — Android targets redirect to androidx).
 *  - `implementation`: compose-resources runtime
 *    (org.jetbrains.compose.components:components-resources), the two
 *    tabler icon packs, navigation3 runtime + ui, lifecycle-viewmodel,
 *    lifecycle-runtime-compose, koin core + compose + compose-viewmodel.
 *
 * Non-Compose modules never see the bundle: it is added only to commonMain
 * of modules applying THIS plugin (`jellyplay.kmp.library.base` alone —
 * core:model/data/network/database/datastore/concurrency/player-contract/
 * test-fixtures — keeps its own hand-picked edges).
 *
 * The former per-module `compose.resources { packageOfResClass = ... }`
 * tail is centralized here as the [JellyPlayExtension.resPackage] default
 * (derived from the module path — see the property's KDoc). The generated
 * package strings are unchanged for every module; `player-book` overrides
 * via `jellyplay { resPackage = ... }`. `publicResClass` stays per-module
 * (only core:ui flips it, for cross-module string sharing).
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

        val jelly = project.extensions.create("jellyplay", JellyPlayExtension::class.java)
        jelly.resPackage.convention(defaultResPackage(project.path))

        // Universal commonMain bundle. Version-catalog type-safe accessors
        // (libs.*) do not generate inside convention-plugin code, so the
        // aliases resolve through the VersionCatalogsExtension — same pins
        // the per-module build files used before the centralization.
        val libs = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            sourceSets.getByName("commonMain").dependencies {
                // api — core:designsystem exposed these to every consumer
                // before the centralization; implementation would narrow its
                // published API (every other module implementation-scoped
                // them, and widening those is compile-safe).
                api(libs.findLibrary("jb-compose-runtime").get())
                api(libs.findLibrary("jb-compose-ui").get())
                api(libs.findLibrary("jb-compose-foundation").get())
                api(libs.findLibrary("jb-compose-animation").get())
                api(libs.findLibrary("jb-compose-material3").get())

                // Compose-resources runtime (stringResource/StringResource
                // API). No catalog alias existed for the DSL notation
                // compose.components.resources — added as
                // jb-compose-components-resources on the same train.
                implementation(libs.findLibrary("jb-compose-components-resources").get())

                // Icon packs (tabler, composables' CMP port).
                implementation(libs.findLibrary("tabler-icons-outline").get())
                implementation(libs.findLibrary("tabler-icons-filled").get())

                // Nav3 ships KMP variants from google maven directly — no
                // mirror. (The legacy lifecycle-viewmodel-navigation3 edge
                // was dropped module-by-module; navigation entries use
                // entry<Route> from the runtime/ui artifacts only.)
                implementation(libs.findLibrary("navigation3-runtime").get())
                implementation(libs.findLibrary("navigation3-ui").get())

                // ViewModel base + collectAsStateWithLifecycle/
                // LocalLifecycleOwner in the screens.
                implementation(libs.findLibrary("lifecycle-viewmodel").get())
                implementation(libs.findLibrary("lifecycle-runtime-compose").get())

                // Koin owns the ViewModels (V3 feature conveyor: one
                // framework per type — koinViewModel() in the nav entries).
                implementation(libs.findLibrary("koin-core").get())
                implementation(libs.findLibrary("koin-compose").get())
                implementation(libs.findLibrary("koin-compose-viewmodel").get())
            }
        }

        // packageOfResClass is a plain var on the compose plugin's nested
        // `resources` extension (no Property, no convention/override
        // semantics), so the default must be applied AFTER the build script
        // had its chance to set an override — hence afterEvaluate, not
        // immediately. The `jellyplay` Property carries the
        // convention-or-override value with normal Gradle semantics.
        project.afterEvaluate {
            val composeResources = (project.extensions.getByType(ComposeExtension::class.java) as ExtensionAware)
                .extensions.getByName("resources") as ResourcesExtension
            composeResources.packageOfResClass = jelly.resPackage.get()
        }
    }

    /**
     * `:shared:feature:home` → `com.raulshma.jellyplay.feature.home.generated.resources`.
     * The leading `shared` segment is dropped; hyphens become dots (package
     * segments cannot carry them): `subtitle-tester` → `subtitle.tester`.
     */
    private fun defaultResPackage(projectPath: String): String {
        val segments = projectPath.split(':').filter { it.isNotEmpty() }.toMutableList()
        if (segments.isNotEmpty() && segments.first() == "shared") {
            segments.removeAt(0)
        }
        return "com.raulshma.jellyplay." + segments.joinToString(".") { it.replace('-', '.') } + ".generated.resources"
    }
}
