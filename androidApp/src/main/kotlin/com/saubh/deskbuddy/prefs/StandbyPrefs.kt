package com.saubh.deskbuddy.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saubh.deskbuddy.ui.standby.clock.ClockFace
import com.saubh.deskbuddy.ui.standby.clock.ClockFaceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.standbyStore by preferencesDataStore(name = "standby_prefs")

/** The clock face last swiped to. A read failure falls back to the default face rather than breaking standby. */
class StandbyPrefs(private val context: Context) : ClockFaceStore {
    private val faceKey = stringPreferencesKey("clock_face")

    override val face: Flow<ClockFace> = context.standbyStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { ClockFace.fromName(it[faceKey]) }

    override suspend fun save(face: ClockFace) {
        context.standbyStore.edit { it[faceKey] = face.name }
    }
}
