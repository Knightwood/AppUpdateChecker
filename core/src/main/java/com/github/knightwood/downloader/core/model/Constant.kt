package com.github.knightwood.downloader.core.model


object Constant {

    /**
     * Http timeout(ms)
     */
    const val HTTP_TIME_OUT = 30_000

    const val DEFAULT_TAG_PREFIX = "Downloader#"

    /**
     * Apk file extension
     */
    const val APK_SUFFIX = ".apk"

    /**
     * Coroutine Name
     */
    const val COROUTINE_NAME = "app-update-coroutine"

    /**
     * Notification id
     */
    const val DEFAULT_NOTIFY_ID = 114514

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
     * 下载文件夹名
     */
    var cacheDirName ="AppUpdateCache"

}
