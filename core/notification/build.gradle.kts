plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.raulshma.jellyplay.core.notification"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
        resValues = false
    }
}

dependencies {
    implementation(project(":shared:core:model"))
    implementation(project(":core:data"))
    // core:database intentionally NOT declared — the notification module
    // consumes SeenMediaRepository from core:data and must not reach into
    // Room DAOs/entities directly (schema changes in core:database shouldn't
    // ripple into sibling modules). core:datastore still declared because
    // the scheduler/worker read UserPreferencesStore.notificationPreferences.
    implementation(project(":shared:core:datastore"))

    // Wave 8A: Hilt left this module — Koin owns the singletons
    // (androidNotificationModule) and NotificationWorkerFactory builds
    // NewMediaCheckWorker for WorkManager.
    implementation(libs.koin.core)

    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
}
