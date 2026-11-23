package com.github.knightwood.downloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.ui.DownloadMgrActivity
import com.github.knightwood.downloader.ui.R
import com.github.knightwood.downloader.ui.preference.ApplicationPreference
import com.github.knightwood.downloader.ui.util.NotificationPermissionHelper
import com.github.knightwood.downloader.updater.ApkUpdaterParams
import com.github.knightwood.downloader.updater.AppUpdater
import kotlinx.coroutines.launch
import kotlin.jvm.java


class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold() {
                Column(modifier = Modifier.padding(it)) {
                    Button(onClick = {
                        startActivity(
                            Intent(this@MainActivity, DownloadMgrActivity::class.java)
                        )
                    }) {
                        Text(text = "Open Download Manager")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = "App更新")
                    val stateHolder = remember() { AppUpdateCheckerSettingsScreenUIStateLogic() }
                    AppUpdateCheckerSettingsScreen(
                        stateHolder = stateHolder,
                        download = {
                            if (stateHolder.uiState.backgroundDownload) {
                                lifecycle.coroutineScope.launch {
                                    AppUpdater.secretDownload(this@MainActivity, stateHolder.paramProvider)
                                }
                            } else {
                                AppUpdater.display(supportFragmentManager, stateHolder.paramProvider)
                            }
                        }
                    )
                }
            }
        }
    }
}
