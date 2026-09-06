package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.RagAnswer
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import kotlinx.coroutines.CompletableDeferred

/**
 * Suspends on [answer] until a test completes it, so a test can observe the ViewModel's
 * transient loading state between calling [invoke] and it returning. [shouldFail] throws
 * immediately instead, without ever suspending on [answer].
 */
class FakeGenerateAnswerUseCase(
    var shouldFail: Boolean = false,
) : GenerateAnswerUseCase {

    val answer = CompletableDeferred<RagAnswer>()

    var lastQuery: String? = null
        private set

    override suspend fun invoke(query: String, onNarration: (String) -> Unit): RagAnswer {
        lastQuery = query
        if (shouldFail) error("Forced failure")
        return answer.await()
    }
}
