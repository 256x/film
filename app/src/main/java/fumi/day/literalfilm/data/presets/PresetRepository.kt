package fumi.day.literalfilm.data.presets

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fumi.day.literalfilm.domain.FilmPresets
import fumi.day.literalfilm.domain.model.FilmPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.presetsDataStore by preferencesDataStore(name = "film_presets")

/** Single JSON blob in DataStore Preferences — a flat list of small objects with no query needs, no schema migration machinery required. */
class PresetRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val PRESETS = stringPreferencesKey("presets_json")
    }

    val presets: Flow<List<FilmPreset>> =
        appContext.presetsDataStore.data.map { prefs -> decode(prefs) }

    suspend fun ensureSeeded() {
        appContext.presetsDataStore.edit { prefs ->
            if (prefs[Keys.PRESETS] == null) prefs[Keys.PRESETS] = json.encodeToString(FilmPresets.SEED)
        }
    }

    suspend fun addPreset(preset: FilmPreset) {
        appContext.presetsDataStore.edit { prefs -> prefs[Keys.PRESETS] = json.encodeToString(decode(prefs) + preset) }
    }

    suspend fun deletePreset(id: String) {
        appContext.presetsDataStore.edit { prefs ->
            prefs[Keys.PRESETS] = json.encodeToString(decode(prefs).filterNot { it.id == id })
        }
    }

    private fun decode(prefs: Preferences): List<FilmPreset> =
        prefs[Keys.PRESETS]?.let { json.decodeFromString<List<FilmPreset>>(it) } ?: emptyList()
}
