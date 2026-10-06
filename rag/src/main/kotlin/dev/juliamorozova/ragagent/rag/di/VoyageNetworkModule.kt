package dev.juliamorozova.ragagent.rag.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.juliamorozova.ragagent.rag.BuildConfig
import dev.juliamorozova.ragagent.rag.embedding.VoyageEmbeddingsApi
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
 * Tags the [OkHttpClient] built for Voyage specifically, so it doesn't collide with
 * [ClaudeHttpClient]'s [OkHttpClient] in the same component — Hilt treats two unqualified
 * `@Provides fun ...(): OkHttpClient` as a duplicate binding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VoyageHttpClient

/**
 * Wires the Voyage AI embeddings client: JSON (de)serialization, Retrofit. Requests go to the
 * serverless proxy ([BuildConfig.PROXY_BASE_URL]), which adds the Voyage bearer token
 * server-side, so no key lives in the APK.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoyageNetworkModule {

    @Provides
    @Singleton
    @VoyageHttpClient
    fun provideVoyageOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC by default — BODY would print the user's actual note content (the
            // text being embedded). Flip to BODY only when debugging, same as
            // ClaudeNetworkModule.
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
    fun provideVoyageEmbeddingsApi(@VoyageHttpClient client: OkHttpClient, json: Json): VoyageEmbeddingsApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(VoyageEmbeddingsApi::class.java)
    }
}
