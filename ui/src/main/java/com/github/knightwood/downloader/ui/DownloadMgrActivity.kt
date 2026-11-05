package com.github.knightwood.downloader.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.coroutineScope
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.ui.di.DownloaderKoinComponent
import com.github.knightwood.downloader.ui.di.inject
import com.github.knightwood.downloader.ui.preference.ApplicationPreference
import com.github.knightwood.downloader.ui.preference.ApplicationPreferenceProvider
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreen
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreenViewModel
import com.github.knightwood.downloader.ui.util.NotificationPermissionHelper
import kotlinx.coroutines.launch

/**
 * 下载任务管理器界面
 *
 * 绑定 [DownloadService] 后把 [DownloadServiceLinker] 交给 ViewModel，
 * 由 ViewModel 负责提交任务、控制任务并把运行状态写回数据库。
 */
class DownloadMgrActivity : ComponentActivity(), DownloaderKoinComponent {
    val viewModel by viewModels<DownloadMgrScreenViewModel>()
    val appPrefs by inject<ApplicationPreferenceProvider>()

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
        checkPermission()
        linkDownloadService()
    }

    private fun checkPermission() {
        if (appPrefs.value.hadRequestNotificationPermission) return
        NotificationPermissionHelper.builder(this)
            .rationaleTitle(R.string.app_update_notify_permission_rationale_title)
            .rationaleMessage(R.string.app_update_notify_permission_rationale_message)
            .showRationaleDialog(true)
            .onResult { result ->
                when (result) {
                    NotificationPermissionHelper.Result.GRANTED -> {

                    }

                    NotificationPermissionHelper.Result.DENIED_PERMANENTLY ->
                        Toast.makeText(
                            applicationContext,
                            R.string.app_update_notify_permission_settings_title,
                            Toast.LENGTH_LONG
                        ).show()

                    else -> {}
                }
                lifecycle.coroutineScope.launch {
                    appPrefs.update {
                        (it as ApplicationPreference).copy(hadRequestNotificationPermission = true)
                    }
                }
            }
            .request()
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
