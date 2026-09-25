package com.saubh.deskbuddy.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saubh.deskbuddy.protocol.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "deskbuddy_prefs")

/** Every PC this phone has paired with, newest first. Pairing once per PC is enough. */
class PairedDesktopPrefs(private val context: Context) {

    private object Keys {
        val DESKTOPS = stringPreferencesKey("desktops_json")

        // Single-PC keys written by earlier versions; migrated on first read.
        val LEGACY_NAME = stringPreferencesKey("desktop_name")
        val LEGACY_HOST = stringPreferencesKey("desktop_host")
        val LEGACY_PORT = intPreferencesKey("desktop_port")
        val LEGACY_TOKEN = stringPreferencesKey("desktop_token")
    }

    val saved: Flow<List<SavedDesktop>> = context.dataStore.data.map(::readList)

    suspend fun save(desktop: SavedDesktop) = update { SavedDesktops.upsert(it, desktop) }

    suspend fun forget(desktop: SavedDesktop) = update { SavedDesktops.remove(it, desktop) }

    private suspend fun update(change: (List<SavedDesktop>) -> List<SavedDesktop>) {
        context.dataStore.edit { prefs ->
            val next = change(readList(prefs))
            prefs[Keys.DESKTOPS] = SavedDesktops.encode(next)
            prefs.remove(Keys.LEGACY_NAME)
            prefs.remove(Keys.LEGACY_HOST)
            prefs.remove(Keys.LEGACY_PORT)
            prefs.remove(Keys.LEGACY_TOKEN)
        }
    }

    private fun readList(prefs: Preferences): List<SavedDesktop> {
        val list = SavedDesktops.decode(prefs[Keys.DESKTOPS])
        val legacy = legacy(prefs) ?: return list
        return if (list.any { it.host == legacy.host }) list else SavedDesktops.upsert(list, legacy)
    }

    private fun legacy(prefs: Preferences): SavedDesktop? {
        val name = prefs[Keys.LEGACY_NAME] ?: return null
        val host = prefs[Keys.LEGACY_HOST] ?: return null
        val token = prefs[Keys.LEGACY_TOKEN] ?: return null
        return SavedDesktop(name, host, prefs[Keys.LEGACY_PORT] ?: Protocol.PORT, token)
    }
}
