package com.github.knightwood.downloader.updater

import com.github.knightwood.downloader.core.util.DownloadFormat

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
 * @property backgroundDownload the background download
 * @property enableNotify to enable notify
 * @property autoInstallAfterDownload the auto install after download
 */
data class ApkUpdaterParams(
    val updateId: String? = null,
    val url: String,
    val appName: String,
    val versionCode: Int,
    val versionName: String,
    val description: String,
    val md5: String? = null,
    val size: Long? = null,
    val sizeText: String = if (size != null) DownloadFormat.formatByteSize(size) else "",
    val backgroundDownload: Boolean = false,
    val enableNotify: Boolean = true,
    val autoInstallAfterDownload: Boolean = true,
) {

}
