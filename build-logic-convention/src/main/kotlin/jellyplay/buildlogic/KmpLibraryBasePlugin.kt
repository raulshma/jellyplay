package jellyplay.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base convention plugin for every `shared/` KMP library module. Replaces the
 * skeleton that was copy-pasted across all ~34 module build files:
 *
 *  - applies `org.jetbrains.kotlin.multiplatform` + AGP 9's dedicated
 *    `com.android.kotlin.multiplatform.library` (AGP 9 forbids combining the
 *    KMP plugin with `com.android.library`, so every KMP module's Android
 *    target uses this plugin; the Android target is configured through the
 *    [KotlinMultiplatformAndroidLibraryTarget] the plugin registers under the
 *    kotlin extension's target name "android", not a top-level `android {}`
 *    block).
 *  - pins compileSdk/minSdk and the explicit JVM 17 compiler target on both
 *    JVM-shaped targets (the root build centralizes the same pin for the AGP
 *    application modules; the KMP modules need their own because their plugin
 *    id differs).
 *  - applies the default hierarchy template and registers the `jvmShared`
 *    middle source set wired commonMain <- jvmShared <- {androidMain, jvmMain}
 *    — the JVM-semantics middle layer every module shares code through.
 *    Modules that carry no jvmShared sources simply leave the directory
 *    empty; the wiring is inert there.
 *  - adds kotlin("test") to commonTest and jvmTest.
 *
 * NOT centralized here (stays per module): the namespace, the per-module
 * compose-resources `packageOfResClass` (a per-module legacy package string —
 * see the tail comment in the compose modules), `withHostTest`/`withDeviceTest`
 * lanes and the Robolectric dependency wiring, the Room KSP setup, and every
 * module's actual dependency list. Modules needing Compose + compose-resources
 * apply `jellyplay.kmp.library.compose` instead, which applies this plugin
 * first.
 */
class KmpLibraryBasePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val pluginManager = project.pluginManager
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            // Explicit Kotlin JVM target, centralized. With AGP 9 built-in
            // Kotlin no module applies the Kotlin (Android) plugin, and the
            // compiler's jvmTarget silently follows each module's
            // compileOptions.targetCompatibility; pinning it explicitly
            // (matching the Java 17 every module already declares) makes the
            // target explicit. Same pin the root build applies to the AGP
            // application modules via com.android.{application,library,test}.
            jvm {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            applyDefaultHierarchyTemplate()

            // JVM-semantics middle source set shared verbatim by android +
            // desktop (TtlCache, OkHttp/Jellyfin implementations, java.time
            // actuals, ...). Registered through the KotlinSourceSetContainer
            // API directly — type-safe accessors do not generate for source
            // set manipulation inside convention-plugin code, so the wiring
            // is deliberately written against the plugin API types.
            sourceSets.maybeCreate("jvmShared").apply {
                dependsOn(sourceSets.getByName("commonMain"))
            }
            sourceSets.getByName("androidMain").dependsOn(sourceSets.getByName("jvmShared"))
            sourceSets.getByName("jvmMain").dependsOn(sourceSets.getByName("jvmShared"))

            sourceSets.getByName("commonTest").dependencies {
                implementation(kotlin("test"))
            }
            sourceSets.getByName("jvmTest").dependencies {
                implementation(kotlin("test"))
            }
        }

        // The AGP-9 KMP library plugin creates the Android target eagerly
        // under the name "android"; its public DSL type carries the library
        // configuration (namespace stays per-module, set by the build file).
        val androidTarget = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .getByName("android") as KotlinMultiplatformAndroidLibraryTarget

        androidTarget.apply {
            compileSdk = 37
            minSdk = 28

            // Compose-resources packaging (device-pass finding): with the
            // AGP-9 KMP library plugin, android resources are OFF by default, so
            // copyAndroidMainComposeResourcesToAndroidAssets never runs and the
            // app APK ships this module's Res accessors with NO backing .cvr
            // assets — runtime MissingResourceException on the first string
            // read. Load-bearing crash fix: leave enabled. Modules without any
            // (compose) resources get empty no-op packaging tasks.
            androidResources {
                enable = true
            }

            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
