package dev.juliamorozova.ragagent.rag.repository

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.rag.local.db.ChunkDao
import dev.juliamorozova.ragagent.rag.local.db.ChunkEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The cosine search runs in Kotlin on top of the DAO, so it is tested here with an in-memory DAO. */
class RoomVectorStoreRepositoryTest {

    private class InMemoryChunkDao : ChunkDao {
        val rows = mutableListOf<ChunkEntity>()

        override suspend fun insert(chunk: ChunkEntity) {
            rows.removeAll { it.id == chunk.id }
            rows += chunk
        }

        override suspend fun getAll(): List<ChunkEntity> = rows.toList()
        override suspend fun clear() = rows.clear()
        override suspend fun count(): Int = rows.size
    }

    private val repository = RoomVectorStoreRepository(InMemoryChunkDao())

    private suspend fun store(id: String, vararg values: Float) =
        repository.store(Chunk(id, "note.md", "text of $id", 0), EmbeddingVector(values))

    private fun query(vararg values: Float) = EmbeddingVector(values)

    @Test
    fun `returns the most similar chunk first`() = runTest {
        store("same-direction", 1f, 0f)
        store("close", 1f, 1f)
        store("orthogonal", 0f, 1f)

        val result = repository.findSimilar(query(1f, 0f), topK = 3)

        assertThat(result.map { it.chunk.id }).containsExactly("same-direction", "close", "orthogonal").inOrder()
    }

    @Test
    fun `scores are cosine similarity, so length does not matter`() = runTest {
        store("long", 10f, 0f)
        store("orthogonal", 0f, 3f)
        store("opposite", -2f, 0f)

        val scores = repository.findSimilar(query(1f, 0f), topK = 3).associate { it.chunk.id to it.score }

        assertThat(scores["long"]).isWithin(1e-6f).of(1f)
        assertThat(scores["orthogonal"]).isWithin(1e-6f).of(0f)
        assertThat(scores["opposite"]).isWithin(1e-6f).of(-1f)
    }

    @Test
    fun `returns at most topK chunks`() = runTest {
        store("a", 1f, 0f)
        store("b", 1f, 1f)
        store("c", 0f, 1f)

        assertThat(repository.findSimilar(query(1f, 0f), topK = 2)).hasSize(2)
    }

    @Test
    fun `an empty store gives an empty result`() = runTest {
        assertThat(repository.findSimilar(query(1f, 0f), topK = 5)).isEmpty()
    }

    @Test
    fun `a zero vector scores zero instead of dividing by zero`() = runTest {
        store("zero", 0f, 0f)

        val result = repository.findSimilar(query(1f, 0f), topK = 1)

        assertThat(result.single().score).isEqualTo(0f)
    }

    @Test
    fun `an embedding of another dimension is rejected`() = runTest {
        store("two-dimensional", 1f, 0f)

        val error = runCatching { repository.findSimilar(query(1f, 0f, 0f), topK = 1) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `storing a chunk with an existing id replaces it`() = runTest {
        store("a", 1f, 0f)
        store("a", 0f, 1f)

        assertThat(repository.count()).isEqualTo(1)
    }
}
