package com.github.knightwood.downloader.ui.preference

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface IPreferenceProvider<T>: PreferenceConvertor<T> {
    val value: T
    val flow: Flow<T>
    suspend fun update(transform: (T) -> T)
    suspend fun query(): T
    suspend fun clear()
}

interface PreferenceConvertor<T> {
   suspend fun toPreference(entity: T): Preferences
    fun toEntity(domain: Preferences): T
}
