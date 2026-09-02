package dev.juliamorozova.ragagent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RagAgentApplication : Application() {

    @Inject
    lateinit var knowledgeBaseSeeder: KnowledgeBaseSeeder

    // SupervisorJob: a failure in seeding shouldn't be able to cancel any other
    // work later launched on this scope. Dispatchers.IO: asset reads + network calls.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            knowledgeBaseSeeder.seedIfEmpty()
        }
    }
}
