import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.requests"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Wave 15B: first shared/feature module with the web target — the
    // requests slice renders in the ComposeViewport web shell. The old
    // blocker (java.time in commonMain) is gone: those reads moved behind
    // the RequestTime.kt expect/actual seam (jvmShared = the verbatim
    // java.time bodies, wasmJsMain = strict-regex + integer math with
    // documented degrades). The karma/Chrome browser run stays off like
    // core:ui/core:network — jvmTest pins the semantics.
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // The java.time actuals for RequestTime.kt (JDK only — no deps).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // ExperimentalStore — the DIRECT_ARR_INTEGRATION flag gate behind
            // the *arr download-progress enrichment.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no requests file imports it —
            // the navigation entry uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // LocalViewModelStoreOwner/LocalLifecycleOwner in
            // ProvidePlatformLocalsFallback (wave 15B) — declared explicitly
            // rather than relying on the transitive koin-compose-viewmodel
            // edge; already a repo pin, no new version enters the graph.
            implementation(libs.lifecycle.viewmodel.compose)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            // Koin owns the requests ViewModel (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:requests
// so migrated files keep their `com.raulshma.jellyplay.feature.requests` imports;
// generated accessors land in `...feature.requests.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.requests.generated.resources"

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (spike w-10C S1/R2; the
// identical block lives in shared/core/ui).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
