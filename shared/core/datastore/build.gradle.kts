@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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

    wasmJs {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: PinHasher
        // (java.security PBKDF2/SHA-256), UUID generation and runBlocking
        // first-value reads. The DI module/qualifiers moved to commonMain in
        // §Phase W (web shell needs them); jvmShared keeps only these
        // platform actuals.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            // Multiplatform core: Preferences/Key/edit + PreferenceDataStoreFactory
            // (the Android-only Context delegate stays in the legacy shim's DI).
            api(libs.datastore.preferences.core)
            api(libs.okio)
            // Koin module/qualifier types are public commonMain API since
            // §Phase W (the web shell binds its DataStores through them);
            // koin-core publishes android/jvm/wasmJs.
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
        getByName("wasmJsMain").dependencies {
            // DOM access for the localStorage-backed DataStore storage of
            // webDatastoreModule (§Phase W spike: datastore 1.2.1 ships a
            // public Storage/StorageConnection API on wasmJs).
            implementation(libs.kotlinx.browser)
            // RUNTIME FIX (coordinator web browser pass): datastore 1.2.1's
            // wasmJs actual of DataStoreFactory.create / PreferenceDataStore-
            // Factory.create is a literal TODO("Not yet implemented") — every
            // Koin single in webDatastoreModule threw at first resolution, so
            // the web shell rendered a blank canvas (klib-string-proven; the
            // W.0 spike verified exports, never execution). 1.3.0-alpha10
            // (Kotlin 2.3.20 / ABI 2.3.0 — compatible with the repo's 2.3.21)
            // implements the factory over DataStore.Builder. Target-scoped
            // override: only the wasm variant resolves upward; android/jvm
            // keep the repo-wide 1.2.1 pin.
            implementation("androidx.datastore:datastore-preferences-core:1.3.0-alpha10")
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
