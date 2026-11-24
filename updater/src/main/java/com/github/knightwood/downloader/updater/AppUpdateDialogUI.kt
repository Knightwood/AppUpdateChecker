package com.github.knightwood.downloader.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.knightwood.downloader.core.model.DownloadProgressRecord
import com.github.knightwood.downloader.core.util.DownloadFormat
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType

@Composable
fun AppUpdateDialogUI(
    params: ApkDownloadParams,
    stateProvider: (() -> DownloadStatusType?),
    progressProvider: (() -> DownloadProgressRecord?),
    onDismissRequest: () -> Unit,
    onCancelDownloadRequest: () -> Unit,
    onDownloadRequest: () -> Unit,
    onPauseRequest: () -> Unit,
    onResumeRequest: () -> Unit,
    onInstallRequest: () -> Unit,
    onRetryRequest: () -> Unit,

    ) {
    val context = LocalContext.current
    val state = stateProvider() ?: DownloadStatusType.IDLE

    AlertDialog(
        properties = DialogProperties(dismissOnBackPress = !params.forceUpdate, dismissOnClickOutside = !params.forceUpdate),
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        icon = {
            //有时候会报错
//            Icon(painterResource(AppUtil.getAppIcon(context)), "app icon")
        },
        title = {
            Text("发现新版本!")
        },
        text = {
            Column() {
                CompositionLocalProvider(LocalTextStyle.provides(MaterialTheme.typography.labelMedium)) {
                    Text("版本号：${params.versionName}")
                    if (params.size != null) {
                        Text("更新大小：${DownloadFormat.formatByteSize(params.size)}")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("版本描述", style = MaterialTheme.typography.titleMedium)
                Text(
                    params.description, modifier = Modifier
                        .padding(vertical = 8.dp)
                        .padding(start = 8.dp)
                )
                if (state != DownloadStatusType.IDLE) {
                    val data = progressProvider()
                    val progress = data?.percentage ?: 0.0
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                    )
                    data?.let {
                        Text(
                            text = it.progressText(),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
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
            if (!params.forceUpdate) {
                TaskDismissButton(
                    state = state,
                    onDismissRequest = onDismissRequest,
                    onCancelDownloadRequest = onCancelDownloadRequest
                )
            }
        },
    )
}

/**
 * 详情弹窗中的进度文本：已下载 / 总大小 (百分比)
 */
private fun DownloadProgressRecord.progressText(): String = when {
    length <= 0L -> "大小未知"
    downloaded <= 0L -> DownloadFormat.formatByteSize(length)
    else -> buildString {
        append(DownloadFormat.formatByteSize(downloaded))
        append(" / ")
        append(DownloadFormat.formatByteSize(this@progressText.length))
        append(" (")
        append((percentage * 100).toInt())
        append("%)")
    }
}

@Composable
private fun TaskDismissButton(
    state: DownloadStatusType,
    onDismissRequest: () -> Unit,
    onCancelDownloadRequest: () -> Unit,
) {
    when (state) {
        DownloadStatusType.ERROR, DownloadStatusType.IDLE, DownloadStatusType.STARTING, DownloadStatusType.CANCELED, DownloadStatusType.FINISHED -> {
            TextButton(onClick = {
                onDismissRequest()
            }) {
                Text("关闭")
            }
        }

        DownloadStatusType.DOWNLOADING, DownloadStatusType.PAUSED -> {
            TextButton(onClick = {
                onCancelDownloadRequest()
            }) {
                Text("取消")
            }
        }

    }

}

@Composable
private fun TaskConfirmButton(
    state: DownloadStatusType,
    onDownloadRequest: () -> Unit,
    onPauseRequest: () -> Unit,
    onResumeRequest: () -> Unit,
    onInstallRequest: () -> Unit,
    onRetryRequest: () -> Unit,
) {
    when (state) {
        DownloadStatusType.IDLE -> {
            Button(onClick = {
                onDownloadRequest()
            }) {
                Text("下载")
            }
        }

        DownloadStatusType.DOWNLOADING -> {
            Button(onClick = {
                onPauseRequest()
            }) {
                Text("暂停")
            }
        }

        DownloadStatusType.PAUSED -> {
            Button(onClick = {
                onResumeRequest()
            }) {
                Text("继续")
            }
        }

        DownloadStatusType.FINISHED -> {
            Button(onClick = {
                onInstallRequest()
            }) {
                Text("安装")
            }
        }

        DownloadStatusType.ERROR, DownloadStatusType.CANCELED -> {
            Button(onClick = {
                onRetryRequest()
            }) {
                Text("重试")
            }
        }

        DownloadStatusType.STARTING -> {
            Button(onClick = {}, enabled = false) {
                Text("开始下载")
            }
        }
    }
}
