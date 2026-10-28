package com.github.knightwood.downloader.ui.di

import com.github.knightwood.downloader.ui.preference.DownloadPreferenceProvider
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

object InstanceModule {
    val module = module {
        single<DownloadPreferenceProvider> {
            DownloadPreferenceProvider(androidApplication())
        }
    }
}
