package dev.juliamorozova.ragagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import dev.juliamorozova.ragagent.presentation.theme.RagAgentTheme

/**
 * Debug-only entry point (its own launcher icon in debug builds) for the Gemini Nano experiments in
 * [NanoSpikeScreen]. Lives in the debug source set so it never ships in a release build.
 */
class NanoSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RagAgentTheme {
                Surface {
                    NanoSpikeScreen()
                }
            }
        }
    }
}
