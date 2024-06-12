package com.kiylx.tools.compose_ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import java.io.File

private val TAG = "Update1"

@Composable
fun BasicUpdateDialog(
    modifier: Modifier = Modifier,
    context: Context,
    downloadManager: DownloadManager,
    cacheFile: File? = ApkUtil.findBackDownloadApk(context, downloadManager.config.apkMD5),
    dismiss: () -> Unit,
    content: @Composable (
        config: DownloadManager.DownloadConfig,
        downloading: () -> Boolean,
        startDownload: () -> Unit,
        cancelDownload: () -> Unit,
        progressValue: Float,
        buttonState: ButtonState,
    ) -> Unit
) {
    var progressValue by remember {
        mutableFloatStateOf(0f)
    }

    var apk by remember {
        mutableStateOf(cacheFile)
    }

    var buttonState by remember {
        val b = downloadManager.canDownload()
        mutableStateOf(ButtonState(b, R.string.update, action = Action.idle))
    }

    LaunchedEffect(key1 = Unit, block = {
        downloadManager.downloadStateFlow.collect {
            when (it) {
                DownloadStatus.Cancel -> {
                    buttonState = ButtonState(
                        enable = false,
                        stringId = R.string.canceled,
                        action = Action.canceled,
                        file = null
                    )
                    dismiss()
                }

                is DownloadStatus.Done -> {
                    if (downloadManager.config.jumpInstallPage) {
                        dismiss()
                    } else {
                        apk =it.apk
                        buttonState = ButtonState(
                            enable = true,
                            stringId = R.string.install,
                            action = Action.readyInstall,
                            file = apk
                        )
                    }
                }

                is DownloadStatus.Downloading -> {
                    progressValue = it.progress.toFloat()
                }

                is DownloadStatus.Error -> {
                    buttonState = ButtonState(
                        enable = false,
                        stringId = com.f_libs.appupdate.R.string.app_update_download_error,
                        action = Action.error,
                        file = null
                    )
                }

                DownloadStatus.IDLE -> {
                    if (apk != null) {
                        buttonState = ButtonState(
                            enable = true, stringId = R.string.install,
                            file = apk, action = Action.readyInstall
                        )
                    } else {
                        buttonState = ButtonState(
                            enable = downloadManager.canDownload(),
                            stringId = R.string.update,
                            action = Action.idle,
                            file = null
                        )
                    }
                }

                DownloadStatus.End -> {
                    buttonState = ButtonState(
                        enable = true, stringId = R.string.install,
                        file = apk, action = Action.readyInstall
                    )

                }

                DownloadStatus.Start -> {
                    buttonState = ButtonState(
                        enable = false,
                        stringId = R.string.app_downloading,
                        action = Action.downloading,
                        file = null
                    )
                }
            }
        }
    })
    content(
        downloadManager.config,
        { downloadManager.downloading },
        downloadManager::directDownload,
        downloadManager::canDownload,
        progressValue,
        buttonState,
    )
}

/**
 * @constructor
 * @property enable Boolean
 * @property visibility Int
 * @property stringId Int
 * @author KnightWood
 */
@Immutable
data class ButtonState(
    val enable: Boolean = true,
    val stringId: Int, //string resource id
    val file: File? = null,
    val action: Int = Action.idle,
) : java.io.Serializable

class Action {
    companion object {
        //mode
        const val idle = 0x01//初始状态
        const val downloading = 0x02//下载中
        const val readyInstall = 0x04//准备安装

        const val canceled = 0x05//下载已取消
        const val error = 0x06//下载失败
        const val end = 0X07//下载流程结束
    }
}