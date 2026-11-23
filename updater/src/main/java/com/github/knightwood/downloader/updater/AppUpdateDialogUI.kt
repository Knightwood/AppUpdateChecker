package com.github.knightwood.downloader.updater

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.IDownloadedHandler
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.core.android.util.AppUtil
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreenViewModel
import com.github.knightwood.downloader.ui.screen.DownloadTaskItem
import com.github.knightwood.downloader.ui.R as UIR
import com.github.knightwood.downloader.ui.util.NotificationPermissionHelper

@Composable
fun AppUpdateDialogUI(
    params: ApkUpdaterParams,
    stateProvider: (() -> DownloadStatus?),
    onDismissRequest: () -> Unit,
    onCancelDownloadRequest: () -> Unit,
    onDownloadRequest: () -> Unit,
    onPauseRequest: () -> Unit,
    onResumeRequest: () -> Unit,
    onInstallRequest: () -> Unit,
    onRetryRequest: () -> Unit,

    ) {
    val context = LocalContext.current
    val state = stateProvider() ?: DownloadStatus.IDLE
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            //有时候会报错
//            Icon(painterResource(AppUtil.getAppIcon(context)), "app icon")
        },
        title = {
            Text("${params.appName}发现新版本!")
        },
        text = {
            Column() {
                ListItem(
                    colors = ListItemDefaults.colors().copy(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    headlineContent = {
                        Text("版本描述")
                    },
                    supportingContent = {
                        Text(params.description)
                    }
                )
                if (state is DownloadStatus.DOWNLOADING) {
                    val progress = state.progress
                    LinearProgressIndicator(
                        progress = { progress.percentage.toFloat() },
                    )
                }
                Column(
                    modifier = androidx.compose.ui.Modifier.align(Alignment.End)
                ) {
                    Text("版本号：${params.versionName}")
                    Text("大小：${params.sizeText}")
                }
            }
        },
        confirmButton = {
            TaskConfirmButton(
                state = state,
                onDownloadRequest = onDownloadRequest,
                onPauseRequest = onPauseRequest,
                onResumeRequest = onResumeRequest,
                onInstallRequest = onInstallRequest,
                onRetryRequest = onRetryRequest
            )
        },
        dismissButton = {
            TaskDismissButton(
                state = state,
                onDismissRequest = onDismissRequest,
                onCancelDownloadRequest = onCancelDownloadRequest
            )
        },
    )
}

@Composable
private fun TaskDismissButton(
    state: DownloadStatus,
    onDismissRequest: () -> Unit,
    onCancelDownloadRequest: () -> Unit,
) {
    when (state) {
        DownloadStatus.IDLE -> {
            Button(onClick = {
                onDismissRequest()
            }) {
                Text("关闭")
            }
        }

        is DownloadStatus.DOWNLOADING,
        is DownloadStatus.PAUSED,
            -> {
            Button(onClick = {
                onCancelDownloadRequest()
            }) {
                Text("取消")
            }
        }

        else -> {}
    }

}

@Composable
private fun TaskConfirmButton(
    state: DownloadStatus,
    onDownloadRequest: () -> Unit,
    onPauseRequest: () -> Unit,
    onResumeRequest: () -> Unit,
    onInstallRequest: () -> Unit,
    onRetryRequest: () -> Unit,
) {
    when (state) {
        DownloadStatus.IDLE -> {
            Button(onClick = {
                onDownloadRequest()
            }) {
                Text("下载")
            }
        }

        is DownloadStatus.DOWNLOADING -> {
            Button(onClick = {
                onPauseRequest()
            }) {
                Text("暂停")
            }
        }

        is DownloadStatus.PAUSED -> {
            Button(onClick = {
                onResumeRequest()
            }) {
                Text("继续")
            }
        }

        is DownloadStatus.FINISHED -> {
            Button(onClick = {
                onInstallRequest()
            }) {
                Text("安装")
            }
        }

        is DownloadStatus.ERROR -> {
            Button(onClick = {
                onRetryRequest()
            }) {
                Text("重试")
            }
        }

        else -> {}
    }
}
