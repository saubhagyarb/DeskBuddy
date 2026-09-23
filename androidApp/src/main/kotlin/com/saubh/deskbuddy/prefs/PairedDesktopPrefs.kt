package com.saubh.deskbuddy.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saubh.deskbuddy.protocol.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "deskbuddy_prefs")

data class SavedDesktop(val name: String, val host: String, val port: Int, val token: String)

class PairedDesktopPrefs(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("desktop_name")
        val HOST = stringPreferencesKey("desktop_host")
        val PORT = intPreferencesKey("desktop_port")
        val TOKEN = stringPreferencesKey("desktop_token")
    }

    val saved: Flow<SavedDesktop?> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.NAME] ?: return@map null
        val host = prefs[Keys.HOST] ?: return@map null
        val token = prefs[Keys.TOKEN] ?: return@map null
        SavedDesktop(name, host, prefs[Keys.PORT] ?: Protocol.PORT, token)
    }

    suspend fun save(desktop: SavedDesktop) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NAME] = desktop.name
            prefs[Keys.HOST] = desktop.host
            prefs[Keys.PORT] = desktop.port
            prefs[Keys.TOKEN] = desktop.token
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
