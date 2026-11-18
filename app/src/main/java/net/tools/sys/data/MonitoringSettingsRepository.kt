package net.tools.sys.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.tools.sys.monitoring.AvailabilityRecord
import net.tools.sys.monitoring.MonitoringIntervals

private val Context.monitoringDataStore: DataStore<Preferences> by preferencesDataStore(name = "monitoring")

data class MonitoringSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = MonitoringIntervals.DEFAULT_MINUTES,
    val selectedIds: Set<String> = emptySet(),
)

class MonitoringSettingsRepository(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("monitoring_enabled")
    private val intervalKey = intPreferencesKey("monitoring_interval_minutes")
    private val selectedKey = stringSetPreferencesKey("monitoring_selected_ids")
    private val recordsKey = stringPreferencesKey("monitoring_status_records")

    private val json = Json { ignoreUnknownKeys = true }
    private val recordSerializer = MapSerializer(String.serializer(), AvailabilityRecord.serializer())

    val settings: Flow<MonitoringSettings> = context.monitoringDataStore.data.map { prefs ->
        MonitoringSettings(
            enabled = prefs[enabledKey] ?: false,
            intervalMinutes = MonitoringIntervals.normalize(
                prefs[intervalKey] ?: MonitoringIntervals.DEFAULT_MINUTES,
            ),
            selectedIds = prefs[selectedKey] ?: emptySet(),
        )
    }

    suspend fun currentSettings(): MonitoringSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.monitoringDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
        }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        val normalized = MonitoringIntervals.normalize(minutes)
        context.monitoringDataStore.edit { prefs ->
            prefs[intervalKey] = normalized
        }
    }

    suspend fun setSelectedIds(ids: Set<String>) {
        context.monitoringDataStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(selectedKey) else prefs[selectedKey] = ids
        }
    }

    suspend fun peekLegacyStatusRecords(): Map<String, AvailabilityRecord> {
        val raw = context.monitoringDataStore.data.first()[recordsKey]
        return decodeRecords(raw)
    }

    suspend fun clearLegacyStatusRecords() {
        context.monitoringDataStore.edit { prefs ->
            prefs.remove(recordsKey)
        }
    }

    private fun decodeRecords(raw: String?): Map<String, AvailabilityRecord> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(recordSerializer, raw) }.getOrDefault(emptyMap())
    }
}
