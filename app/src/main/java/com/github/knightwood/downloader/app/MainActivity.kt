package com.github.knightwood.downloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import com.github.knightwood.downloader.ui.DownloadMgrActivity
import com.github.knightwood.downloader.updater.AppUpdater
import com.github.knightwood.downloader.updater.example.ColorfulUpdateDialog
import com.github.knightwood.downloader.updater.example.FullScreenUpdateDialog
import kotlinx.coroutines.launch
import kotlin.jvm.java


class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold() {
                Column(modifier = Modifier
                    .padding(it)
                    .padding(horizontal = 12.dp)) {
                    Button(onClick = {
                        startActivity(
                            Intent(this@MainActivity, DownloadMgrActivity::class.java)
                        )
                    }) {
                        Text(text = "Open Download Manager")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ListItem(
                        headlineContent = {
                            Text(text = "App更新")
                        }
                    )
                    val stateHolder = remember() { AppUpdateCheckerSettingsScreenUIStateLogic() }
                    AppUpdateCheckerSettingsScreen(
                        stateHolder = stateHolder,
                        download = {
                            AppUpdater.display(supportFragmentManager, stateHolder.paramProvider)
                        },
                        secretDownload = {
                            lifecycle.coroutineScope.launch {
                                AppUpdater.secretDownload(this@MainActivity, stateHolder.paramProvider)
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            ColorfulUpdateDialog(
                                stateHolder.paramProvider(),
                            ).show(supportFragmentManager, "ColorfulUpdateDialog")
                        }) {
                            Text("自定义弹窗1")
                        }
                        Button(onClick = {
                            FullScreenUpdateDialog(
                                stateHolder.paramProvider(),
                            ).show(supportFragmentManager, "ColorfulUpdateDialog")
                        }) {
                            Text("自定义弹窗2")
                        }
                    }
                }
            }
        }
    }
}
