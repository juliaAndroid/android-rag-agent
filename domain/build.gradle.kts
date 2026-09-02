plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin, no Android/Compose/Room dependency on purpose: this module is the
// business-rule core of Clean Architecture and must be testable on the plain JVM
// (and, if the KMM stretch goal happens, shareable with iOS as-is).

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
}
