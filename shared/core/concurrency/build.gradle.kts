plugins {
    id("jellyplay.kmp.library.base")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.concurrency"
    }

    sourceSets {
        commonMain.dependencies {
            // The module's whole subject: suspension, cancellation, structured
            // job choreography. No other dependency — this module must stay a
            // leaf so core:network, core:data and every feature module can
            // see it without a cycle.
            implementation(libs.kotlinx.coroutines.core)
        }
        // kotlin("test") on commonTest/jvmTest comes from the convention
        // plugin; coroutines-test declared once here covers both test lanes
        // through the commonTest → jvmTest hierarchy edge.
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
    }
}
