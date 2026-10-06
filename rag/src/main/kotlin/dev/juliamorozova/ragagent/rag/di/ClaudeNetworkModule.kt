package dev.juliamorozova.ragagent.rag.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.juliamorozova.ragagent.rag.BuildConfig
import dev.juliamorozova.ragagent.rag.agent.claude.ClaudeApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Tags the [OkHttpClient] built for Claude specifically, so it doesn't collide with
 * [VoyageHttpClient]'s [OkHttpClient] in the same component — Hilt treats two unqualified
 * `@Provides fun ...(): OkHttpClient` as a duplicate binding and fails the build.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeHttpClient

/**
 * Wires the Claude Messages API client: JSON (de)serialization, Retrofit. Requests go to the
 * serverless proxy ([BuildConfig.PROXY_BASE_URL]), which adds the API key and the
 * `anthropic-version` header server-side, so neither lives in the APK.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClaudeNetworkModule {

    @Provides
    @Singleton
    fun provideClaudeJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @ClaudeHttpClient
    fun provideClaudeOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideClaudeApi(@ClaudeHttpClient client: OkHttpClient, json: Json): ClaudeApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ClaudeApi::class.java)
    }
}
