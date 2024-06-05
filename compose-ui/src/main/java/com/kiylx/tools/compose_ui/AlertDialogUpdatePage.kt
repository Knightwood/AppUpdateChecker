package com.kiylx.tools.compose_ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.kiylx.tools.compose_ui.component.ConfirmButton
import com.kiylx.tools.compose_ui.component.DismissButton
import com.kiylx.tools.compose_ui.component.SealDialog
import com.kiylx.tools.compose_ui.icons.SystemUpdate

@Composable
fun AlertUpdateDialog(
    modifier: Modifier = Modifier,
    context: Context,
    downloadManager: DownloadManager,
    dismiss: () -> Unit,
) {

    BasicUpdateDialog(
        modifier = modifier,
        context = context,
        downloadManager = downloadManager,
        dismiss = dismiss,
        content = { modifier1: Modifier,
                    config: DownloadManager.DownloadConfig,
                    dismiss1: () -> Unit,
                    downloadState: State<DownloadStatus>,
                    progressValue: Float,
                    buttonState: ButtonState,
                    context1: Context,
                    downloadManager1: DownloadManager ->
            SampleUpdateDialog(
                modifier = modifier1,
                config = config,
                dismiss = dismiss1,
                downloadState = downloadState,
                progressValue = progressValue,
                buttonState = buttonState,
                context = context1,
                downloadManager = downloadManager1
            )

        }
    )
}

@Composable
private fun SampleUpdateDialog(
    modifier: Modifier,
    config: DownloadManager.DownloadConfig,
    dismiss: () -> Unit,
    downloadState: State<DownloadStatus>,
    progressValue: Float,
    buttonState: ButtonState,
    context: Context,
    downloadManager: DownloadManager
) {
    SealDialog(
        modifier = modifier,
        icon = {
            if (config.smallIcon != -1) {
                Icon(painter = painterResource(config.smallIcon), contentDescription = "icon")
            } else {
                Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = "icon")
            }
        },
        onDismissRequest = {
            if (!config.forcedUpgrade)
                dismiss()
        },
        title = { Text(text = "发现新版本${config.apkVersionName}!") },
        content = {
            config.apkSize.takeIf {
                it.isNotEmpty() && it.isNotBlank()
            }?.let {
                Text(
                    text = "更新大小:${it}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 12.dp, top = 4.dp)
                        .align(Alignment.Start)
                )
            }
            //描述
            Text(
                text = "更新描述:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.Start)
            )
            Text(
                text = config.apkDescription.replace("\\n", "\n"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.Start)
            )
            if (
                downloadState.value is DownloadStatus.Downloading
                || downloadState.value is DownloadStatus.Start
            ) {
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }

        },
        dismissButton = {
            DismissButton {
                dismiss()
            }
        },
        confirmButton = {
            ConfirmButton(
                text = stringResource(id = buttonState.stringId), enabled = buttonState.enable
            ) {
                if (downloadState.value is DownloadStatus.Done) {
                    //安装
                    if (!config.jumpInstallPage) {
                        ApkUtil.installApk(
                            context,
                            Constant.AUTHORITIES!!,
                            (downloadState.value as DownloadStatus.Done).apk
                        )
                    }
                } else {
                    //执行下载
                    if (!downloadManager.downloadState)
                        downloadManager.directDownload()
                }
            }
        })
}