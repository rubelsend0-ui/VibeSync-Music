package com.vibesync.music.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibesync.music.data.model.AppSettings
import com.vibesync.music.data.model.ThemePref
import com.vibesync.music.util.BackendUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.vibeDataStore: DataStore<Preferences> by preferencesDataStore(name = "vibesync_settings")

interface SettingsSource {
    suspend fun current(): AppSettings
}

class SettingsRepository(private val store: DataStore<Preferences>) : SettingsSource {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val backend = stringPreferencesKey("backend_url")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val demo = booleanPreferencesKey("demo_mode")
        val quality = stringPreferencesKey("audio_quality")
        val playback = stringPreferencesKey("playback_state")
        val eq = stringPreferencesKey("eq_state")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.DARK,
            accentIndex = p[K.accent] ?: 0,
            backendUrl = p[K.backend] ?: "",
            wifiOnlyDownloads = p[K.wifiOnly] ?: true,
            demoMode = p[K.demo] ?: false,
            audioQuality = p[K.quality] ?: "auto",
        )
    }

    override suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(t: ThemePref) = store.edit { it[K.theme] = t.name }
    suspend fun setAccent(i: Int) = store.edit { it[K.accent] = i }
    suspend fun setWifiOnly(b: Boolean) = store.edit { it[K.wifiOnly] = b }
    suspend fun setDemoMode(b: Boolean) = store.edit { it[K.demo] = b }
    suspend fun setQuality(q: String) = store.edit { it[K.quality] = q }

    /** Validates before saving. Returns the stored (normalized) URL or a failure with a readable message. */
    suspend fun setBackendUrl(raw: String): Result<String> =
        BackendUrl.normalize(raw).onSuccess { url -> store.edit { it[K.backend] = url } }

    val playbackState: Flow<String?> = store.data.map { it[K.playback] }
    suspend fun savePlaybackState(json: String) = store.edit { it[K.playback] = json }
    val eqState: Flow<String?> = store.data.map { it[K.eq] }
    suspend fun saveEqState(json: String) = store.edit { it[K.eq] = json }
}
