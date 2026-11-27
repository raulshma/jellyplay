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
     * `com.raulshma.jellyplay.feature.home.generated.resources` — kept
     * stable so shared sources need no import churn.
     *
     * Override for modules whose package breaks the derivation — today only
     * `feature:player-book`, whose Res package is `...feature.book...`
     * (derivation would insert a `player` segment the generated accessors
     * do not use).
     */
    val resPackage: Property<String>
}

/**
 * Convention plugin for `shared/` modules that use Compose: applies
 * [KmpLibraryBasePlugin] plus the Compose compiler plugin and the JetBrains
 * Compose Multiplatform distribution plugin (the only publisher of real JVM
 * compose binaries — see the catalog note on `composeMultiplatform`).
 *
 * Also owns the UNIVERSAL commonMain dependency bundle — the subset every
 * Compose module needs. This deliberately reverses the "every module owns
 * its dependency list" stance recorded in the base plugin's KDoc for
 * exactly this subset; everything outside it (project edges,
 * coil/datetime/serialization/paging/saveable/activity/platform-lane deps,
 * test lanes, ...) stays declared per module.
 *
 * The bundle — `api` vs `implementation` mirrors what modules declare
 * (core:designsystem api-exports the five JB compose artifacts so every
 * feature façade compiles against Compose; nothing else is api-scoped, so
 * keeping them implementation preserves each module's exposed API
 * byte-for-byte):
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
 * The `compose.resources { packageOfResClass = ... }` tail is centralized
 * here as the [JellyPlayExtension.resPackage] default (derived from the
 * module path — see the property's KDoc); the default reproduces each
 * module's declared package. `player-book` overrides via
 * `jellyplay { resPackage = ... }`. `publicResClass` stays per-module
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
        // aliases resolve through the VersionCatalogsExtension.
        val libs = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            sourceSets.getByName("commonMain").dependencies {
                // api — core:designsystem exposes these to every consumer;
                // implementation would narrow its published API (every other
                // module implementation-scopes them, and widening those is
                // compile-safe).
                api(libs.findLibrary("jb-compose-runtime").get())
                api(libs.findLibrary("jb-compose-ui").get())
                api(libs.findLibrary("jb-compose-foundation").get())
                api(libs.findLibrary("jb-compose-animation").get())
                api(libs.findLibrary("jb-compose-material3").get())

                // Compose-resources runtime (stringResource/StringResource
                // API), aliased as jb-compose-components-resources on the
                // same train as the DSL notation compose.components.resources.
                implementation(libs.findLibrary("jb-compose-components-resources").get())

                // Icon packs (tabler, composables' CMP port).
                implementation(libs.findLibrary("tabler-icons-outline").get())
                implementation(libs.findLibrary("tabler-icons-filled").get())

                // Nav3 ships KMP variants from google maven directly — no
                // mirror. Navigation entries use entry<Route> from the
                // runtime/ui artifacts only.
                implementation(libs.findLibrary("navigation3-runtime").get())
                implementation(libs.findLibrary("navigation3-ui").get())

                // ViewModel base + collectAsStateWithLifecycle/
                // LocalLifecycleOwner in the screens.
                implementation(libs.findLibrary("lifecycle-viewmodel").get())
                implementation(libs.findLibrary("lifecycle-runtime-compose").get())

                // Koin owns the ViewModels (one framework per type —
                // koinViewModel() in the nav entries).
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

        installMaterial3AlignmentConstraint(project, libs)
    }

    /**
     * Drags the androidx material3 lineage in EVERY graph of this project
     * onto the catalog's `composeMaterial3` pin, so compile, runtime, and
     * test lanes all resolve the same version.
     *
     * WHY: shared modules reach material3 through the JB CMP distribution
     * (`org.jetbrains.compose.material3`), whose android redirect carries ITS
     * OWN androidx material3 version — currently 1.5.0-alpha22, older than
     * the androidx material3 `:app` declares (catalog pin, highest-wins) and
     * therefore what the APK actually ships. Gradle resolves per graph, so
     * module compile classpaths sat on the alpha train while the app shipped
     * beta: deprecated APIs removed in between compiled clean and crashed
     * with NoSuchMethodError at runtime (the user-detail tab-switch crash —
     * no-arg `Modifier.menuAnchor()` was exactly that API). The constraint
     * closes the drift: what compiles is what ships.
     *
     * Constraints never ADD a module to a graph — they only adjust the
     * version when the module is already resolved — so desktop resolutions
     * (the real JB material3 jar, no androidx module present) are untouched,
     * and graphs without compose never see it. Applied to every mutable
     * configuration via configureEach because KMP/AGP create the lane
     * configurations after plugin application (androidHostTest materializes
     * only in afterEvaluate).
     */
    private fun installMaterial3AlignmentConstraint(
        project: Project,
        libs: org.gradle.api.artifacts.VersionCatalog,
    ) {
        val notation = "androidx.compose.material3:material3:" +
            libs.findVersion("composeMaterial3").get().requiredVersion
        project.configurations.configureEach {
            // Gradle 9 role-based configurations: only declarable ones accept
            // constraints (buckets like <sourceSet>Implementation). The
            // resolvable classpath configurations extend these buckets, so
            // every graph that resolves androidx material3 inherits the
            // pin — compile, runtime, and both test lanes.
            if (!isCanBeDeclared) return@configureEach
            val constraint = project.dependencies.constraints.add(name, notation)
            constraint.because(
                "material3 compile/runtime alignment — shared modules must compile " +
                    "against the version the app ships (catalog composeMaterial3)"
            )
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
