package com.f_libs.appupdate.listener

import java.io.File

/**
 * ProjectName: AppUpdate
 * PackageName: com.azhon.appupdate.listener
 * FileName:    OnDownloadListener
 * CreateDate:  2022/4/7 on 10:27
 * Desc:
 *
 * @author azhon
 */

interface OnDownloadListener {
    /**
     * start download
     */
    fun start()

    /**
     *
     * @param max      file length
     * @param progress downloaded file size
     */
    fun downloading(max: Int, progress: Int)

    /**
     * 下载完成，还没有安装。
     * 如果自动安装，service中会自动跳转安装，这里不需要额外处理自动安装
     * @param apk
     */
    fun done(apk: File)

    /**
     * cancel download
     */
    fun cancel()

    /**
     *
     * @param e
     */
    fun error(e: Throwable)
}