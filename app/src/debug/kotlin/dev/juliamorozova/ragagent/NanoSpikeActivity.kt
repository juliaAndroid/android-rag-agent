package dev.juliamorozova.ragagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import dev.juliamorozova.ragagent.presentation.theme.RagAgentTheme

/** Debug-only screen for trying Gemini Nano. Open: long-press the app icon. */
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
