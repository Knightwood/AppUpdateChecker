package com.kiylx.tools.compose_ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.manager.DownloadManager

@Composable
fun BasicUpdateDialog(
    modifier: Modifier = Modifier,
    context: Context,
    downloadManager: DownloadManager,
    dismiss: () -> Unit,
    content: @Composable (
        config: DownloadManager.DownloadConfig,
        downloadState: State<DownloadStatus>,
        downloading: () -> Boolean,
        startDownload: () -> Unit,
        cancelDownload: () -> Unit,
        progressValue: Float,
        buttonState: ButtonState,
    ) -> Unit
) {


    val downloadState = downloadManager.downloadStateFlow.collectAsState()

    var progressValue by remember {
        mutableFloatStateOf(0f)
    }

    var buttonState by remember {
        mutableStateOf(ButtonState(downloadManager.canDownload(), R.string.update))
    }

    LaunchedEffect(key1 = Unit, block = {
        downloadManager.downloadStateFlow.collect {
            when (it) {
                DownloadStatus.Cancel -> {
                    buttonState = ButtonState(
                        enable = false,
                        stringId = R.string.canceled
                    )
                    dismiss()
                }

                is DownloadStatus.Done -> {
                    if (downloadManager.config.jumpInstallPage) {
                        dismiss()
                    } else {
                        buttonState = ButtonState(enable = true, stringId = R.string.install)
                    }
                }

                is DownloadStatus.Downloading -> {
                    progressValue = it.progress.toFloat()
                }

                is DownloadStatus.Error -> {
                    buttonState = ButtonState(
                        enable = false,
                        stringId = com.f_libs.appupdate.R.string.app_update_download_error
                    )
                    dismiss()
                }

                DownloadStatus.IDLE -> {
                    buttonState = ButtonState(
                        enable = downloadManager.canDownload(),
                        stringId = R.string.update
                    )
                }

                DownloadStatus.Start -> {
                    buttonState = ButtonState(
                        enable = false,
                        stringId = R.string.app_downloading
                    )
                }
            }
        }
    })
    content(
        downloadManager.config,
        downloadState,
        { downloadManager.downloading },
        downloadManager::directDownload,
        downloadManager::cancelDirectly,
        progressValue,
        buttonState,
    )
}

/**
 * @author KnightWood
 * @property enable Boolean
 * @property visibility Int
 * @property stringId Int
 * @constructor
 */
data class ButtonState(
    val enable: Boolean = true,
    val stringId: Int
) : java.io.Serializable
