package dev.juliamorozova.ragagent.rag.retrieval

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultRetrieveRelevantChunksUseCaseTest {

    private class RecordingEmbed(private val vector: EmbeddingVector) : EmbedTextUseCase {
        val embedded = mutableListOf<String>()

        override suspend fun invoke(text: String): EmbeddingVector {
            embedded += text
            return vector
        }
    }

    private class RecordingStore(private val results: List<RetrievedChunk>) : VectorStoreRepository {
        var lastQueryEmbedding: EmbeddingVector? = null
        var lastTopK: Int? = null

        override suspend fun findSimilar(queryEmbedding: EmbeddingVector, topK: Int): List<RetrievedChunk> {
            lastQueryEmbedding = queryEmbedding
            lastTopK = topK
            return results
        }

        override suspend fun store(chunk: Chunk, embedding: EmbeddingVector) = error("not used")
        override suspend fun clear() = error("not used")
        override suspend fun count(): Int = error("not used")
        override suspend fun getAll(): List<Chunk> = error("not used")
    }

    private val queryVector = EmbeddingVector(floatArrayOf(1f, 0f))
    private val hit = RetrievedChunk(Chunk("c1", "note.md", "text", 0), 0.9f)

    @Test
    fun `embeds exactly the query and searches the store with that vector`() = runTest {
        val embed = RecordingEmbed(queryVector)
        val store = RecordingStore(listOf(hit))

        DefaultRetrieveRelevantChunksUseCase(embed, store)("clean architecture", topK = 3)

        assertThat(embed.embedded).containsExactly("clean architecture")
        assertThat(store.lastQueryEmbedding).isSameInstanceAs(queryVector)
    }

    @Test
    fun `passes topK to the store and returns its results unchanged`() = runTest {
        val store = RecordingStore(listOf(hit))

        val result = DefaultRetrieveRelevantChunksUseCase(RecordingEmbed(queryVector), store)("anything", topK = 7)

        assertThat(store.lastTopK).isEqualTo(7)
        assertThat(result).containsExactly(hit)
    }
}
