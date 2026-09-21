plugins {
    id("jellyplay.kmp.library.base")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.data"
        // The moved notification drawables/strings (media-session icons,
        // playback-sync + download notifications) are real android res —
        // same packaging note as :shared:core:ui. (androidResources.enable
        // itself comes from the convention plugin; see its KDoc for the
        // MissingResourceException story.)
        // cutover: the legacy :core:data module's Robolectric suites
        // moved here (androidHostTest). Flags carried over from the legacy
        // module's testOptions verbatim.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop — this is
        // where the JVM-EDGE Room touches live (the promotion moved the DAO-backed
        // repository impls that are platform-neutral — SearchHistory /
        // ItemPlaybackPreference / SeenMedia / PlaybackOutbox /
        // Smart+MoodPlaylist / QueuePersistenceHelper / RoomTransactions — to
        // commonMain; what remains here is the java.io/java.time files, the
        // OkHttp transfer machinery and the repositories whose signatures leak
        // File/URI/stream edges). commonMain holds the common-safe seams + the
        // promoted Seerr/Arr repositories (kotlinx-datetime instead of
        // java.time) + the promoted DAO-backed impls. The jvmShared middle
        // source set itself comes from the convention plugin.

        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:network"))
            // Cancellation-safe suspend wrappers + TaskBundle — the module's
            // own concurrency seam, not something borrowed from core:network.
            api(project(":shared:core:concurrency"))
            // promotion: the Room-backed repository impls
            // (SearchHistory/ItemPlaybackPreference/SeenMedia/PlaybackOutbox/
            // Smart+MoodPlaylist, QueuePersistenceHelper, RoomTransactions)
            // moved from jvmShared to commonMain — the Room edge is no
            // longer JVM-only. Promoted from the old jvmShared api() edge
            // (kept there too — dataJvmModule still wires the DAO consumers
            // that remain JVM).
            api(project(":shared:core:database"))
            // promotion: the promoted impls' Koin definitions live in
            // dataJvmModule (jvmShared) — they need the Koin DSL, and
            // koin-core 4.2.2 is multiplatform.
            api(libs.koin.core)
            // Room is consumed from BOTH source sets now: the promoted
            // commonMain impls use the commonMain API edge above, while the
            // JVM-only stragglers (DownloadRepositoryImpl's OkHttp transfer
            // machinery, OfflineSyncManager, AuthRepositoryImpl, ...) keep
            // using the jvmShared edge below.
            api(project(":shared:core:datastore"))
            // ArrRepository(Impl)'s calendar windows — kotlinx-datetime 0.8.0
            // (ABI evidence in the catalog note: Kotlin 2.1.20-built klibs,
            // safe under the repo's 2.3.21 pin).
            implementation(libs.kotlinx.datetime)
            // PlayerLifecycleCallbacks (implemented by
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
            // (Koin construction owner).
            api(libs.koin.core)
            // Room DAOs/entities: confined to jvmShared (the
            // moved Room-backed repositories above).
            api(project(":shared:core:database"))
            // (No javax.inject dependency: the @Inject/@Singleton decorations
            // were stripped from these impls when Koin took construction
            // ownership, and no Dagger/KSP processing exists anywhere in the
            // repo — jvmShared is compiled by Kotlin only.)
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
            // JellyPlayImageLoader (jvmShared image/): the shared Coil
            // builder policy both JVM shells construct their loader through.
            // Same coil pin the shells already use (api's coil-core arrives
            // via this artifact; the Android app's 3.5.0 bom keeps winning
            // version resolution there, as it already does for androidMain's
            // coil edge above).
            implementation(libs.coil.network.okhttp)
        }
        getByName("androidMain").dependencies {
            // AndroidOfflineModeManager registers itself against
            // ProcessLifecycleOwner (C4 part 2 offline-mode seam).
            implementation(libs.lifecycle.process)
            // ApkInstallBuilderImpl (FileProvider) + the AndroidDataModule
            // version probe (PackageInfoCompat) — AppUpdate split.
            implementation(libs.androidx.core.ktx)
            // ── cutover: the legacy :core:data module's Android
            // halves moved here wholesale (same packages) — cast stack,
            // media3 audio stack, WorkManager workers, remote controls,
            // shortcuts, TV watch-next, download seams. Dependencies are
            // the legacy module's implementation set carried over.
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.paging.runtime)
            implementation(libs.work.runtime.ktx)
            // (room-ktx dropped in the room3 spike: no room3-ktx artifact
            // exists — coroutine APIs live in room3-runtime, which arrives
            // transitively via the jvmShared api(:shared:core:database)
            // edge; no androidMain source imports room.)
            implementation(libs.media3.session)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.cast)
            implementation(libs.media3.datasource)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.media3.database)
            // FFmpeg software audio decoder for codecs MediaCodec lacks on
            // most devices (DTS, MLP/TrueHD, EAC3...). Auto-loaded by
            // DefaultRenderersFactory via reflection.
            implementation(libs.media3.ffmpeg.decoder)
            implementation(libs.play.services.cast.framework)
            implementation(libs.okhttp)
            implementation(libs.palette.ktx)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            implementation(libs.navigation3.runtime)
            // TV provider (Watch Next / preview channels) — R8 strips for
            // phone release.
            implementation(libs.tvprovider)
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
        // kotlin("test") + coroutines-test on commonTest cover both test lanes
        // through the commonTest → jvmTest hierarchy edge.
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
        getByName("jvmTest").dependencies {
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

// ── cutover: the legacy :core:data Robolectric suites moved here
// wholesale (70 files + the MediaSessionPlayerStubs helper + the
// MainDispatcherRule from the dissolved :core:testing module). AGP 9.4's
// withHostTest names the lane's source set androidHostTest and materializes
// it only in afterEvaluate, so the dependency wiring rides a configureEach.
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.work.testing)
            implementation(libs.androidx.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.okhttp.mockwebserver)
            // MediaSessionPlayerStubs builds a mockk<Player> against media3.
            implementation(libs.media3.session)
            implementation(libs.media3.exoplayer)
        }
    }
}

// Run each test class in a fresh worker JVM (carried over from the legacy
// module): the suite mixes Robolectric shadows, async receivers and
// coroutine scopes; a late in-flight coroutine from one class can leak an
// uncaught exception into the *next* class's TestScope on the same worker.
tasks.withType<Test>().matching { it.name.contains("AndroidHostTest") || it.name == "testAndroidHostTest" }.configureEach {
    forkEvery = 1
}
