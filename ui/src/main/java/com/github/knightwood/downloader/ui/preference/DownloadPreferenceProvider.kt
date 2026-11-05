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

class DownloadPreferenceProvider(val application: Application) : IPreferenceProvider<IDownloadPreference> {
    val Application.dataStore by preferencesDataStore(name = "download_preferences")
    override val flow = application.dataStore.data.map { value -> toEntity(value) }
    override val value: IDownloadPreference = runBlocking { flow.first() }

    override suspend fun update(transform: (IDownloadPreference) -> IDownloadPreference) {
        val old = flow.first()
        toPreference(transform(old))
    }

    override suspend fun query(): IDownloadPreference = flow.first()

    override suspend fun clear() {
        application.dataStore.edit { settings ->
            settings.clear()
        }
    }

    override fun toEntity(domain: Preferences): IDownloadPreference {
        return DownloadPreference(
            maxParallelDownloadCount = domain[Keys.KEY_MAX_PARALLEL_DOWNLOAD_COUNT] ?: 5,
            chunkSize = domain[Keys.KEY_MIN_CHUNK_SIZE] ?: HttpConstant.MIN_CHUNK_SIZE,
            maxChunkCount = domain[Keys.KEY_MAX_CHUNK_COUNT] ?: HttpConstant.DEFAULT_CHUNK_COUNT
        )
    }

    override suspend fun toPreference(entity: IDownloadPreference): Preferences {
        return application.dataStore.edit { settings ->
            settings[Keys.KEY_MAX_PARALLEL_DOWNLOAD_COUNT] = entity.maxParallelDownloadCount
            settings[Keys.KEY_MIN_CHUNK_SIZE] = entity.chunkSize
            settings[Keys.KEY_MAX_CHUNK_COUNT] = entity.maxChunkCount
        }
    }

    object Keys {
        val KEY_MAX_PARALLEL_DOWNLOAD_COUNT = intPreferencesKey("MAX_PARALLEL_DOWNLOAD_COUNT")
        val KEY_MIN_CHUNK_SIZE = longPreferencesKey("MIN_CHUNK_SIZE")
        val KEY_MAX_CHUNK_COUNT = intPreferencesKey("MAX_CHUNK_COUNT")
    }
}
