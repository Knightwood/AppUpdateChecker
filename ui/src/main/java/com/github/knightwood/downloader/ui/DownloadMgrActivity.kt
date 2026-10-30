package com.github.knightwood.downloader.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreen
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreenViewModel

/**
 * 下载任务管理器界面
 *
 * 绑定 [DownloadService] 后把 [DownloadServiceLinker] 交给 ViewModel，
 * 由 ViewModel 负责提交任务、控制任务并把运行状态写回数据库。
 */
class DownloadMgrActivity : ComponentActivity() {

    val viewModel by viewModels<DownloadMgrScreenViewModel>()

    private var isBound = false

    private val downloadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            (service as? DownloadServiceLinker)?.let { linker ->
                viewModel.onLinkerAttached(linker)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModel.onLinkerDetached()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloadMgrScreen(viewModel)
        }
        linkDownloadService()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onLinkerDetached()
        if (isBound) {
            runCatching { unbindService(downloadServiceConnection) }
            isBound = false
        }
    }

    private fun linkDownloadService() {
        DownloadService.start(this)
        isBound = bindService(
            Intent(this, DownloadService::class.java),
            downloadServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }
}
