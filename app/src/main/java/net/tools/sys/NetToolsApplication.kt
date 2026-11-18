package net.tools.sys

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.tools.sys.data.MonitoringSettingsRepository
import net.tools.sys.work.MonitoringScheduler

class NetToolsApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching {
                val settings = MonitoringSettingsRepository(this@NetToolsApplication).currentSettings()
                MonitoringScheduler.sync(
                    context = this@NetToolsApplication,
                    enabled = settings.enabled,
                    intervalMinutes = settings.intervalMinutes,
                )
                MonitoringScheduler.ensureHistoryCleanup(this@NetToolsApplication)
            }
        }
    }
}
