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

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Wave 15B: the requests slice's data layer compiles for the web shell.
    // Room stayed behind: :shared:core:database has no wasm build, so the
    // Room-backed repositories (QueuePersistenceHelper, SeenMedia*,
    // ItemPlaybackPreference*, PlaylistRepositories, OfflineSyncProjection,
    // ScanWorkerHelper) plus the other JVM-touching files moved to jvmShared,
    // and the database edge demoted from commonMain api() to jvmShared
    // implementation() (core:network precedent: common seams + jvmShared
    // impls). The browser lane is compile-only like core:network's — no wasm
    // test task (jvmTest pins the semantics).
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: since wave
        // 15B this is where Room touches live (the module's ONLY Room-coupled
        // code — see the wasmJs note above), plus the java.io/java.time files
        // and the file-system-touching repositories that have no wasm story.
        // commonMain holds the common-safe seams + the promoted Seerr/Arr
        // repositories (kotlinx-datetime instead of java.time).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:network"))
            // Room is consumed ONLY from jvmShared now (database has no wasm
            // build; demoted from api() in wave 15B).
            api(project(":shared:core:datastore"))
            // ArrRepository(Impl)'s calendar windows — kotlinx-datetime 0.8.0
            // (ABI evidence in the catalog note: Kotlin 2.1.20-built klibs,
            // safe under the repo's 2.3.21 pin).
            implementation(libs.kotlinx.datetime)
            // Phase W.3: PlayerLifecycleCallbacks (implemented by
            // PlayerLifecycleManager) + RemotePlayableEngine (used by
            // ActivePlayerController consumers / VideoMiniPlayerState) moved to
            // player-contract commonMain in the SAME packages, so every
            // reference — in this module and through the legacy :core:data
            // api() re-export — resolves unchanged. api() because the
            // interfaces appear in this module's public surface (e.g.
            // VideoMiniPlayerState.engine). No cycle: player-contract's only
            // project dep is core:model.
            api(project(":shared:core:player-contract"))
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
            // Room DAOs/entities: confined to jvmShared since wave 15B (the
            // moved Room-backed repositories above) — never visible to wasmJs.
            api(project(":shared:core:database"))
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
            // ApkInstallBuilderImpl (FileProvider) + the AndroidDataModule
            // version probe (PackageInfoCompat) — AppUpdate split (Wave xB).
            implementation(libs.androidx.core.ktx)
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
