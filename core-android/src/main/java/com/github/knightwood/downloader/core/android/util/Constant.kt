package com.github.knightwood.downloader.core.android.util

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.util.Date

object Constant {

    val emptyPath: Path = "/empty".toPath()

    /**
     * Coroutine Name
     */
    const val COROUTINE_NAME = "download-service-coroutine"

    /**
     * Notification channel id
     */
    const val DEFAULT_CHANNEL_ID = "downloader"

    /**
     * Notification channel name
     */
    const val DEFAULT_CHANNEL_NAME = "下载"
    const val DEFAULT_CHANNEL_DESC = "通知下载状态和进度"

    /**
     * Notification id
     */
    const val DEFAULT_NOTIFY_ID = 1

    /**
     * 下载文件夹名
     */
    const val CACHE_DIR_NAME = "Download"

    /**
     * 默认下载文件名称前缀
     */
    const val DEFAULT_FILE_NAME_PREFIX = "DownloadFile"

    /**
     * 默认下载目录路径
     *
     * 路径:
     * 应用在外置存储中的专属缓存文件夹/Download
     */
    fun downloadCacheDirPath(context: Context): Path {
        val path = context.externalCacheDir!!.toOkioPath().resolve(CACHE_DIR_NAME)
        return path
    }

    /**
     * @param suffix 不带"."前缀的文件类型名 例如 txt、apk、zip、exe等
     */
    fun fileName(suffix: String): String {
        return "${DEFAULT_FILE_NAME_PREFIX}_${Date()}$suffix"
    }
}
