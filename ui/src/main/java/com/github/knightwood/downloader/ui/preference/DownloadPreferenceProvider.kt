package com.github.knightwood.downloader.ui.preference

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.knightwood.downloader.core.util.HttpConstant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class DownloadPreferenceProvider(val application: Application) {
    val Application.dataStore by preferencesDataStore(name = "download_preferences")
    val flow = application.dataStore.data.map { value -> value.toDownloadPreference() }
    val value: DownloadPreference = runBlocking { flow.first() }

    suspend fun update(entity: IDownloadPreference) {
        application.dataStore.edit { settings ->
            settings[Keys.KEY_MAX_PARALLEL_DOWNLOAD_COUNT] = entity.maxParallelDownloadCount
            settings[Keys.KEY_MIN_CHUNK_SIZE] = entity.minChunkSize
            settings[Keys.KEY_MAX_CHUNK_COUNT] = entity.maxChunkCount
        }
    }

    private fun Preferences.toDownloadPreference(): DownloadPreference {
        return DownloadPreference(
            maxParallelDownloadCount = this[Keys.KEY_MAX_PARALLEL_DOWNLOAD_COUNT] ?: 5,
            minChunkSize = this[Keys.KEY_MIN_CHUNK_SIZE] ?: HttpConstant.MIN_CHUNK_SIZE,
            maxChunkCount = this[Keys.KEY_MAX_CHUNK_COUNT] ?: HttpConstant.DEFAULT_CHUNK_COUNT
        )
    }

    object Keys {
        val KEY_MAX_PARALLEL_DOWNLOAD_COUNT = intPreferencesKey("MAX_PARALLEL_DOWNLOAD_COUNT")
        val KEY_MIN_CHUNK_SIZE = longPreferencesKey("MIN_CHUNK_SIZE")
        val KEY_MAX_CHUNK_COUNT = intPreferencesKey("MAX_CHUNK_COUNT")
    }
}
