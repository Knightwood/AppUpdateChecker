package com.github.knightwood.downloader.updater.model

import androidx.compose.runtime.Immutable
import com.github.knightwood.downloader.core.android.service.INotifier

/**
 * Apk updater params
 *
 * @constructor Creates a new ApkUpdaterParams
 * @property updateId 标识任务唯一性，此id并非下载任务id
 * @property url
 * @property appName the app name
 * @property versionCode the version code
 * @property versionName the version name
 * @property description
 * @property md5
 * @property enableNotify to enable notify
 * @property autoInstallAfterDownload the auto install after download
 */
@Immutable
data class ApkDownloadParams(
    val updateId: String? = null,
    val url: String,
    val appName: String,
    val versionName: String,
    val description: String,
    val md5: String? = null,
    val size: Long? = null,

    val enableNotify: Boolean = true,
    val autoInstallAfterDownload: Boolean = true,
    var mNotifier: INotifier? = null,
    var forceUpdate: Boolean = false,
)
