package dev.juliamorozova.ragagent.rag.agent.nano

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until the future completes (ML Kit returns Guava futures); cancelling the coroutine cancels the future.
 */
internal suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
        },
        Runnable::run,
    )
    continuation.invokeOnCancellation { cancel(false) }
}
