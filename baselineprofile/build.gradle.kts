plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile.producer)
}

android {
    namespace = "com.raulshma.jellyplay.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("platform", "phone")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.art-profile"] = true
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui.test)
    implementation(libs.compose.ui.test.manifest)
}
