plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.raulshma.jellyplay.core.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric tests resolve the module's merged string resources
            // (e.g. TranscodeReasonsTest); without this the lookups throw
            // Resources$NotFoundException under the stripped android.jar.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

// KMP migration shim (docs/kmp-migration-plan.md §Phase V1): everything portable
// lives in :shared:core:ui; this module keeps only the Android-coupled halves —
// biometric/WebView/LocaleApplier, KeyEvent D-pad input, the @StringRes Int label
// tables, UiText/UserMessageBus, ContextExt, LocalNetworkAccess, the Dpad slider
// pair — plus this module's strings.xml (legacy consumers still resolve
// com.raulshma.jellyplay.core.ui.R; dies at cutover §Phase X).
dependencies {
    api(project(":shared:core:ui"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))

    implementation(libs.javax.inject)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.tabler.icons.outline)
    implementation(libs.tabler.icons.filled)
    implementation(libs.biometric.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // RoutePredicatesTest round-trips NavKeys through nav3 serialization.
    testImplementation(libs.navigation3.runtime)
    testImplementation(libs.kotlinx.serialization.json)
    // Compose UI tests under Robolectric (focus-behavior regression tests).
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
