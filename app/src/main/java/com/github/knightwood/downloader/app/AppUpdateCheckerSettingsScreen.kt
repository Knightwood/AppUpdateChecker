@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.github.knightwood.downloader.app

import androidx.compose.runtime.Composable
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.github.knightwood.downloader.core.android.model.TaskParams
import com.github.knightwood.downloader.ui.DownloadMgrActivity
import com.github.knightwood.downloader.updater.ApkUpdaterParams
import kotlin.jvm.java


@Composable
fun AppUpdateCheckerSettingsScreen(
    stateHolder: AppUpdateCheckerSettingsScreenUIStateLogic,
    download: () -> Unit,
) {
    val uiState = stateHolder.uiState
    Column() {
        OutlinedTextField(
            value = uiState.url,
            onValueChange = { stateHolder.doAction(AppUpdateCheckerSettingsScreenIntent.ChangeUrl(it)) },
            label = { Text("URL") }
        )
        OutlinedTextField(
            value = uiState.appName,
            onValueChange = { stateHolder.doAction(AppUpdateCheckerSettingsScreenIntent.ChangeApkName(it)) },
            label = { Text("App Name") }
        )
        SwitchItem(
            title = "Enable Notify",
            checked = uiState.enableNotify,
            onCheckedChange = { stateHolder.doAction(AppUpdateCheckerSettingsScreenIntent.ChangeEnableNotify(it)) })
        SwitchItem(
            title = "Auto Install After Download",
            checked = uiState.autoInstallAfterDownload,
            onCheckedChange = {
                stateHolder.doAction(
                    AppUpdateCheckerSettingsScreenIntent.ChangeAutoInstallAfterDownload(it)
                )
            })
        SwitchItem(
            title = "Background Download",
            checked = uiState.backgroundDownload,
            onCheckedChange = {
                stateHolder.doAction(
                    AppUpdateCheckerSettingsScreenIntent.ChangeBackgroundDownload(it)
                )
            })
        Button(onClick = {
            download()
        }) {
            Text(text = "App下载&更新")
        }
    }
}

@Composable
private fun SwitchItem(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    desc: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        checked = checked, onCheckedChange = onCheckedChange,
        modifier = modifier,
        content = { Text(title) },
        supportingContent = { if (desc != null) Text(desc) else null },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        enabled = enabled
    )
}

data class AppUpdateCheckerSettingsScreenUIState(
    val url: String = "",
    val appName: String = "",
    val backgroundDownload: Boolean = false,
    val enableNotify: Boolean = true,
    val autoInstallAfterDownload: Boolean = true,
)

class AppUpdateCheckerSettingsScreenUIStateLogic(
    initializer: () -> AppUpdateCheckerSettingsScreenUIState = { AppUpdateCheckerSettingsScreenUIState() },
) {
    var uiState: AppUpdateCheckerSettingsScreenUIState by mutableStateOf(initializer())
        private set
    val paramProvider: () -> ApkUpdaterParams={
        ApkUpdaterParams(
            url = "",
            appName = "",
            backgroundDownload = uiState.backgroundDownload,
            updateId = "TEST",
            versionCode = 999,
            versionName = "2026.01.01",
            description = "测试应用更新",
            size = 1024,
            sizeText = "1K",
            enableNotify = uiState.enableNotify,
            autoInstallAfterDownload = uiState.autoInstallAfterDownload
        )

    }
    fun doAction(intent: AppUpdateCheckerSettingsScreenIntent) {
        when (intent) {
            is AppUpdateCheckerSettingsScreenIntent.ChangeUrl -> uiState = uiState.copy(url = intent.url)
            is AppUpdateCheckerSettingsScreenIntent.ChangeApkName -> uiState = uiState.copy(appName = intent.appName)
            is AppUpdateCheckerSettingsScreenIntent.ChangeEnableNotify -> uiState =
                uiState.copy(enableNotify = intent.enableNotify)

            is AppUpdateCheckerSettingsScreenIntent.ChangeAutoInstallAfterDownload -> uiState =
                uiState.copy(autoInstallAfterDownload = intent.autoInstallAfterDownload)

            is AppUpdateCheckerSettingsScreenIntent.ChangeBackgroundDownload -> uiState =
                uiState.copy(backgroundDownload = intent.backgroundDownload)
        }
    }
}

sealed class AppUpdateCheckerSettingsScreenIntent {
    data class ChangeUrl(val url: String) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeApkName(val appName: String) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeEnableNotify(val enableNotify: Boolean) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeAutoInstallAfterDownload(val autoInstallAfterDownload: Boolean) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeBackgroundDownload(val backgroundDownload: Boolean) : AppUpdateCheckerSettingsScreenIntent()

}
