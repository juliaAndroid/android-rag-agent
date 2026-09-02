package dev.juliamorozova.ragagent.rag.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.juliamorozova.ragagent.rag.BuildConfig
import dev.juliamorozova.ragagent.rag.embedding.VoyageEmbeddingsApi
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
 * Tags the [OkHttpClient] built for Voyage specifically (auth header baked in), so it
 * doesn't collide with [ClaudeHttpClient]'s [OkHttpClient] in the same component —
 * Hilt treats two unqualified `@Provides fun ...(): OkHttpClient` as a duplicate binding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VoyageHttpClient

/** Wires the Voyage AI embeddings client: auth header, JSON (de)serialization, Retrofit. */
@Module
@InstallIn(SingletonComponent::class)
object VoyageNetworkModule {

    // Native voyageai.com key/endpoint — NOT the same as a MongoDB Atlas-issued Model
    // API key, which authenticates against a different host (ai.mongodb.com) entirely.
    // Mixing the two (Atlas key against this URL, or vice versa) fails with 403.
    private const val VOYAGE_BASE_URL = "https://api.voyageai.com/"

    // Voyage uses standard bearer auth — NOT the "x-api-key" scheme Claude uses. Two
    // different providers, two different auth conventions; don't assume they match.
    private const val AUTHORIZATION_HEADER = "Authorization"

    @Provides
    @Singleton
    @VoyageHttpClient
    fun provideVoyageOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val authedRequest = chain.request().newBuilder()
                .addHeader(AUTHORIZATION_HEADER, "Bearer ${BuildConfig.VOYAGE_API_KEY}")
                .build()
            chain.proceed(authedRequest)
        }
        val logging = HttpLoggingInterceptor().apply {
            // BASIC by default — BODY would print the user's actual note content (the
            // text being embedded), not just a key. Flip to BODY only when debugging,
            // same as ClaudeNetworkModule; the Authorization header stays redacted either way.
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader(AUTHORIZATION_HEADER)
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideVoyageEmbeddingsApi(@VoyageHttpClient client: OkHttpClient, json: Json): VoyageEmbeddingsApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(VOYAGE_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(VoyageEmbeddingsApi::class.java)
    }
}
