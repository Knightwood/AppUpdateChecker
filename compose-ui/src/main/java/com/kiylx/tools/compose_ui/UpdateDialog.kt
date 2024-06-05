package com.kiylx.tools.compose_ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.listener.OnDownloadListener
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.kiylx.tools.compose_ui.component.ConfirmButton
import com.kiylx.tools.compose_ui.component.DismissButton
import com.kiylx.tools.compose_ui.component.SealDialog
import com.kiylx.tools.compose_ui.icons.SystemUpdate
import java.io.File

@Composable
fun BasicUpdateDialog(
    modifier: Modifier = Modifier,
    context: Context,
    downloadManager: DownloadManager,
    dismiss: () -> Unit,
    content: @Composable (
        modifier: Modifier,
        config: DownloadManager.DownloadConfig,
        dismiss: () -> Unit,
        downloadState: State<DownloadStatus>,
        progressValue: Float,
        buttonState: ButtonState,
        context: Context,
        downloadManager: DownloadManager
    ) -> Unit
) {

    val config = remember(downloadManager) {
        downloadManager.config
    }
    val downloadState = downloadManager.downloadStateFlow.collectAsState()

    var progressValue by remember {
        mutableFloatStateOf(0f)
    }

    var buttonState by remember {
        mutableStateOf(ButtonState(downloadManager.canDownload(), R.string.update))
    }

    LaunchedEffect(key1 = Unit, block = {
        config.registerDownloadListener(object : OnDownloadListener {
            override fun start() {
                buttonState = ButtonState(
                    enable = false,
                    stringId = R.string.app_downloading
                )
            }

            override fun downloading(max: Int, progress: Int) {
                progressValue = progress.toFloat()
            }

            override fun done(apk: File) {
                if (config.jumpInstallPage) {
                    dismiss()
                } else {
                    buttonState = ButtonState(enable = true, stringId = R.string.install)
                }
            }

            override fun cancel() {
                dismiss()
                buttonState = ButtonState(
                    enable = false,
                    stringId = R.string.canceled
                )
            }

            override fun error(e: Throwable) {
                buttonState = ButtonState(
                    enable = false,
                    stringId = com.f_libs.appupdate.R.string.app_update_download_error
                )
                dismiss()
            }

        })
    })
    content(
        modifier,
        config,
        dismiss,
        downloadState,
        progressValue,
        buttonState,
        context,
        downloadManager
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
