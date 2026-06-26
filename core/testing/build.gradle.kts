plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.raulshma.jellyplay.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.coroutines.test)
    implementation(libs.junit)
}
