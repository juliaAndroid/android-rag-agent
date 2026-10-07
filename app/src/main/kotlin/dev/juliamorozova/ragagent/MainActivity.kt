package dev.juliamorozova.ragagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dev.juliamorozova.ragagent.presentation.query.QueryScreen
import dev.juliamorozova.ragagent.presentation.theme.RagAgentTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RagAgentTheme {
                Surface {
                    QueryScreen()
                }
            }
        }
    }
}
