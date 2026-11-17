package net.tools.sys.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.tools.sys.model.ServerEntry

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "servers")

class ServerRepository(private val context: Context) {

    private val serversKey = stringPreferencesKey("server_list_json")

    private val json = Json { ignoreUnknownKeys = true }

    val servers: Flow<List<ServerEntry>> = context.serverDataStore.data.map { prefs ->
        val raw = prefs[serversKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<ServerEntry>>(raw) }
            .getOrDefault(emptyList())
            .map { it.forPersistence() }
    }

    suspend fun saveServers(servers: List<ServerEntry>) {
        context.serverDataStore.edit { prefs ->
            prefs[serversKey] = json.encodeToString(servers.map { it.forPersistence() })
        }
    }

    suspend fun addServer(entry: ServerEntry) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[serversKey]?.let {
                runCatching { json.decodeFromString<List<ServerEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[serversKey] = json.encodeToString(current + entry.forPersistence())
        }
    }

    suspend fun removeServer(id: String) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[serversKey]?.let {
                runCatching { json.decodeFromString<List<ServerEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[serversKey] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun updateServerName(id: String, name: String) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[serversKey]?.let {
                runCatching { json.decodeFromString<List<ServerEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[serversKey] = json.encodeToString(
                current.map { if (it.id == id) it.copy(name = name.trim()).forPersistence() else it.forPersistence() },
            )
        }
    }
}