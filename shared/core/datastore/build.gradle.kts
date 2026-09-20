import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.datastore"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: PinHasher
        // (java.security PBKDF2/SHA-256), UUID generation and runBlocking
        // first-value reads. The DI module/qualifiers moved to commonMain;
        // jvmShared keeps only these platform actuals.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            // Cancellation-safe suspend wrappers — the module's flagged
            // suspend-fun sites ride the same seam as the rest of the tree.
            implementation(project(":shared:core:concurrency"))
            // Multiplatform core: Preferences/Key/edit + PreferenceDataStoreFactory
            // (the Android-only Context delegate stays in the legacy shim's DI).
            api(libs.datastore.preferences.core)
            api(libs.okio)
            // Koin module/qualifier types are public commonMain API;
            // koin-core publishes android/jvm.
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            // Annotation-only Compose usage (@Immutable/@Stable on preference
            // models), same pattern as :shared:core:model.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
        }
        getByName("androidMain").dependencies {
            // SecureKeyValueStorage android actual (EncryptedSharedPreferences).
            implementation(libs.security.crypto)
        }
        getByName("jvmMain").dependencies {
            // SecureKeyValueStorage desktop actual (OS keyring via JNA).
            implementation(libs.java.keyring)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}
