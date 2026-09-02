package dev.juliamorozova.ragagent.rag.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.juliamorozova.ragagent.rag.BuildConfig
import dev.juliamorozova.ragagent.rag.agent.claude.ClaudeApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Tags the [OkHttpClient] built for Claude specifically (auth header baked in), so it
 * doesn't collide with `DataModule`'s plain [OkHttpClient] for the Voyage embeddings API —
 * Hilt treats two unqualified `@Provides fun ...(): OkHttpClient` in the same component as
 * a duplicate binding and fails the build.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeHttpClient

/** Wires the Claude Messages API client: auth header, JSON (de)serialization, Retrofit. */
@Module
@InstallIn(SingletonComponent::class)
object ClaudeNetworkModule {

    private const val CLAUDE_BASE_URL = "https://api.anthropic.com/"

    // https://docs.claude.com/en/api/versioning — pinned
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val API_KEY_HEADER = "x-api-key"

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
        val authInterceptor = Interceptor { chain ->
            val authedRequest = chain.request().newBuilder()
                .addHeader(API_KEY_HEADER, BuildConfig.CLAUDE_API_KEY)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .build()
            chain.proceed(authedRequest)
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader(API_KEY_HEADER)
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideClaudeApi(@ClaudeHttpClient client: OkHttpClient, json: Json): ClaudeApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(CLAUDE_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ClaudeApi::class.java)
    }
}
