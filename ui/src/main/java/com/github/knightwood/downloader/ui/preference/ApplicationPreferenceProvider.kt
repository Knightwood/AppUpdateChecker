package com.github.knightwood.downloader.ui.preference

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class ApplicationPreferenceProvider(val application: Application) : IPreferenceProvider<IApplicationPreference> {
    val Application.dataStore by preferencesDataStore(name = "app_preferences")
    override val flow = application.dataStore.data.map { value -> toEntity(value) }
    override val value: IApplicationPreference = runBlocking { flow.first() }

    override suspend fun update(transform: (IApplicationPreference) -> IApplicationPreference) {
        val current = flow.first()
        toPreference(transform(current))
    }

    override suspend fun query(): IApplicationPreference = flow.first()

    override suspend fun clear() {
        application.dataStore.edit { settings ->
            settings.clear()
        }
    }

    override fun toEntity(domain: Preferences): IApplicationPreference {
        return ApplicationPreference(
            hadRequestNotificationPermission = domain[Keys.KEY_HAD_REQUEST_NOTIFICATION_PERMISSION] ?: false
        )
    }

    override suspend fun toPreference(entity: IApplicationPreference): Preferences {
        return application.dataStore.edit { settings ->
            settings[Keys.KEY_HAD_REQUEST_NOTIFICATION_PERMISSION] = entity.hadRequestNotificationPermission
        }
    }

    object Keys {
        val KEY_HAD_REQUEST_NOTIFICATION_PERMISSION = booleanPreferencesKey("HAD_REQUEST_NOTIFICATION_PERMISSION")
    }

}
