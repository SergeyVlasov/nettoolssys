package net.tools.sys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.tools.sys.ui.ServerMonitorScreen
import net.tools.sys.ui.theme.NettoolssysTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NettoolssysTheme {
                ServerMonitorScreen()
            }
        }
    }
}