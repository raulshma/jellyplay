plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.raulshma.jellyplay.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.jellyfin.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    
    // SLF4J - required by Jellyfin SDK
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
}
