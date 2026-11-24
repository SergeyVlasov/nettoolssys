package net.tools.sys

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.tools.sys.ui.AppSettingsViewModel
import net.tools.sys.ui.ServerMonitorScreen
import net.tools.sys.ui.theme.NettoolssysTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings: AppSettingsViewModel = viewModel()
            val useDarkTheme by appSettings.useDarkTheme.collectAsStateWithLifecycle()
            NettoolssysTheme(darkTheme = useDarkTheme) {
                ServerMonitorScreen(appSettingsViewModel = appSettings)
            }
        }
    }
}