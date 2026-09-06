package dev.juliamorozova.ragagent.rag.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChunkDaoTest {

    private lateinit var database: RagDatabase
    private lateinit var chunkDao: ChunkDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RagDatabase::class.java).build()
        chunkDao = database.chunkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadBackMatches() = runTest {
        val chunk = ChunkEntity(
            id = "chunk-1",
            documentId = "doc-1",
            text = "hello world",
            position = 0,
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            createdAtMillis = 1_000L,
        )

        chunkDao.insert(chunk)

        val all = chunkDao.getAll()
        assertThat(all).hasSize(1)
        assertThat(all.first()).isEqualTo(chunk)
    }

    @Test
    fun getAllReflectsInserts() = runTest {
        val chunks = (0 until 3).map { index ->
            ChunkEntity(
                id = "chunk-$index",
                documentId = "doc-1",
                text = "text $index",
                position = index,
                embedding = floatArrayOf(index.toFloat()),
                createdAtMillis = 1_000L + index,
            )
        }
        chunks.forEach { chunkDao.insert(it) }

        val all = chunkDao.getAll()
        assertThat(all).hasSize(3)
        assertThat(all.map { it.id }).containsExactlyElementsIn(chunks.map { it.id })
    }
}
