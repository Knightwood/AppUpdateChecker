package com.kiylx.tools.compose_ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.listener.OnButtonClickListener
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.kiylx.tools.compose_ui.component.ConfirmButton
import com.kiylx.tools.compose_ui.component.FreeSealDialog
import java.io.File

@Composable
fun ColorfulUpdateDialog(
    modifier: Modifier = Modifier,
    context: Context,
    confirmButton: @Composable (modifier: Modifier, text: String, enabled: Boolean, onClick: () -> Unit) -> Unit
    = { modifier1, text, enabled, onClick ->
        ConfirmButton(modifier1, text, enabled, onClick)
    },
    downloadManager: DownloadManager,
    cacheFile: File? = ApkUtil.findBackDownloadApk(context, downloadManager.config.apkMD5),
    dismiss: () -> Unit,
) {

    BasicUpdateDialog(
        modifier = modifier,
        context = context,
        downloadManager = downloadManager,
        dismiss = dismiss,
        cacheFile = cacheFile,
        content = {
                config: DownloadManager.DownloadConfig,
                downloading: () -> Boolean,
                startDownload: () -> Unit,
                cancelDownload: () -> Unit,
                progressValue: Float,
                buttonState: ButtonState,
            ->
            SampleUpdateDialog(
                modifier = modifier,
                config = config,
                dismiss = dismiss,
                downloading = downloading,
                startDownload = startDownload,
                cancelDownload = cancelDownload,
                progressValue = progressValue,
                buttonState = buttonState,
                context = context,
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
    downloading: () -> Boolean,
    startDownload: () -> Unit,
    cancelDownload: () -> Unit,
    progressValue: Float,
    buttonState: ButtonState,
    context: Context,
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
                        Text(text = "发现新版本${config.apkVersionName} !")
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = config.apkDescription.replace("\\n", "\n"),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .align(Alignment.Start)
                            )
                        }
                        if (
                            buttonState.action == Action.downloading
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
                            if (buttonState.file != null && buttonState.action == Action.readyInstall
                            ) {
                                config.onButtonClickListener?.onButtonClick(OnButtonClickListener.UPDATE)
                                //安装
                                if (!config.jumpInstallPage) {
                                    ApkUtil.installApk(
                                        context,
                                        Constant.AUTHORITIES!!,
                                        buttonState.file
                                    )
                                }
                            } else {
                                Log.d("fff", "SFG: failed")
                                //执行下载
                                config.onButtonClickListener?.onButtonClick(OnButtonClickListener.DOWNLOAD)
                                if (!downloading()) {
                                    startDownload()
                                }
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
                config.onButtonClickListener?.onButtonClick(OnButtonClickListener.CANCEL)
                cancelDownload()
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
