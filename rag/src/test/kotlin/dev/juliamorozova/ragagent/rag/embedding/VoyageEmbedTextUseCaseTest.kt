package dev.juliamorozova.ragagent.rag.embedding

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * `delay` runs on the test scheduler's virtual clock, so the 20 s spacing and the backoff cost no real time
 * and can be measured through [currentTime].
 */
class VoyageEmbedTextUseCaseTest {

    /** Plays back the given outcomes in order: a response is returned, a throwable is thrown. */
    private class ScriptedApi(vararg outcomes: Any) : VoyageEmbeddingsApi {
        private val script = ArrayDeque(outcomes.toList())
        val requests = mutableListOf<VoyageEmbeddingsRequest>()

        override suspend fun embed(request: VoyageEmbeddingsRequest): VoyageEmbeddingsResponse {
            requests += request
            return when (val next = script.removeFirst()) {
                is Throwable -> throw next
                else -> next as VoyageEmbeddingsResponse
            }
        }
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    private fun answer(vararg vectors: List<Float>) = VoyageEmbeddingsResponse(
        data = vectors.mapIndexed { index, values -> VoyageEmbeddingData(embedding = values, index = index) },
    )

    private fun TestScope.useCase(api: VoyageEmbeddingsApi) = VoyageEmbedTextUseCase(api)

    @Test
    fun `an empty list makes no request`() = runTest {
        val api = ScriptedApi()

        assertThat(useCase(api)(emptyList())).isEmpty()
        assertThat(api.requests).isEmpty()
    }

    @Test
    fun `sends the texts to voyage-4 and converts the vectors`() = runTest {
        val api = ScriptedApi(answer(listOf(1f, 2f), listOf(3f, 4f)))

        val result = useCase(api)(listOf("first", "second"))

        assertThat(api.requests.single().input).containsExactly("first", "second").inOrder()
        assertThat(api.requests.single().model).isEqualTo("voyage-4")
        assertThat(result.map { it.values.toList() }).containsExactly(listOf(1f, 2f), listOf(3f, 4f)).inOrder()
    }

    @Test
    fun `orders the vectors by the index in the response, not by arrival`() = runTest {
        val shuffled = VoyageEmbeddingsResponse(
            data = listOf(
                VoyageEmbeddingData(embedding = listOf(2f), index = 1),
                VoyageEmbeddingData(embedding = listOf(1f), index = 0),
            ),
        )

        val result = useCase(ScriptedApi(shuffled))(listOf("a", "b"))

        assertThat(result.map { it.values.single() }).containsExactly(1f, 2f).inOrder()
    }

    @Test
    fun `the first request is not delayed`() = runTest {
        useCase(ScriptedApi(answer(listOf(1f))))("a")

        assertThat(currentTime).isEqualTo(0L)
    }

    @Test
    fun `the second request waits for the rate-limit window`() = runTest {
        val useCase = useCase(ScriptedApi(answer(listOf(1f)), answer(listOf(1f))))

        useCase("a")
        useCase("b")

        assertThat(currentTime).isAtLeast(19_000L)
        assertThat(currentTime).isAtMost(20_000L)
    }

    @Test
    fun `a 429 is retried and the later answer is returned`() = runTest {
        val api = ScriptedApi(httpError(429), httpError(429), answer(listOf(7f)))

        val result = useCase(api)("a")

        assertThat(api.requests).hasSize(3)
        assertThat(result.values.toList()).containsExactly(7f)
    }

    @Test
    fun `gives up after five retries of a 429`() = runTest {
        val api = ScriptedApi(*Array(6) { httpError(429) })

        val error = runCatching { useCase(api)("a") }.exceptionOrNull()

        assertThat(error).isInstanceOf(HttpException::class.java)
        assertThat(api.requests).hasSize(6)
    }

    @Test
    fun `an error other than 429 is not retried`() = runTest {
        val api = ScriptedApi(httpError(500))

        val error = runCatching { useCase(api)("a") }.exceptionOrNull()

        assertThat((error as HttpException).code()).isEqualTo(500)
        assertThat(api.requests).hasSize(1)
    }

    @Test
    fun `a network failure is not retried`() = runTest {
        val api = ScriptedApi(IOException("offline"))

        val error = runCatching { useCase(api)("a") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(api.requests).hasSize(1)
    }
}
