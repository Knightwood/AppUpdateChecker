package com.github.knightwood.downloader.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreen
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreenViewModel

class DownloadMgrActivity : FragmentActivity() {
    val viewModel by viewModels<DownloadMgrScreenViewModel>()
    var downloadServiceLinker: DownloadServiceLinker? = null
    val downloadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            downloadServiceLinker = service as DownloadServiceLinker
        }

        override fun onServiceDisconnected(name: ComponentName) {
            downloadServiceLinker = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        linkDownloadService()
        setContent {
            DownloadMgrScreen(viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(downloadServiceConnection)
    }

    private fun linkDownloadService() {
        startService(
            Intent(this, DownloadService::class.java)
        )
        bindService(
            Intent(this, DownloadService::class.java),
            downloadServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
}
