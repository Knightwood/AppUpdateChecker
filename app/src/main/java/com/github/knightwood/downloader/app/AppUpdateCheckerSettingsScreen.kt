@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.github.knightwood.downloader.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.knightwood.downloader.updater.ApkDownloadParams


@Composable
fun AppUpdateCheckerSettingsScreen(
    stateHolder: AppUpdateCheckerSettingsScreenUIStateLogic,
    download: () -> Unit,
    secretDownload: () -> Unit,
) {
    val uiState = stateHolder.uiState
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.url,
            onValueChange = { stateHolder.doAction(AppUpdateCheckerSettingsScreenIntent.ChangeUrl(it)) },
            label = { Text("URL") }
        )
        OutlinedTextField(
            value = uiState.appName,
            onValueChange = { stateHolder.doAction(AppUpdateCheckerSettingsScreenIntent.ChangeApkName(it)) },
            label = { Text("App Name") },
            suffix = { Text(".apk") }
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
            title = "Force Update",
            checked = uiState.forceUpdate,
            onCheckedChange = { stateHolder.doAction(AppUpdateCheckerSettingsScreenIntent.ChangeForceUpdate(it)) })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                download()
            }) {
                Text(text = "App下载&更新")
            }
            OutlinedButton(onClick = {
                secretDownload()
            }) {
                Text(text = "静默更新")
            }
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
    val enableNotify: Boolean = true,
    val autoInstallAfterDownload: Boolean = true,
    val forceUpdate: Boolean = false,
)

class AppUpdateCheckerSettingsScreenUIStateLogic(
    initializer: () -> AppUpdateCheckerSettingsScreenUIState = { AppUpdateCheckerSettingsScreenUIState() },
) {
    var uiState: AppUpdateCheckerSettingsScreenUIState by mutableStateOf(initializer())
        private set
    val paramProvider: () -> ApkDownloadParams = {
        ApkDownloadParams(
            url = uiState.url,
            appName = if (uiState.appName.endsWith(".apk")) uiState.appName else "${uiState.appName}.apk",
            updateId = "TEST",
            versionName = "2026.01.01",
            description = "1.测试应用更新\n2.修复已知问题\n3.优化性能\n4.其他问题\n",
            size = 13387 * 1024,
            enableNotify = uiState.enableNotify,
            autoInstallAfterDownload = uiState.autoInstallAfterDownload,
            forceUpdate = uiState.forceUpdate,
        )
    }

    fun doAction(intent: AppUpdateCheckerSettingsScreenIntent) {
        when (intent) {
            is AppUpdateCheckerSettingsScreenIntent.ChangeUrl -> uiState = uiState.copy(url = intent.url)
            is AppUpdateCheckerSettingsScreenIntent.ChangeApkName -> uiState = uiState.copy(appName = intent.appName)
            is AppUpdateCheckerSettingsScreenIntent.ChangeEnableNotify -> uiState =
                uiState.copy(enableNotify = intent.enableNotify)
            is AppUpdateCheckerSettingsScreenIntent.ChangeForceUpdate -> uiState = uiState.copy(forceUpdate = intent.forceUpdate)

            is AppUpdateCheckerSettingsScreenIntent.ChangeAutoInstallAfterDownload -> uiState =
                uiState.copy(autoInstallAfterDownload = intent.autoInstallAfterDownload)

        }
    }
}

sealed class AppUpdateCheckerSettingsScreenIntent {
    data class ChangeUrl(val url: String) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeApkName(val appName: String) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeEnableNotify(val enableNotify: Boolean) : AppUpdateCheckerSettingsScreenIntent()
    data class ChangeAutoInstallAfterDownload(val autoInstallAfterDownload: Boolean) :
        AppUpdateCheckerSettingsScreenIntent()
    data class ChangeForceUpdate(val forceUpdate: Boolean) : AppUpdateCheckerSettingsScreenIntent()
}
