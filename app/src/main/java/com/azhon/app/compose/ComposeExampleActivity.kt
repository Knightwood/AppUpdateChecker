package com.azhon.app.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.azhon.app.R
import com.azhon.app.compose.ui.theme.AppUpdateTheme
import com.f_libs.appupdate.manager.DownloadManager
import com.kiylx.tools.compose_ui.AlertUpdateDialog
import com.kiylx.tools.compose_ui.ColorfulUpdateDialog
import com.kiylx.tools.compose_ui.component.ConfirmButton

class ComposeExampleActivity : ComponentActivity() {
    lateinit var manager: DownloadManager
    private val url = "http://s.duapps.com/apks/own/ESFileExplorer-cn.apk"
    private val appName = "appupdate.apk"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = DownloadManager.config(application) {
            apkUrl = url
            apkName = appName
            apkVersionCode = 2
            apkVersionName = "v4.2.1"
            apkSize = "7.7MB"
            apkDescription = getString(R.string.dialog_msg)+getString(R.string.dialog_msg)
            enableLog(true)
            jumpInstallPage = false
            showNotification = true
            showBgdToast = false
            forcedUpgrade = false
        }
        setContent {
            var openDialog by remember { mutableStateOf(false) }
            var openDialog2 by remember { mutableStateOf(false) }

            AppUpdateTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (openDialog) {
                        AlertUpdateDialog(
                            context = this,
                            downloadManager = manager
                        ) {
                            openDialog = false
                        }
                    }
                    if (openDialog2) {
                        ColorfulUpdateDialog(
                            context = this,
                            downloadManager = manager,
                            confirmButton = { modifier, text, enabled, onClick ->
                                ConfirmButton(modifier, text, enabled, onClick)
                            }
                        ) {
                            openDialog2 = false
                        }
                    }
                    Column {
                        Button(onClick = {
                            openDialog = true
                        }) {
                            Text(text = "alertDialog类型")
                        }
                        Button(onClick = {
                            openDialog2 = true
                        }) {
                            Text(text = "多彩的更新弹窗")
                        }
                    }
                }
            }
        }
    }

}
