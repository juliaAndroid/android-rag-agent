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

/** Qualifier for the Claude [OkHttpClient]; two unqualified OkHttpClient bindings would collide in Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeHttpClient

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
            // BASIC: BODY would print questions, note passages and answers to logcat in every build.
            level = HttpLoggingInterceptor.Level.BASIC
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
