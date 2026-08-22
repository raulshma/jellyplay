import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.data"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target: depends on :shared:core:database (Room), which has no
    // wasm build (plan §Phase W — web v1 keeps the server as source of truth).
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: every
        // repository impl, OkHttp-shaped cache body and file-system touch of
        // the old :core:data that migrates here (plan §Phase C4 part 2).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:network"))
            api(project(":shared:core:database"))
            api(project(":shared:core:datastore"))
            implementation(libs.kotlinx.serialization.json)
            // suspend/Flow surface of the repositories + polling managers.
            implementation(libs.kotlinx.coroutines.core)
            // Annotation-only Compose usage (@Immutable/@Stable on data-layer
            // models), same pattern as :shared:core:model and :datastore.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
            // Paging types in repository signatures (PagingData) — common
            // artifact only; Android consumers add their own runtime.
            implementation(libs.paging.common)
        }
        getByName("jvmShared").dependencies {
            // Module/qualifier types appear in the public di signatures
            // (Phase C4 Koin construction owner). Never visible to wasmJs.
            api(libs.koin.core)
            // @Inject/@Singleton/@Named stay on the impl classes; Dagger reads
            // them from binaries at the legacy shim's KSP processing.
            implementation(libs.javax.inject)
            // okio FileSystem injected into the file-touching repositories.
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            // BandwidthInterceptor (network's jvmShared) is a ctor param of
            // AdaptiveBitrateSelector here; the DLNA UPnP helpers talk OkHttp.
            // Same source-set-scoped pattern as :shared:core:network's
            // jvmShared → :shared:core:datastore dependency.
            implementation(project(":shared:core:network"))
            implementation(libs.okhttp)
            // androidx.collection.LruCache memoization in the moved repository
            // impls (AuthRepositoryImpl's folder-id cache, OfflineRepositoryImpl's
            // artwork + JSON-decode caches). Plain JVM artifact, safe on jvmShared.
            implementation(libs.androidx.collection)
        }
        getByName("androidMain").dependencies {
            // AndroidOfflineModeManager registers itself against
            // ProcessLifecycleOwner (C4 part 2 offline-mode seam).
            implementation(libs.lifecycle.process)
        }
        getByName("jvmMain").dependencies {
            // Real org.json for the desktop target (SyncPlayEventHandler in
            // jvmShared parses Jellyfin SyncPlay group-update payloads with
            // org.json, mirroring :shared:core:network's websocket note): the
            // android target resolves the same classes from android.jar, so
            // the artifact is declared here only and never enters the Android
            // AAR's consumer metadata.
            implementation(libs.org.json)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            // Koin module smoke tests (C4): load dataJvmModule +
            // desktopDataModule against the datastore/network/database modules.
            implementation(libs.koin.test)
            implementation(libs.mockk)
            implementation(libs.okhttp)
            implementation(libs.okhttp.mockwebserver)
            // In-memory Room DAOs for the de-Robolectric repository tests
            // (PlaybackOutboxRepositoryImplTest, AuthRepositoryImplTest) —
            // same pattern as :shared:core:database's jvmTest.
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
