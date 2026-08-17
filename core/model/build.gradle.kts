plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.raulshma.jellyplay.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        // Annotation-only Compose usage (@Immutable/@Stable on data classes):
        // compose.runtime suffices, no compiler plugin needed (pattern documented
        // in feature/player/core/build.gradle.kts).
        buildConfig = false
        resValues = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
