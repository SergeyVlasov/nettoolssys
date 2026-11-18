package net.tools.sys.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.tools.sys.model.PortTarget
import net.tools.sys.model.ServerEntry
import net.tools.sys.model.ServerListExport

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "servers")

class ServerRepository(private val context: Context) {

    private val serversKey = stringPreferencesKey("server_list_json")

    private val json = Json { ignoreUnknownKeys = true }

    private val exportJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val servers: Flow<List<ServerEntry>> = context.serverDataStore.data.map { prefs ->
        val raw = prefs[serversKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<ServerEntry>>(raw) }
            .getOrDefault(emptyList())
            .map { it.forPersistence() }
    }

    suspend fun saveServers(servers: List<ServerEntry>) {
        context.serverDataStore.edit { prefs ->
            prefs[serversKey] = json.encodeToString(ListSerializer(ServerEntry.serializer()), servers.map { it.forPersistence() })
        }
    }

    suspend fun addServer(entry: ServerEntry) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[serversKey]?.let {
                runCatching { json.decodeFromString<List<ServerEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[serversKey] = json.encodeToString(ListSerializer(ServerEntry.serializer()), current + entry.forPersistence())
        }
    }

    suspend fun removeServer(id: String) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[serversKey]?.let {
                runCatching { json.decodeFromString<List<ServerEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[serversKey] = json.encodeToString(ListSerializer(ServerEntry.serializer()), current.filterNot { it.id == id })
        }
    }

    suspend fun updateServer(
        id: String,
        name: String,
        host: String,
        portTargets: List<PortTarget>,
    ) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[serversKey]?.let {
                runCatching { json.decodeFromString<List<ServerEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map { entry ->
                if (entry.id == id) {
                    entry.copy(
                        name = name.trim(),
                        host = host.trim(),
                        portTargets = portTargets,
                    ).forPersistence()
                } else {
                    entry.forPersistence()
                }
            }
            prefs[serversKey] = json.encodeToString(ListSerializer(ServerEntry.serializer()), updated)
        }
    }

    suspend fun currentServers(): List<ServerEntry> = servers.first()

    suspend fun replaceAllServers(servers: List<ServerEntry>) {
        saveServers(servers.map { it.forPersistence() })
    }

    suspend fun exportToJson(): String {
        val list = currentServers().map { it.forPersistence() }
        return exportJson.encodeToString(ServerListExport.serializer(), ServerListExport(servers = list))
    }

    fun parseImportJson(raw: String): Result<List<ServerEntry>> {
        return runCatching {
            val parsed = exportJson.decodeFromString<ServerListExport>(raw)
            parsed.servers.map { it.forPersistence() }
        }.recoverCatching {
            exportJson.decodeFromString<List<ServerEntry>>(raw).map { it.forPersistence() }
        }
    }
}