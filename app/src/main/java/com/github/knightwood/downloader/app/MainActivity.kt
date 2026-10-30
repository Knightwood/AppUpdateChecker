package com.github.knightwood.downloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.github.knightwood.downloader.ui.DownloadMgrActivity
import kotlin.jvm.java

/**
 * https://ftp.mozilla.org/pub/firefox/releases/1.0/win32/en-US/Firefox%20Setup%201.0.exe
 * http://192.168.0.139:9167/jiliang/img/apk_files/app-master-release.apk
 */
class MainActivity : ComponentActivity() {
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
                }
            }
        }
    }
}
