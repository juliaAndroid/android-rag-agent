plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.gradle)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.juliamorozova.ragagent.rag"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        // Read from ~/.gradle/gradle.properties (global, outside any git repo) —
        // NOT from this project's local.properties. Falls back to "" so a clone
        // without the key set still compiles; calling the Claude API without it
        // will just fail at runtime with an auth error, not a build error.
        buildConfigField(
            "String",
            "CLAUDE_API_KEY",
            "\"${providers.gradleProperty("CLAUDE_API_KEY").getOrElse("")}\"",
        )
        buildConfigField(
            "String",
            "CLAUDE_MODEL",
            "\"${providers.gradleProperty("CLAUDE_MODEL").getOrElse("claude-haiku-4-5-20251001")}\"",
        )
        buildConfigField(
            "String",
            "VOYAGE_API_KEY",
            "\"${providers.gradleProperty("VOYAGE_API_KEY").getOrElse("")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
