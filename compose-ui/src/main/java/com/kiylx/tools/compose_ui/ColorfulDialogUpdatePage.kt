package com.kiylx.tools.compose_ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.kiylx.tools.compose_ui.component.ConfirmButton
import com.kiylx.tools.compose_ui.component.FreeSealDialog

@Composable
fun ColorfulUpdateDialog(
    modifier: Modifier = Modifier,
    context: Context,
    confirmButton: @Composable (modifier: Modifier, text: String, enabled: Boolean, onClick: () -> Unit) -> Unit
    = { modifier1, text, enabled, onClick ->
        ConfirmButton(modifier1, text, enabled, onClick)
    },
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
                downloadManager = downloadManager1,
                confirmButton = confirmButton,
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
    downloadManager: DownloadManager,
    confirmButton: @Composable (modifier: Modifier, text: String, enabled: Boolean, onClick: () -> Unit) -> Unit,
) {
    FreeSealDialog(
        modifier = modifier,
        containerColor = Color.Transparent,
        onDismissRequest = {
            if (!config.forcedUpgrade)
                dismiss()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //信息
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 120.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color.Black else Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "发现新版本${config.apkVersionName}!")
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
                        confirmButton(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .padding(vertical = 12.dp),
                            text = stringResource(id = buttonState.stringId),
                            enabled = buttonState.enable
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
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.app_update_dialog_default),
                    contentDescription = "img",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .requiredHeight(140.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    alignment = Alignment.TopCenter
                )
            }
            //space分隔
            Spacer(modifier = Modifier.requiredHeight(52.dp))
            //关闭按钮
            IconButton(onClick = {
                dismiss()
            }) {
                Icon(
                    modifier = Modifier
                        .background(Color.Gray, CircleShape)
                        .alpha(0.8f)
                        .size(48.dp)
                        .padding(8.dp),
                    imageVector = Icons.Default.Close,
                    contentDescription = "close downlaod",
                    tint = Color.White,
                )
            }
        }
    }
}
