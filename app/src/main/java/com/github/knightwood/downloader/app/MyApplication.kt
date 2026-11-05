package com.github.knightwood.downloader.app

import android.app.Application
import com.github.knightwood.downloader.ui.di.startDownloaderKoin

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startDownloaderKoin(this@MyApplication)
    }
}
