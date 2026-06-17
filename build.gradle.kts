plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kover)
}

subprojects {
    if (name != "website") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}

dependencies {
    subprojects.forEach {
        if (it.name != "website" && it.name != "baselineprofile") {
            kover(it)
        }
    }
}
