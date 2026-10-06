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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read from ~/.gradle/gradle.properties (global, outside any git repo) —
        // NOT from this project's local.properties — so the proxy URL never lands in
        // the public repo. The fallback is a syntactically valid but unreachable URL
        // (".invalid" is reserved and never resolves): a clone without the property still
        // builds and launches (Retrofit rejects an empty base URL), and calls just fail
        // with a network error. The Claude and Voyage API keys live in the proxy, not here.
        buildConfigField(
            "String",
            "PROXY_BASE_URL",
            "\"${providers.gradleProperty("PROXY_BASE_URL").getOrElse("https://proxy.invalid/")}\"",
        )
        buildConfigField(
            "String",
            "CLAUDE_MODEL",
            "\"${providers.gradleProperty("CLAUDE_MODEL").getOrElse("claude-haiku-4-5-20251001")}\"",
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

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
