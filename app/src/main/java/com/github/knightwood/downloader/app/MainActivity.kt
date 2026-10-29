package com.github.knightwood.downloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.github.knightwood.downloader.ui.DownloadMgrActivity
import kotlin.jvm.java

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
